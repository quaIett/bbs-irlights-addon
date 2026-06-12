param([string]$repoRoot = ".")
$packs = @{
    'bsl'                     = 'BSL'
    'bliss'                   = 'Bliss'
    'complementaryreimagined' = 'ComplementaryReimagined'
    'iterationrp'             = 'IterationRP'
    'photon'                  = 'Photon'
    'solas'                   = 'Solas'
}
$allOk = $true
foreach ($patchKey in ($packs.Keys | Sort-Object)) {
    $file = "$repoRoot\patches\$patchKey.irlights"
    $own  = $packs[$patchKey]
    $others = ($packs.Values | Where-Object { $_ -ne $own }) -join '|'
    $lineage = 'ported from|IRLEngine|kept across ports|Phase [0-9]|A/B strip|take-[23]|original addon|rebased onto|old irlite_'
    $pattern = "($lineage|$others)"
    $hits = Select-String -Path $file -Pattern $pattern
    if ($hits) {
        $allOk = $false
        Write-Host "=== $patchKey.irlights ==="
        $hits | ForEach-Object { Write-Host "  L$($_.LineNumber): $($_.Line.Trim())" }
    } else {
        Write-Host "OK  $patchKey.irlights"
    }
}
if ($allOk) { Write-Host "`nAll 6 patches: no lineage words found." }
