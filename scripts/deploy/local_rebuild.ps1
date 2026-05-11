param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$ExtraArgs
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$localDeploy = Join-Path $scriptDir "local_deploy.ps1"

if (-not (Test-Path -Path $localDeploy)) {
  throw "[local-rebuild] ERROR: local_deploy.ps1 no encontrado en $scriptDir"
}

Write-Host "[local-rebuild] Bajando stack local..."
& $localDeploy "down" "--remove-orphans"

Write-Host "[local-rebuild] Subiendo stack local con build..."
if ($ExtraArgs -and $ExtraArgs.Length -gt 0) {
  & $localDeploy "up" @ExtraArgs
} else {
  & $localDeploy "up"
}
