param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$diag = Join-Path $repo 'src/client/java/qualet/irlite/client/diag'
$classes = Join-Path $repo 'build/profiler-test'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
& $javac --release 17 -encoding UTF-8 -d $classes (Join-Path $diag 'FrameGpuTimings.java') (Join-Path $diag 'TimingStats.java') (Join-Path $PSScriptRoot 'profiler/ProfilerTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Profiler test compilation failed' }
& $java -cp $classes qualet.irlite.client.diag.ProfilerTest
if ($LASTEXITCODE -ne 0) { throw 'Profiler tests failed' }

$fixture = Join-Path $classes 'capture-test.csv'
$report = Join-Path $classes 'capture-test.json'
$rows = [Collections.Generic.List[string]]::new()
$rows.Add('kind,frame,name,value')
foreach ($frame in 1..5) {
    $rows.Add("gpu,$frame,bake,$($frame * 10000000)")
    $rows.Add("cpu,$frame,frame,$($frame * 12000000)")
    if ($frame -ne 3) { $rows.Add("frame,$frame,gpu-complete,1") }
}
$rows.Add('gpu,2,bake-spot-copy,6000000')
$rows.Add('work,2,sp.copy,2')
$rows.Add('work,2,sp.copy,3')
$rows | Set-Content -LiteralPath $fixture -Encoding utf8
& (Join-Path $PSScriptRoot 'analyze-profile.ps1') -Capture $fixture -WarmupFrames 0 -MeasureFrames 5 -OutputJson $report -WarningAction SilentlyContinue | Out-Null
$result = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json
$bake = $result.metrics | Where-Object metric -eq 'gpu/bake'
$copy = $result.metrics | Where-Object metric -eq 'gpu/bake-spot-copy'
$work = $result.metrics | Where-Object metric -eq 'work/sp.copy'
if ($result.completeGpuFrames -ne 4 -or $result.missingGpuFrames -ne 1 -or $bake.avg -ne 30 -or $bake.p50 -ne 30 -or $bake.p95 -ne 50 -or $copy.avg -ne 1.5 -or $work.avg -ne 1.25) {
    throw 'CSV regression: incomplete frames, zero-work frames, percentiles or repeated counters were aggregated incorrectly'
}
Write-Output 'CSV analysis checks passed'

function Expect-Rejected([string]$expected, [scriptblock]$action) {
    $message = $null
    try { & $action | Out-Null } catch { $message = $_.Exception.Message }
    if (!$message -or !$message.Contains($expected)) { throw "Expected rejection '$expected', got '$message'" }
}
$analyzer = Join-Path $PSScriptRoot 'analyze-profile.ps1'
Expect-Rejected 'Incomplete benchmark window' {
    & $analyzer -Capture $fixture -WarmupFrames 0 -MeasureFrames 5 -RequireCompleteWindow
}
Expect-Rejected "Required GPU metric 'deferred2'" {
    & $analyzer -Capture $fixture -WarmupFrames 0 -MeasureFrames 2 -RequireGpuMetric deferred2
}
Expect-Rejected "Required GPU metric 'bake-spot-copy'" {
    & $analyzer -Capture $fixture -WarmupFrames 0 -MeasureFrames 2 -RequireGpuMetric bake-spot-copy
}
$vlFixture = Join-Path $classes 'capture-vl-test.csv'
@('kind,frame,name,value', 'frame,1,gpu-complete,1', 'gpu,1,deferred2,0',
  'frame,2,gpu-complete,1', 'gpu,2,deferred2,1000000') | Set-Content -LiteralPath $vlFixture -Encoding utf8
& $analyzer -Capture $vlFixture -WarmupFrames 0 -MeasureFrames 2 -RequireGpuMetric deferred2 -RequireCompleteWindow -OutputJson $report | Out-Null
$strict = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json
if ($strict.metrics[0].avg -ne 0.5 -or !$strict.requireCompleteWindow -or $strict.requiredGpuMetrics[0] -ne 'deferred2') {
    throw 'Strict VL analysis must accept explicitly recorded zero and retain benchmark requirements'
}
Write-Output 'Strict VL capture checks passed: 4'
