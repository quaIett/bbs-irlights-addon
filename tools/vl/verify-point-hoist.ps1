param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$baselinePath = Join-Path $PSScriptRoot 'reference/irlite_lights.glsl'
$candidatePath = Join-Path $repo 'Shadres/Modification/ComplementaryReimagined/shaders/lib/irlite/irlite_lights.glsl'
$baseline = [IO.File]::ReadAllText($baselinePath)
$candidate = [IO.File]::ReadAllText($candidatePath)

function Normalize-Shader([string]$value) {
    $value = [regex]::Replace($value, '(?s)/\*.*?\*/|//[^\r\n]*', '')
    return [regex]::Replace($value, '\s+', '')
}
function Read-Function([string]$source, [string]$name) {
    $match = [regex]::Match($source, '(?ms)^(?:float|vec2|void|int) ' + $name + '\(.*?^\}')
    if (!$match.Success) { throw "Missing function: $name" }
    return Normalize-Shader $match.Value
}
function Read-Body([string]$function) {
    return $function.Substring($function.IndexOf('{') + 1, $function.LastIndexOf('}') - $function.IndexOf('{') - 1)
}
function Assert-Same([string]$expected, [string]$actual, [string]$label) {
    if ($expected -cne $actual) { throw "Point hoist source contract changed: $label" }
}

# Freeze the untouched shared surface helpers and the explicit A/B baseline.
foreach ($entry in @{IRL_PT_END0 = 2; IRL_PT_END1 = 14; IRL_PT_CELL1 = 2; IRL_PT_CELL2 = 5}.GetEnumerator()) {
    $declaration = 'const\s+int\s+' + $entry.Key + '\s*=\s*' + $entry.Value + '\s*;'
    if ($baseline -notmatch $declaration -or $candidate -notmatch $declaration) {
        throw "CPU reference block table no longer matches shader: $($entry.Key)"
    }
}
foreach ($name in @('irlite_pointAtlasUV', 'irlite_cubeFaceUV', 'irlite_depthBias', 'irlite_vlPointStep')) {
    Assert-Same (Read-Function $baseline $name) (Read-Function $candidate $name) $name
}
$oldAtlas = Read-Body (Read-Function $baseline 'irlite_pointAtlasUV')
$setup = Read-Body (Read-Function $candidate 'irlite_vlPointSetup')
$oldStep = Read-Body (Read-Function $baseline 'irlite_vlPointStep')
$newStep = Read-Body (Read-Function $candidate 'irlite_vlPointStepPrepared')

# Moving declarations into out-parameters must be the only arithmetic edit.
$expectedSetup = $oldAtlas.Substring(0, $oldAtlas.IndexOf('vec2uv;'))
$expectedSetup = $expectedSetup.Replace('floatfaceUv=', 'faceUv=').Replace('vec2blockMin=', 'blockMin=')
$expectedSetup += 'vec2atlasSize=vec2(textureSize(irl_pointShadowAtlas,0));halfTexel=0.5/atlasSize;'
Assert-Same $expectedSetup $setup 'block geometry / atlas half texel operation order'

# Nudge, both guards, perspective depth and bias are byte-equivalent tokens.
$oldPrefix = $oldStep.Substring(0, $oldStep.IndexOf('vec2atlasSize='))
$newPrefix = $newStep.Substring(0, $newStep.IndexOf('vec2faceLocalUV;'))
Assert-Same $oldPrefix $newPrefix 'receiver nudge, guards, depth and bias'

# Inline the unchanged per-tap face suffix, renaming only local variables and
# moving the original caller's clamp to the new helper's UV expression.
$expectedSuffix = $oldAtlas.Substring($oldAtlas.IndexOf('vec2uv;'))
$expectedSuffix = $expectedSuffix.Replace('vec2uv;face=', 'vec2faceLocalUV;intface=')
$expectedSuffix = $expectedSuffix.Replace('(tapDir,uv)', '(dir,faceLocalUV)')
$expectedSuffix = $expectedSuffix.Replace('vec2halfTexel=0.5/atlasSize;', '')
$expectedSuffix = $expectedSuffix.Replace('tileMin=', 'vec2tMin=').Replace('tileMax=', 'vec2tMax=')
$expectedSuffix = $expectedSuffix.Replace('returnfaceOrigin+uv*faceUv;', 'vec2uv=clamp(faceOrigin+faceLocalUV*faceUv,tMin,tMax);')
$expectedSuffix += $oldStep.Substring($oldStep.IndexOf('floatstored='))
Assert-Same $expectedSuffix $newStep.Substring($newStep.IndexOf('vec2faceLocalUV;')) 'face selection, half-texel clamp and texture comparison'

$flat = Normalize-Shader $candidate
if (!$flat.Contains('#ifndefIRLITE_VL_POINT_HOIST#defineIRLITE_VL_POINT_HOIST1#endif#if!IRLITE_VL_POINT_HOIST')) {
    throw 'Missing internal baseline/candidate switch'
}
if (!$flat.Contains('if(shHas&&!isSpot){irlite_vlPointSetup(shTile,shPointBlockMin,shPointFaceUv,shPointHalfTexel);}')) {
    throw 'Prepared point setup must be guarded by shadow map availability and point type'
}
if (!$flat.Contains(':irlite_vlPointStepPrepared(toLight,dist,range,shPointBlockMin,shPointFaceUv,shPointHalfTexel);#else:irlite_vlPointStep(toLight,dist,range,shTile);#endif')) {
    throw 'Missing baseline/candidate point call switch'
}
Write-Output 'Point hoist source contract passed: shared helpers, baseline branch, float operation order and guarded setup.'

$classes = Join-Path $repo 'build/vl/point-test'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
& $javac --release 17 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'PointShadowHoistTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Point shadow hoist test compilation failed' }
& $java -cp $classes PointShadowHoistTest
if ($LASTEXITCODE -ne 0) { throw 'Point shadow hoist CPU parity failed' }
