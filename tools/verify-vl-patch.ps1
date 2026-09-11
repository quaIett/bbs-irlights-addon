param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$code = Split-Path -Parent $repo
$testRoot = Join-Path $repo 'build/vl'
$classes = Join-Path $testRoot 'patch-harness'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$patcher = Join-Path $code 'irl-core/src/main/java/org/qualet/irl/patcher'
$sources = @('IrlPatch.java','IrlPatchParser.java','IrlPatchApplier.java','PatchResult.java','PatchEngine.java') |
    ForEach-Object { Join-Path $patcher $_ }
& $javac --release 17 -encoding UTF-8 -d $classes @sources (Join-Path $PSScriptRoot 'PatchHarness.java')
if ($LASTEXITCODE -ne 0) { throw 'Patch harness compilation failed' }

# A fresh child avoids replacing any pre-existing pack through the patcher API.
$output = Join-Path $testRoot ('patch-check-' + [Guid]::NewGuid().ToString('N'))
$original = Join-Path $repo 'Shadres/Original/ComplementaryReimagined'
$modified = Join-Path $repo 'Shadres/Modification/ComplementaryReimagined/shaders'
$mainPatch = Join-Path $repo 'patches/complementaryreimagined.irlights'
$comboPatch = Join-Path $code 'bbs-dof-addon/patches/complementaryreimagined-irl-dof.irlights'
$editorPatch = Join-Path $code 'irlights/src/client/resources/assets/irl-redactor/patches/complementaryreimagined.irlights'
New-Item -ItemType Directory -Path $output | Out-Null
foreach ($entry in @(@($mainPatch, 'main'), @($comboPatch, 'combo'))) {
    $log = & $java -cp $classes PatchHarness $entry[0] $original (Join-Path $output $entry[1])
    $exit = $LASTEXITCODE
    $log | Set-Content -LiteralPath (Join-Path $output ($entry[1] + '.log')) -Encoding utf8
    if ($exit -ne 0) { throw ('Patch failed: ' + $entry[0] + ' — ' + ($log -join "`n")) }
}
function ContentHash([string]$file) {
    if ([IO.Path]::GetExtension($file) -in @('.glsl','.fsh','.vsh','.gsh','.csh','.properties','.lang','.txt')) {
        $bytes = [Text.Encoding]::UTF8.GetBytes([IO.File]::ReadAllText($file).Replace("`r`n", "`n"))
        return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    }
    return (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
}
$mainShaders = Join-Path $output 'main/shaders'
$expected = @(Get-ChildItem -LiteralPath $modified -Recurse -File)
$actual = @(Get-ChildItem -LiteralPath $mainShaders -Recurse -File)
if ($actual.Count -ne $expected.Count) { throw 'Generated and modified shader trees have different file counts' }
foreach ($file in $expected) {
    $relative = [IO.Path]::GetRelativePath($modified, $file.FullName)
    $generated = Join-Path $mainShaders $relative
    if (!(Test-Path -LiteralPath $generated) -or (ContentHash $generated) -ne (ContentHash $file.FullName)) {
        throw ('Generated shader differs: ' + $relative)
    }
}
foreach ($relative in @('lib/irlite/irlite_lights.glsl', 'program/deferred2.glsl')) {
    if ((ContentHash (Join-Path $mainShaders $relative)) -ne (ContentHash (Join-Path $output ('combo/shaders/' + $relative)))) {
        throw ('Combo VL differs: ' + $relative)
    }
}
if ((Get-FileHash -LiteralPath $mainPatch).Hash -ne (Get-FileHash -LiteralPath $editorPatch).Hash) {
    throw 'Bundled editor CR patch differs from addon CR patch'
}
$result = [pscustomobject]@{
    shadersCompared = $expected.Count
    mainPatchSha256 = (Get-FileHash -LiteralPath $mainPatch).Hash
    comboPatchSha256 = (Get-FileHash -LiteralPath $comboPatch).Hash
    editorMatches = $true
    comboVlMatches = $true
    output = $output
}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRoot 'patch-verification.json') -Encoding utf8
Write-Output "VL patch verification passed: $($expected.Count) shader files, main/combo apply, editor and combo VL match."
