param(
    [Parameter(Mandatory)][string]$Capture,
    [ValidateRange(0, 1000000)][int]$WarmupFrames = 240,
    [ValidateRange(1, 1000000)][int]$MeasureFrames = 600,
    [string]$OutputJson,
    [string[]]$RequireGpuMetric = @(),
    [switch]$RequireCompleteWindow
)
$ErrorActionPreference = 'Stop'
$rows = @(Import-Csv -LiteralPath $Capture)
if ($rows.Count -eq 0) { throw 'Empty capture' }
$first = ($rows | ForEach-Object { [long]$_.frame } | Measure-Object -Minimum).Minimum
$start = [long]$first + $WarmupFrames
$end = $start + $MeasureFrames
$selected = @($rows | Where-Object { [long]$_.frame -ge $start -and [long]$_.frame -lt $end })
$complete = [System.Collections.Generic.HashSet[long]]::new()
foreach ($row in $selected) {
    if ($row.kind -eq 'frame' -and $row.name -eq 'gpu-complete') { [void]$complete.Add([long]$row.frame) }
}
if ($complete.Count -eq 0) { throw 'No complete GPU frames after warmup; capture longer or reduce WarmupFrames' }
if ($RequireCompleteWindow -and $complete.Count -ne $MeasureFrames) {
    throw "Incomplete benchmark window: expected $MeasureFrames complete GPU frames, found $($complete.Count)"
}
$metrics = @{}
foreach ($row in $selected) {
    $frame = [long]$row.frame
    if ($row.kind -eq 'frame' -or -not $complete.Contains($frame)) { continue }
    if ($row.kind -notin @('cpu', 'gpu', 'work')) { throw "Unknown capture kind: $($row.kind)" }
    $key = "$($row.kind)/$($row.name)"
    if (-not $metrics.ContainsKey($key)) { $metrics[$key] = @{} }
    $metrics[$key][$frame] = [long]$metrics[$key][$frame] + [long]$row.value
}
# A missing optional pass means no work; a missing benchmark target means no measurement.
foreach ($name in $RequireGpuMetric) {
    $key = "gpu/$name"
    $present = if ($metrics.ContainsKey($key)) { $metrics[$key].Count } else { 0 }
    if ($present -ne $complete.Count) {
        throw "Required GPU metric '$name' is missing on $($complete.Count - $present) complete frames"
    }
}
$summary = foreach ($key in ($metrics.Keys | Sort-Object)) {
    $isTime = -not $key.StartsWith('work/')
    $isCpu = $key.StartsWith('cpu/')
    # Missing GPU/work on a complete frame means no work; missing CPU means unavailable.
    $values = @(foreach ($frame in $complete) {
        if ($isCpu -and -not $metrics[$key].ContainsKey($frame)) { continue }
        [double]$metrics[$key][$frame]
    }) | Sort-Object
    $n = $values.Count
    if ($n -eq 0) { continue }
    $scale = if ($isTime) { 1000000.0 } else { 1.0 }
    $mid = [int][Math]::Floor($n / 2)
    $median = if ($n % 2 -eq 0) { ($values[$mid - 1] + $values[$mid]) / 2 } else { $values[$mid] }
    [pscustomobject]@{
        metric = $key; unit = $(if ($isTime) { 'ms' } else { 'count/frame' }); samples = $n
        avg = ($values | Measure-Object -Average).Average / $scale
        p50 = $median / $scale
        p95 = $values[[int][Math]::Ceiling($n * 0.95) - 1] / $scale
        max = $values[-1] / $scale
    }
}
$last = ($rows | ForEach-Object { [long]$_.frame } | Measure-Object -Maximum).Maximum
$report = [pscustomobject]@{
    capture = (Resolve-Path -LiteralPath $Capture).Path
    startFrame = $start; endFrameExclusive = $end
    requestedFrames = $MeasureFrames; completeGpuFrames = $complete.Count
    missingGpuFrames = $MeasureFrames - $complete.Count
    captureReachesEnd = ([long]$last -ge $end - 1)
    requiredGpuMetrics = @($RequireGpuMetric)
    requireCompleteWindow = [bool]$RequireCompleteWindow
    metrics = @($summary)
}
if ($OutputJson) { $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputJson -Encoding utf8 }
$report | Select-Object startFrame, endFrameExclusive, completeGpuFrames, missingGpuFrames, captureReachesEnd | Format-List
$summary | Format-Table -AutoSize
if ($complete.Count -lt $MeasureFrames) { Write-Warning 'Incomplete window: missing GPU frames are excluded. Do not treat this as a complete benchmark.' }
