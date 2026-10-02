param([string]$Tasks = "build", [int]$TimeoutSeconds = 900)
# Serialized Gradle build helper: a named mutex prevents concurrent agents
# from clobbering the shared build directory.
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools/dsh-build.ps1 -Tasks "build"
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$mutex = New-Object System.Threading.Mutex($false, "Global\idtw_dsh_gradle_build")
$acquired = $false
try {
    $acquired = $mutex.WaitOne($TimeoutSeconds * 1000)
    if (-not $acquired) { Write-Output "[dsh-build] timeout waiting for build mutex"; exit 124 }
    Push-Location $root
    $taskList = $Tasks -split '\s+'
    $out = & .\gradlew.bat $taskList --console=plain 2>&1
    $code = $LASTEXITCODE
    $out | ForEach-Object { $_ }
    Write-Output ("[dsh-build] exit=" + $code)
    exit $code
} finally {
    if ($acquired) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
    Pop-Location -ErrorAction SilentlyContinue
}
