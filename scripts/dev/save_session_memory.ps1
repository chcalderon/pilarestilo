param(
  [string]$Note = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location -Path $repoRoot

$memoryFile = Join-Path $repoRoot "docs/session-memory.md"
$timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$commit = (git rev-parse --short HEAD).Trim()
$statusLines = git status --short
$bt = [char]96

if (-not $statusLines) {
  $statusLines = @("clean")
}

$entry = @()
$entry += "## $timestamp"
$entry += ""
$entry += "- Branch: $bt$branch$bt"
$entry += "- Commit: $bt$commit$bt"
if (-not [string]::IsNullOrWhiteSpace($Note)) {
  $entry += "- Note: $Note"
}
$entry += "- Working tree:"
$entry += '```text'
$entry += ($statusLines -join "`n")
$entry += '```'
$entry += ""

if (-not (Test-Path -Path $memoryFile)) {
  @(
    "# Session Memory"
    ""
    "Bitacora de contexto tecnico para retomar trabajo sin perder continuidad."
    ""
  ) | Set-Content -Path $memoryFile
}

Add-Content -Path $memoryFile -Value ($entry -join "`n")
Write-Host "[session-memory] actualizado: $memoryFile"
