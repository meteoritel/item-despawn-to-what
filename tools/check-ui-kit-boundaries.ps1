<#
.SYNOPSIS
    转发 UI kit 边界校验，保留 ProjectRoot / KitPath 调用参数。
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot,
    [string]$KitPath = 'common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit'
)
$ErrorActionPreference = 'Stop'
$pythonArgs = @()
if (-not [string]::IsNullOrEmpty($ProjectRoot)) {
    $pythonArgs += @('--root', $ProjectRoot)
}
$pythonArgs += @('--kit-path', $KitPath)
& python (Join-Path $PSScriptRoot 'check-ui-kit-boundaries.py') @pythonArgs
exit $LASTEXITCODE
