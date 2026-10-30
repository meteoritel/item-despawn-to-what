# GUI 探针编排：准备 dev 运行目录、拉起真实客户端、驱动用例、收证据、还原现场。
#
# 只在本机跑：需要真实 GL 桌面会话，跑完还原 options.txt 并停掉自己拉起的客户端。
# -Reuse 复用已运行的客户端：不拉新进程、不改写/还原 options.txt，等价于 -NoLaunch 且不杀进程。
param(
    [string]$RunDir = "",
    [string]$Cases = "",
    [string[]]$Only = @(),
    [string]$World = "",
    [int]$WaitClient = 240,
    [switch]$NoLaunch,
    [switch]$KeepClient,
    [switch]$Reuse
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runRoot = Join-Path $repoRoot "neoforge/build/gui-runs"
if ([string]::IsNullOrWhiteSpace($RunDir)) {
    $RunDir = Join-Path $runRoot (Get-Date -Format "yyyyMMdd-HHmmss")
} elseif (-not [System.IO.Path]::IsPathRooted($RunDir)) {
    # 相对名字一律落在 gui-runs 下：证据目录集中，不随调用者的当前目录漂移。
    $RunDir = Join-Path $runRoot $RunDir
}
$RunDir = [System.IO.Path]::GetFullPath($RunDir)
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
if ([string]::IsNullOrWhiteSpace($Cases)) { $Cases = Join-Path $repoRoot "tools/gui-probes/cases" }

$validationArgs = @((Join-Path $repoRoot "tools/gui-probes/lib/run.py"), "--run-dir", $RunDir, "--cases", $Cases, "--validate")
foreach ($name in $Only) { $validationArgs += @("--only", $name) }
& python @validationArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$modsDir = Join-Path $repoRoot "neoforge/run/mods"
$requiredMods = @("AutoJS-neoforge-1.21.1-1.0.0.jar", "rhino-2101.2.7-build.81.jar")
foreach ($mod in $requiredMods) {
    if (-not (Test-Path (Join-Path $modsDir $mod))) {
        Write-Host "[gui-probes] 缺少 dev 依赖 $mod，先跑 tools/gui-probes/setup.ps1" -ForegroundColor Red
        exit 2
    }
}

$optionsPath = Join-Path $repoRoot "neoforge/run/options.txt"
$optionsBackup = Join-Path $repoRoot "neoforge/run/options.txt.gui-probes-backup"
$settings = @{
    "guiScale" = "2"
    "lang" = "en_us"
    "forceUnicodeFont" = "false"
    "fullscreen" = "false"
    "onboardAccessibility" = "false"
}

function Set-Options {
    # 探针固定 GUI 缩放与语言：缩放决定逻辑坐标，语言决定断言里的文案真值。
    Copy-Item $optionsPath $optionsBackup -Force
    $lines = Get-Content $optionsPath
    $seen = @{}
    $out = foreach ($line in $lines) {
        $key = ($line -split ":", 2)[0]
        if ($settings.ContainsKey($key)) { $seen[$key] = $true; "$key`:$($settings[$key])" }
        elseif ($key -like "quickPlay*") { }
        else { $line }
    }
    foreach ($key in $settings.Keys) {
        if (-not $seen.ContainsKey($key)) { $out += "$key`:$($settings[$key])" }
    }
    # 必须无 BOM：带 BOM 时游戏解析不出第一项，整套选项退回默认（含首次启动的无障碍引导页）。
    [System.IO.File]::WriteAllLines($optionsPath, $out, (New-Object System.Text.UTF8Encoding($false)))
}

function Restore-Options {
    if (Test-Path $optionsBackup) {
        Move-Item $optionsBackup $optionsPath -Force
    }
}

$client = $null
$exitCode = 0
$launch = -not ($NoLaunch -or $Reuse)
# 只有真正拉起客户端才碰 options.txt：复用会话时改它会把现场搞脏。
if ($launch) { Set-Options }
try {
    if ($launch) {
        $clientLog = Join-Path $RunDir "client.log"
        $clientErr = Join-Path $RunDir "client.err.log"
        Write-Host "[gui-probes] 启动客户端，日志 $clientLog"
        # 不用 Start-Process：Windows PowerShell 5.1 在环境里有大小写重名变量（如 NO_PROXY/no_proxy）时会直接抛错。
        $launcher = New-Object System.Diagnostics.ProcessStartInfo
        $launcher.FileName = "cmd.exe"
        $launcher.Arguments = '/c powershell -ExecutionPolicy Bypass -NoProfile -File "' +
            (Join-Path $repoRoot "tools/dsh-build.ps1") + '" -Tasks :neoforge:runClient -TimeoutSeconds 2400 > "' +
            $clientLog + '" 2> "' + $clientErr + '"'
        $launcher.UseShellExecute = $false
        $client = [System.Diagnostics.Process]::Start($launcher)
    }
    $pythonArgs = @(
        (Join-Path $repoRoot "tools/gui-probes/lib/run.py"),
        "--run-dir", $RunDir,
        "--cases", $Cases,
        "--wait-client", "$WaitClient"
    )
    if ($World) { $pythonArgs += @("--world", $World) }
    foreach ($name in $Only) { $pythonArgs += @("--only", $name) }
    & python @pythonArgs
    $exitCode = $LASTEXITCODE
} finally {
    if ($client -ne $null -and -not $KeepClient) {
        Write-Host "[gui-probes] 停止客户端进程树 pid=$($client.Id)"
        & taskkill /PID $client.Id /T /F 2>$null | Out-Null
    }
    if ($launch) { Restore-Options }
}

Write-Host "[gui-probes] 证据目录 $RunDir"
$summaryPath = Join-Path $RunDir "gui-summary.json"
if (Test-Path $summaryPath) {
    $guiSummary = Get-Content $summaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($guiSummary.shots) {
        Write-Host "[gui-probes] 截图 $($guiSummary.shots.count) 张"
        if ($guiSummary.shots.contact_sheet) {
            Write-Host "[gui-probes] contact sheet $($guiSummary.shots.contact_sheet)"
        }
        if ($guiSummary.shots.list) {
            Write-Host "[gui-probes] 截图清单 $($guiSummary.shots.list)"
        }
    }
}
exit $exitCode
