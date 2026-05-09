param(
  [ValidateSet("up", "down", "ps", "logs", "restart")]
  [string]$Action = "up",
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$ExtraArgs
)

$ErrorActionPreference = "Stop"

function Is-True([string]$Raw) {
  if ([string]::IsNullOrWhiteSpace($Raw)) { return $false }
  $normalized = $Raw.Trim().ToLowerInvariant()
  return $normalized -in @("true", "1", "yes", "on")
}

function Read-EnvValue([string]$File, [string]$Key) {
  $escaped = [Regex]::Escape($Key)
  $line = Get-Content -Path $File | Where-Object { $_ -match "^${escaped}=" } | Select-Object -First 1
  if (-not $line) { return "" }
  return ($line.Substring($Key.Length + 1)).Trim()
}

function Is-LocalDomain([string]$Domain) {
  if ([string]::IsNullOrWhiteSpace($Domain)) { return $true }
  $value = $Domain.Trim().ToLowerInvariant()
  return $value -eq "localhost" -or $value -eq "127.0.0.1" -or $value -eq "::1" -or $value.EndsWith(".localhost")
}

$appDir = if ($env:APP_DIR) { $env:APP_DIR } else { (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path }
Set-Location -Path $appDir

$composeFile = "infra/docker-compose.yml"
$envFile = "infra/.env"

if (-not (Test-Path -Path $composeFile)) {
  throw "[local-deploy] ERROR: $composeFile not found in $appDir"
}

if (-not (Test-Path -Path $envFile)) {
  throw "[local-deploy] ERROR: $envFile not found. Create it from infra/.env.example first."
}

$allowNonLocal = Is-True($env:LOCAL_DEPLOY_ALLOW_NONLOCAL)
if (-not $allowNonLocal) {
  $domainRaw = Read-EnvValue -File $envFile -Key "DOMAIN"
  if (-not [string]::IsNullOrWhiteSpace($domainRaw)) {
    $domains = $domainRaw -split "\s+" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    foreach ($domain in $domains) {
      if (-not (Is-LocalDomain $domain)) {
        throw "[local-deploy] ERROR: DOMAIN='$domainRaw' parece entorno no local. Usa scripts/deploy/vps_deploy.sh para VPS/produccion. Para forzar: LOCAL_DEPLOY_ALLOW_NONLOCAL=true."
      }
    }
  }
}

$deployProfiles = $env:DEPLOY_PROFILES
if ([string]::IsNullOrWhiteSpace($deployProfiles)) {
  $deployProfiles = Read-EnvValue -File $envFile -Key "DEPLOY_PROFILES"
}

if (-not [string]::IsNullOrWhiteSpace($deployProfiles)) {
  $items = $deployProfiles.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne "ai" }
  $deployProfiles = ($items -join ",")
}

if (-not [string]::IsNullOrWhiteSpace($deployProfiles)) {
  $env:COMPOSE_PROFILES = $deployProfiles
}

$skipBuild = Is-True($env:SKIP_BUILD)
$forceRecreate = Is-True($env:FORCE_RECREATE)
$removeOrphans = if ($env:REMOVE_ORPHANS) { Is-True($env:REMOVE_ORPHANS) } else { $true }

Write-Host "[local-deploy] App dir: $appDir"
Write-Host "[local-deploy] Action: $Action"
$profilesLabel = if ([string]::IsNullOrWhiteSpace($deployProfiles)) { "<none>" } else { $deployProfiles }
Write-Host "[local-deploy] Profiles: $profilesLabel"

$composeBase = @("compose", "-f", $composeFile, "--env-file", $envFile)

switch ($Action) {
  "up" {
    $args = @("up", "-d")
    if (-not $skipBuild) { $args += "--build" }
    if ($forceRecreate) { $args += "--force-recreate" }
    if ($removeOrphans) { $args += "--remove-orphans" }
    if ($ExtraArgs) { $args += $ExtraArgs }
    & docker @composeBase @args
    & docker @composeBase "ps"
  }
  "down" {
    $args = @("down")
    if ($ExtraArgs) { $args += $ExtraArgs }
    & docker @composeBase @args
  }
  "ps" {
    $args = @("ps")
    if ($ExtraArgs) { $args += $ExtraArgs }
    & docker @composeBase @args
  }
  "logs" {
    $args = @("logs", "-f")
    if ($ExtraArgs) { $args += $ExtraArgs }
    & docker @composeBase @args
  }
  "restart" {
    $args = @("restart")
    if ($ExtraArgs) { $args += $ExtraArgs }
    & docker @composeBase @args
    & docker @composeBase "ps"
  }
}
