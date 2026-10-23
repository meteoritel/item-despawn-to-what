param(
    [ValidateSet('core', 'natural', 'settlement', 'damage', 'recovery', 'loading', 'budget', 'state', 'save')]
    [string]$Chain = 'core',
    [switch]$Negative,
    [int]$TimeoutSeconds = 900
)
# 单入口：隔离存档、串行 Gradle、结构化报告。每次保留现场，失败退出非零。
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runName = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + $Chain
$relativeRun = 'build/probe-runs/' + $runName
$runDirectory = Join-Path $projectRoot ('neoforge/' + $relativeRun)
New-Item -ItemType Directory -Path $runDirectory | Out-Null
[IO.File]::WriteAllText((Join-Path $runDirectory 'eula.txt'), "eula=true`n")
[IO.File]::WriteAllText((Join-Path $runDirectory 'server.properties'), @"
level-name=world
level-type=minecraft:flat
generate-structures=false
online-mode=false
server-ip=127.0.0.1
server-port=0
view-distance=2
simulation-distance=2
spawn-protection=0
max-tick-time=120000
pause-when-empty-seconds=-1
"@)
Write-Output "[probe] artifacts=$runDirectory"
$negativeProperty = $Negative.IsPresent.ToString().ToLowerInvariant()
$exitCode = 1
try {
    foreach ($phase in @('prepare', 'resume')) {
        $tasks = ":neoforge:runProbeServer -PprobeRunDir=$relativeRun -PprobeSuite=$Chain -PprobeNegative=$negativeProperty -PprobePhase=$phase"
        $log = Join-Path $runDirectory ($phase + '-gradle.log')
        $errors = Join-Path $runDirectory ($phase + '-stderr.log')
        $buildScript = Join-Path $PSScriptRoot 'dsh-build.ps1'
        $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $buildScript + '"'), '-Tasks', ('"' + $tasks + '"'))
        $process = Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errors
        $processHandle = $process.Handle
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            & taskkill.exe /PID $process.Id /T /F | Out-Null
            throw "phase=$phase timeout after ${TimeoutSeconds}s; logs=$runDirectory"
        }
        $process.Refresh()
        $reportFile = Join-Path $runDirectory 'probe-report.json'
        if ($process.ExitCode -ne 0) { throw "phase=$phase Gradle exit=$($process.ExitCode); log=$log" }
        if (-not (Test-Path -LiteralPath $reportFile)) { throw "phase=$phase missing report; log=$log" }
        $report = Get-Content -LiteralPath $reportFile -Raw -Encoding UTF8 | ConvertFrom-Json
        Copy-Item -LiteralPath $reportFile -Destination (Join-Path $runDirectory ($phase + '-report.json'))
        $expectedPhase = $phase
        if ($report.format -ne 'idtw-backend-probe-v1' -or $report.phase -ne $expectedPhase -or $report.suite -ne $Chain) {
            throw "unexpected report identity: $reportFile"
        }
        foreach ($result in $report.results) {
            Write-Output ("[probe] {0}: {1}" -f $result.step, $result.verdict)
            foreach ($check in $result.checks) {
                if (-not $check.pass) { Write-Output ("  FAIL {0} expected={1} actual={2}" -f $check.name, $check.expected, $check.actual) }
            }
        }
        if ($report.verdict -eq 'RESTART_REQUIRED' -and $phase -eq 'prepare' -and @($report.remaining).Count -eq 0) { continue }
        if ($report.verdict -ne 'PASS' -or @($report.results).Count -eq 0 -or @($report.remaining).Count -ne 0) {
            throw "verdict=$($report.verdict) reason=$($report.reason); report=$reportFile"
        }
        foreach ($result in $report.results) {
            if ($result.verdict -ne 'PASS' -or @($result.checks).Count -eq 0 -or @($result.checks | Where-Object { -not $_.pass }).Count -gt 0) {
                throw "invalid PASS: $($result.step); report=$reportFile"
            }
        }
        $exitCode = 0
        break
    }
} catch {
    Write-Output "[probe] FAIL $_"
}
Write-Output "[probe] exit=$exitCode artifacts=$runDirectory"
exit $exitCode
