# Generates patches/iterationrp.irlights from Shadres/Modification/IterationRP. WITH OUTLINE
# (array-free LocalLightOutline rim, accumulated PRE-albedo into diffuse — IterationRP AE-safe).
# Validate after generating (must be EMPTY):
#   java -cp tools\build PatchHarness patches\iterationrp.irlights Shadres\Original\IterationRP $env:TEMP\IterationRP_ptest
#   git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol $env:TEMP\IterationRP_ptest Shadres\Modification\IterationRP

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-irlights-addon"
$mod  = "$repo\Shadres\Modification\IterationRP\shaders"
$org  = "$repo\Shadres\Original\IterationRP\shaders"
$out  = "$repo\patches\iterationrp.irlights"
$T = "`t"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function IndexOfLine($lines, $text) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found: $text"
}
function IndexOfLineFrom($lines, $text, $from) {
    for ($i = $from; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found after ${from}: $text"
}

# ---- extract bodies from Modification ----
$libText = FileText "$mod\Lib\irlite_lights.glsl"
if (-not $libText.Contains("irlite_outlineFactor")) { throw "outline missing from the lib" }
if (-not $libText.EndsWith("`n")) { throw "lib must end with a newline" }

# Soild_FS: include pair + diffuse block (before-op) + specular block (after-op)
$sf = Lines "$mod\Lib\Programs\Composite\Soild_FS.glsl"
$sfInc = IndexOfLine $sf '#include "/Lib/BasicFunctions/Blocklight.glsl"'
if ($sf[$sfInc + 1] -cne '#define IRLITE_SURFACE_PASS') { throw "Soild_FS include op line 1 unexpected" }
if ($sf[$sfInc + 2] -cne '#include "/Lib/irlite_lights.glsl"') { throw "Soild_FS include op line 2 unexpected" }
$sfPre = IndexOfLine $sf ($T + $T + $T + 'color = color * (1.0 - metalnessMask * METALMASK_STRENGTH) + textureLighting;')
$sfAnc = IndexOfLine $sf ($T + $T + $T + 'color = color * gbuffer.albedo + sunlightSpecular;')
$sfDiffuse = $sf[($sfPre + 1)..($sfAnc - 1)]
if (-not $sfDiffuse[0].StartsWith($T + $T + $T + 'bool irlite_nonTerrain')) { throw "Soild_FS diffuse head unexpected" }
if ($sfDiffuse[-1] -cne ($T + $T + $T + '#endif')) { throw "Soild_FS diffuse tail unexpected" }
$sfSpec = $sf[($sfAnc + 1)..($sfAnc + 3)]
if ($sfSpec[0] -cne ($T + $T + $T + '#ifdef IRLITE_SPECULAR')) { throw "Soild_FS spec head unexpected" }
if ($sfSpec[-1] -cne ($T + $T + $T + '#endif')) { throw "Soild_FS spec tail unexpected" }

# Volumetric_FS: include pair + VL call block (after-op)
$vf = Lines "$mod\Lib\Programs\Composite\Volumetric_FS.glsl"
$vfInc = IndexOfLine $vf '#include "/Lib/BasicFunctions/Blocklight.glsl"'
if ($vf[$vfInc + 1] -cne '#define IRLITE_VL_PASS') { throw "Volumetric_FS include op line 1 unexpected" }
if ($vf[$vfInc + 2] -cne '#include "/Lib/irlite_lights.glsl"') { throw "Volumetric_FS include op line 2 unexpected" }
$vfAncText = $T + $T + $T + 'if (fogTimeFactor > 0.01 && isEyeInWater == 0) VolumetricFog(color, vec3(0.0), rayWorldPos, worldDir, globalCloudShadow, fogTimeFactor, fogTransmittance);'
$vfAnc = IndexOfLine $vf $vfAncText
$vlBlock = $vf[($vfAnc + 1)..($vfAnc + 6)]
if ($vlBlock[0] -cne ($T + $T + $T + '#ifdef IRLITE_VOLUMETRIC')) { throw "VL block head unexpected" }
if ($vlBlock[-1] -cne ($T + $T + $T + '#endif')) { throw "VL block tail unexpected" }
if ($vf[$vfAnc + 7] -cne ($T + $T + '#endif')) { throw "expected the VFOG #endif after the VL block" }

# Entities_FS: bit-7 replace
$ef = FileText "$mod\Lib\Programs\Gbuffers\Entities_FS.glsl"
if (-not $ef.Contains('Pack2xU8_to_U16(vec2(parallaxShadow, (v_materialIDs + 128.0) / 255.0))')) { throw "Entities_FS +128 missing" }

# shaders.properties: features flag, main-screen entry, screen tree, sliders
$pr  = Lines "$mod\shaders.properties"
$prO = Lines "$org\shaders.properties"
$shIdx = -1
for ($i = 0; $i -lt $pr.Count; $i++) { if ($pr[$i].Contains('[IRLIGHTS]')-and $pr[$i].Contains('<empty>')) { $shIdx = $i; break } }
if ($shIdx -lt 0) { throw "main-screen [IRLIGHTS] line not found" }
$shLine1 = $pr[$shIdx]
$shLine2 = $pr[$shIdx + 1]
if (-not $shLine1.StartsWith($T + $T + '[IRLIGHTS]')) { throw "main-screen line 1 unexpected: $shLine1" }
if (-not $shLine2.StartsWith($T + $T + '[SHORTCUT]')) { throw "main-screen line 2 unexpected: $shLine2" }
$shAnchor = $shLine2.Substring(2)              # the pristine substring, tabs excluded
$shBody1  = $shLine1.Substring(2)              # replace keeps the original leading tabs
$scAncText = 'screen.LIGHTING=[LIGHT_SOURCE] [HELDLIGHT] [SHADOW]'
$scAnc = IndexOfLine $pr $scAncText
$treeEnd = -1                                  # content-defined end: the flat screen.IRLIGHTS line (ends in IRLITE_SHADOWS)
for ($i = $scAnc + 1; $i -lt $pr.Count; $i++) { if ($pr[$i].TrimStart($T).StartsWith('screen.IRLIGHTS ') -and $pr[$i].EndsWith('IRLITE_SHADOWS')) { $treeEnd = $i; break } }
if ($treeEnd -lt 0) { throw "flat screen.IRLIGHTS line (ending IRLITE_SHADOWS) not found" }
$prTree = $pr[($scAnc + 1)..$treeEnd]
if ($prTree[0] -cne '') { throw "screen tree must start with a blank line" }
if (-not $prTree[1].StartsWith($T + 'screen.IRLIGHTS')) { throw "screen tree head unexpected" }
if ($prTree -match 'screen\.IRLIGHTS_(SPECULAR|SHADOWS|TOON|VOLUMETRIC|OUTLINE)') { throw "flat screen must carry no IRLIGHTS sub-screens" }
$slAncText = 'sliders=PT_VOXEL_RESOLUTION_X PT_VOXEL_RESOLUTION_Y \'
$slAnc = IndexOfLine $pr $slAncText
$slBody = $pr[$slAnc + 1]
if (-not $slBody.StartsWith($T + 'IRLITE_INTENSITY')) { throw "sliders body head unexpected" }
if (-not $slBody.EndsWith('IRLITE_TOON_SMOOTH \')) { throw "sliders body tail unexpected" }

# lang/en_us.lang: label block
$lg  = Lines "$mod\lang\en_us.lang"
$lgO = Lines "$org\lang\en_us.lang"
$lgAncText = 'option.iterationRP_VERSION=iterationRP by Tahnass'
$lgAncO = IndexOfLine $lgO $lgAncText
$lgNextO = $lgO[$lgAncO + 1]
$lgAnc = IndexOfLine $lg $lgAncText
$lgEnd = IndexOfLineFrom $lg $lgNextO ($lgAnc + 1)
$langBody = $lg[($lgAnc + 1)..($lgEnd - 1)]
if ($langBody[0] -cne '') { throw "lang body must start with a blank line" }
if (-not $langBody[1].StartsWith('# IRLights')) { throw "lang body head unexpected" }
if (-not ($langBody -match 'IRLITE_OUTLINE')) { throw "outline labels missing from the lang body" }

# ---- assemble the patch ----
$sb = New-Object System.Text.StringBuilder
function Emit($s) { [void]$sb.Append($s).Append("`n") }
function EmitBody($lines) { Emit '<<<'; foreach ($l in $lines) { Emit $l }; Emit '>>>' }
function EmitFile($relPath, $text) {
    Emit "+file $relPath"
    Emit '<<<'
    [void]$sb.Append($text)                    # ends with \n -> blank last body line preserves the trailing newline
    Emit '>>>'
}

Emit '# IRLite point + spot lights for IterationRP (by Tahnass). Outline = array-free LocalLightOutline rim, accumulated pre-albedo into diffuse (AE-safe on this pack).'
Emit '@name    IterationRP lights'
Emit '@target  IterationRP'
Emit '@irlite  1'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + shadows + volumetric) ---'
EmitFile 'shaders/Lib/irlite_lights.glsl' $libText
Emit ''
Emit '# --- diffuse + specular in the main opaque shading pass ---'
Emit '@file shaders/Lib/Programs/Composite/Soild_FS.glsl'
Emit ('after "#include \"/Lib/BasicFunctions/Blocklight.glsl\""')
EmitBody @('#define IRLITE_SURFACE_PASS', '#include "/Lib/irlite_lights.glsl"')
Emit ('before "\t\t\tcolor = color * gbuffer.albedo + sunlightSpecular;"')
EmitBody $sfDiffuse
Emit ('after "\t\t\tcolor = color * gbuffer.albedo + sunlightSpecular;"')
EmitBody $sfSpec
Emit ''
Emit '# --- volumetric light in the volumetric pass ---'
Emit '@file shaders/Lib/Programs/Composite/Volumetric_FS.glsl'
Emit ('after "#include \"/Lib/BasicFunctions/Blocklight.glsl\""')
EmitBody @('#define IRLITE_VL_PASS', '#include "/Lib/irlite_lights.glsl"')
Emit ('after "\t\t\t' + $vfAncText.Substring(3) + '"')
EmitBody $vlBlock
Emit ''
Emit '# --- mark entity fragments as non-terrain (bit 7 of materialID) ---'
Emit '@file shaders/Lib/Programs/Gbuffers/Entities_FS.glsl'
Emit 'replace "Pack2xU8_to_U16(vec2(parallaxShadow, v_materialIDs / 255.0))"'
EmitBody @('Pack2xU8_to_U16(vec2(parallaxShadow, (v_materialIDs + 128.0) / 255.0))')
Emit ''
Emit '# --- Iris: the IRLite settings screens (pack already declares the SSBO feature) ---'
Emit '@file shaders/shaders.properties'
Emit ('replace "' + $shAnchor.Replace('\', '\\') + '"')
EmitBody @($shBody1, $shLine2)
Emit ('after "' + $scAncText + '"')
EmitBody $prTree
Emit ('after "' + $slAncText.Replace('\', '\\') + '"')
EmitBody @($slBody)
Emit ''
Emit '# --- option labels ---'
Emit '@file shaders/lang/en_us.lang'
Emit ('after "' + $lgAncText + '"')
EmitBody $langBody

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
