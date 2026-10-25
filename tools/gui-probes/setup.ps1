<#
.SYNOPSIS
    下载并校验 GUI 探针依赖的两个 mod jar（AutoJS + Rhino）。

.DESCRIPTION
    目标目录默认是仓库根下的 neoforge/run/mods。已存在且大小、sha256 都匹配则跳过；
    否则下载到临时文件，校验通过后原子替换，任一步失败都以非零码退出并打印原因。
    两个 jar 都是 dev-only 依赖，neoforge/run 已被 .gitignore 忽略，不入库。

.PARAMETER DryRun
    只打印计划，不访问网络、不写文件。

.PARAMETER ModsDir
    覆盖 jar 目标目录。

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools/gui-probes/setup.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [switch]$DryRun,
    [string]$ModsDir
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$dependencies = @(
    @{
        Name   = 'AutoJS-neoforge-1.21.1-1.0.0.jar'
        Url    = 'https://maven.sighs.cc/repository/maven-releases/cc/sighs/autojs/AutoJS-neoforge-1.21.1/1.0.0/AutoJS-neoforge-1.21.1-1.0.0.jar'
        Sha256 = 'bd46b38445754fd9d6b8f78c5f66e5b87d6707f7ab26fb78edb148bafc6858ae'
        Size   = 163497
    },
    @{
        Name   = 'rhino-2101.2.7-build.81.jar'
        Url    = 'https://maven.latvian.dev/releases/dev/latvian/mods/rhino/2101.2.7-build.81/rhino-2101.2.7-build.81.jar'
        Sha256 = 'a02abde402b5cea16f31dea4026d630f95a3369134d891a196c014d1daf492c3'
        Size   = 882032
    }
)

if (-not $ModsDir) {
    $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $ModsDir = Join-Path $repoRoot 'neoforge/run/mods'
}

function Get-Sha256 {
    param([Parameter(Mandatory)][string]$Path)
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-Jar {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Sha256,
        [Parameter(Mandatory)][long]$Size
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    if ((Get-Item -LiteralPath $Path).Length -ne $Size) { return $false }
    return (Get-Sha256 -Path $Path) -eq $Sha256
}

Write-Host ('GUI 探针依赖目录: {0}' -f $ModsDir)
$missing = @()
foreach ($dependency in $dependencies) {
    $target = Join-Path $ModsDir $dependency.Name
    if (Test-Jar -Path $target -Sha256 $dependency.Sha256 -Size $dependency.Size) {
        Write-Host ('[跳过] {0} 已存在且 sha256 校验通过' -f $dependency.Name)
        continue
    }
    if ($DryRun) {
        Write-Host ('[计划] {0}' -f $target)
        Write-Host ('       来源 {0}' -f $dependency.Url)
        continue
    }
    $temporary = $target + '.part'
    try {
        if (-not (Test-Path -LiteralPath $ModsDir -PathType Container)) {
            New-Item -ItemType Directory -Path $ModsDir -Force | Out-Null
        }
        Write-Host ('[下载] {0}' -f $dependency.Url)
        Invoke-WebRequest -Uri $dependency.Url -OutFile $temporary -UseBasicParsing -TimeoutSec 180
        if (-not (Test-Jar -Path $temporary -Sha256 $dependency.Sha256 -Size $dependency.Size)) {
            $actual = if (Test-Path -LiteralPath $temporary) { Get-Sha256 -Path $temporary } else { '文件缺失' }
            throw ('sha256 或大小不符：期望 {0} / {1} B，实际 {2}' -f $dependency.Sha256, $dependency.Size, $actual)
        }
        Move-Item -LiteralPath $temporary -Destination $target -Force
        Write-Host ('[完成] {0} 校验通过' -f $dependency.Name)
    }
    catch {
        $missing += $dependency.Name
        Write-Host ('[失败] {0}: {1}' -f $dependency.Name, $_.Exception.Message) -ForegroundColor Red
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
        }
    }
}

if ($missing.Count -gt 0) {
    Write-Host ('依赖未就绪，缺少: {0}' -f ($missing -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host ('依赖就绪: {0}' -f $ModsDir)
exit 0
