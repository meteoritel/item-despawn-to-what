<#
.SYNOPSIS
    检查客户端 UI kit（client/ui/kit）的依赖边界。
.DESCRIPTION
    扫描 kit 目录下所有 .java 的 import 与全限定引用，规则：

      * 允许 kit 自身包内的引用：com.meteorite.itemdespawntowhat.client.ui.kit.*；
      * import 白名单：java.* / javax.* / net.minecraft.* / org.jetbrains.* / org.joml.* / org.lwjgl.*；
      * 禁用：本模组其它包（core、client.edit、client.net、client.ui.theme、client.ui.screen、
        client.ui.widget 等）、net.fabricmc.*、net.neoforged.*、com.google.gson.*。

    命中任意一条即列出违规清单并以退出码 1 结束；全部通过则打印通过信息并以退出码 0 结束。
    目录不存在或没有 .java 文件时以退出码 2 结束（按配置错误处理）。
.PARAMETER ProjectRoot
    项目根目录；默认取脚本所在目录的上一级。
.PARAMETER KitPath
    kit 源码目录，相对于项目根或绝对路径；默认
    common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit。
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/check-ui-kit-boundaries.ps1
#>
[CmdletBinding()]
param(
    [string]$ProjectRoot,
    [string]$KitPath = 'common/src/main/java/com/meteorite/itemdespawntowhat/client/ui/kit'
)

$ErrorActionPreference = 'Stop'

# 参数默认值不在 param 块里求值：Windows PowerShell 5.1 在参数绑定阶段读不到 $PSScriptRoot。
# 脚本默认位于 <项目根>/tools，因此用脚本自身路径的上一级作为项目根。
if ([string]::IsNullOrEmpty($ProjectRoot)) {
    $scriptPath = $MyInvocation.MyCommand.Path
    $scriptDir = (Get-Location).Path
    if (-not [string]::IsNullOrEmpty($scriptPath)) {
        $scriptDir = Split-Path -Parent $scriptPath
    }
    $ProjectRoot = (Resolve-Path -LiteralPath (Join-Path -Path $scriptDir -ChildPath '..')).Path
}

# kit 自身包：同包内互相引用是允许的
$kitPackage = 'com.meteorite.itemdespawntowhat.client.ui.kit'
# import 白名单前缀
$allowedPrefixes = @('java.', 'javax.', 'net.minecraft.', 'org.jetbrains.', 'org.joml.', 'org.lwjgl.')
$allowedClasses = @('com.mojang.blaze3d.platform.Lighting', 'com.mojang.blaze3d.vertex.PoseStack', 'com.mojang.blaze3d.vertex.VertexConsumer')
# 明确禁用前缀（即使落在白名单里也禁用）
$forbiddenPrefixes = @('net.fabricmc.', 'net.neoforged.', 'com.google.gson.')

if (-not [System.IO.Path]::IsPathRooted($KitPath)) {
    $KitPath = Join-Path -Path $ProjectRoot -ChildPath $KitPath
}
if (-not (Test-Path -LiteralPath $KitPath -PathType Container)) {
    Write-Host ('[配置错误] kit 目录不存在：' + $KitPath)
    exit 2
}

$rootFull = (Resolve-Path -LiteralPath $ProjectRoot).Path
$kitFull = (Resolve-Path -LiteralPath $KitPath).Path
$files = @(Get-ChildItem -LiteralPath $kitFull -Filter '*.java' -File | Sort-Object Name)
if ($files.Count -eq 0) {
    Write-Host ('[配置错误] kit 目录下没有 .java 文件：' + $kitFull)
    exit 2
}

$importPattern = '^\s*import\s+(?:static\s+)?([A-Za-z_][A-Za-z0-9_\.]*)\s*;'
$fqPattern = '\b(?:com\.meteorite|com\.mojang\.blaze3d|net\.fabricmc|net\.neoforged|com\.google\.gson)\.[A-Za-z_][A-Za-z0-9_\.]*'
$violations = New-Object System.Collections.Generic.List[string]
$scannedFiles = 0
$scannedImports = 0

foreach ($file in $files) {
    $scannedFiles++
    $relative = $file.FullName.Substring($rootFull.Length).TrimStart('\', '/')
    $lines = @(Get-Content -LiteralPath $file.FullName -Encoding UTF8)
    for ($index = 0; $index -lt $lines.Count; $index++) {
        $raw = $lines[$index]
        $lineNo = $index + 1

        # 一、import 行：按白名单 / 禁用集合逐条判定
        if ($raw -match $importPattern) {
            $scannedImports++
            $target = $Matches[1]
            if ($target -eq $kitPackage -or $target.StartsWith($kitPackage + '.')) {
                continue
            }
            $reason = $null
            foreach ($prefix in $forbiddenPrefixes) {
                if ($target.StartsWith($prefix)) { $reason = '禁用依赖 ' + $prefix; break }
            }
            if ($null -eq $reason -and $target.StartsWith('com.meteorite.')) {
                $reason = '本模组非 kit 包'
            }
            if ($null -eq $reason) {
                $allowed = $allowedClasses -contains $target
                foreach ($prefix in $allowedPrefixes) {
                    if ($target.StartsWith($prefix)) { $allowed = $true; break }
                }
                if (-not $allowed) { $reason = '白名单外 import' }
            }
            if ($null -ne $reason) {
                $violations.Add(('{0}:{1} [{2}] {3}' -f $relative, $lineNo, $reason, $raw.Trim()))
            }
            continue
        }

        # 二、非 import 行：检查全限定引用（含 javadoc 里的 FQ 名）
        foreach ($match in [regex]::Matches($raw, $fqPattern)) {
            $fq = $match.Value
            if ($fq -eq $kitPackage -or $fq.StartsWith($kitPackage + '.')) {
                continue
            }
            if ($allowedClasses -contains $fq) { continue }
            if ($fq.StartsWith('com.meteorite.')) {
                $violations.Add(('{0}:{1} [本模组非 kit 包的全限定引用] {2}' -f $relative, $lineNo, $fq))
            } else {
                $violations.Add(('{0}:{1} [禁用依赖的全限定引用] {2}' -f $relative, $lineNo, $fq))
            }
        }
    }
}

Write-Host ('[信息] 扫描目录：' + $kitFull)
Write-Host ('[信息] 扫描文件：' + $scannedFiles + ' 个，import：' + $scannedImports + ' 条')

if ($violations.Count -gt 0) {
    Write-Host ''
    Write-Host ('[违规] 共 ' + $violations.Count + ' 处：')
    foreach ($violation in $violations) {
        Write-Host ('  ' + $violation)
    }
    Write-Host ''
    Write-Host '[失败] kit 依赖边界检查未通过。'
    exit 1
}

Write-Host ''
Write-Host ('[通过] kit 依赖边界检查通过：' + $scannedFiles + ' 个文件，0 处违规。')
exit 0
