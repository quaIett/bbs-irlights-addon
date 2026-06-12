# Generates patches/bliss.irlights by splicing the IRLite bodies VERBATIM out
# of Shadres/Modification/Bliss (so the patch reproduces the working tree
# byte-for-byte). Anchors are unique literals captured from the PRISTINE pack.
# Bliss is a MIXED-EOL pack (mostly CRLF) -> SINGLE-LINE anchors only (see
# memory patcher.md). One Bliss anchor ends with a literal backslash and
# several carry tabs -> anchors are emitted through EscAnchor (\\, \t, \").
# Validate after generating: javac the 5 pure patcher classes + PatchHarness,
# apply to the pristine pack, then
#   git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol <out> <Mod>
# must be empty. See memory bliss-port-plan Phase 5.

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLite"
$mod  = "$repo\Shadres\Modification\Bliss\shaders"
$pris = "$repo\Shadres\Original\Bliss\shaders"
$out  = "$repo\patches\bliss.irlights"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function IndexOfLine($lines, $text) {
    $found = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -ceq $text) {
            if ($found -ge 0) { throw "line not unique: $text" }
            $found = $i
        }
    }
    if ($found -lt 0) { throw "line not found: $text" }
    return $found
}
function IndexOfLineStarting($lines, $prefix) {
    $found = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].StartsWith($prefix)) {
            if ($found -ge 0) { throw "prefix not unique: $prefix" }
            $found = $i
        }
    }
    if ($found -lt 0) { throw "prefix not found: $prefix" }
    return $found
}
function EscAnchor($s) { return $s.Replace('\', '\\').Replace('"', '\"').Replace("`t", '\t') }
function AssertIncludeTriplet($lines, $anchorIdx, $defineLine) {
    if ($lines[$anchorIdx + 1] -cne '') { throw "expected blank after include anchor" }
    if ($lines[$anchorIdx + 2] -cne $defineLine) { throw "expected $defineLine" }
    if ($lines[$anchorIdx + 3] -cne '#include "/lib/irlite/irlite_lights.glsl"') { throw "expected the irlite include line" }
}

# ---- the lib (new file) ----
$libText = FileText "$mod\lib\irlite\irlite_lights.glsl"

# ---- composite1.fsh: include triplet + the pre-Emission block ----
$c1 = Lines "$mod\dimensions\composite1.fsh"
$c1Inc = IndexOfLine $c1 '#include "/lib/diffuse_lighting.glsl"'
AssertIncludeTriplet $c1 $c1Inc '#define IRLITE_SURFACE_PASS'
$E = IndexOfLine $c1 "`t`tEmission(gl_FragData[0].rgb, albedo, SpecularTex.a, exposure);"
$I = IndexOfLine $c1 "`t`t#ifdef IRLITE_ACTIVE"
$c1Body = $c1[$I..($E - 1)]
if ($c1Body[-1] -cne '') { throw "expected trailing blank in c1Body" }
if ($c1Body[-2] -cne "`t`t#endif") { throw "c1Body tail unexpected" }

# ---- all_translucent.fsh: include triplet + the post-FinalColor block ----
$at = Lines "$mod\dimensions\all_translucent.fsh"
$atInc = IndexOfLine $at '#include "/lib/diffuse_lighting.glsl"'
AssertIncludeTriplet $at $atInc '#define IRLITE_SURFACE_PASS'
$F = IndexOfLine $at "`tvec3 FinalColor = (Indirect_lighting + Direct_lighting) * Albedo;"
if ($at[$F + 1] -cne '') { throw "expected blank after FinalColor" }
if ($at[$F + 2] -cne "`t#ifdef IRLITE_ACTIVE") { throw "all_translucent block head unexpected" }
$atEnd = -1
for ($i = $F + 2; $i -lt $at.Count; $i++) {
    if ($at[$i] -ceq "`t#endif" -and $at[$i - 1] -ceq "`t}") { $atEnd = $i; break }
}
if ($atEnd -lt 0) { throw "all_translucent block end not found" }
$atBody = $at[($F + 1)..$atEnd]

# ---- composite2.fsh: include triplet + two replace bodies ----
$c2 = Lines "$mod\dimensions\composite2.fsh"
$c2Inc = IndexOfLine $c2 '#include "/lib/waterBump.glsl"'
AssertIncludeTriplet $c2 $c2Inc '#define IRLITE_VL_PASS'
$A = IndexOfLine $c2 "`tgl_FragData[0] = clamp(VolumetricFog, 0.0, 65000.0);"
if ($c2[$A + 1] -cne '') { throw "expected blank after the VolumetricFog write" }
if ($c2[$A + 2] -cne "`t#if defined IRLITE_ACTIVE && defined IRLITE_VOLUMETRIC") { throw "c2 march block head unexpected" }
$endA = -1
for ($i = $A + 2; $i -lt $c2.Count; $i++) {
    if ($c2[$i] -ceq "`t#endif" -and $c2[$i - 1] -ceq "`tgl_FragData[0].rgb += irliteVL;") { $endA = $i; break }
}
if ($endA -lt 0) { throw "c2 march block end not found" }
$c2BodyA = $c2[$A..$endA]
$B = IndexOfLine $c2 "`t`tgl_FragData[0] = clamp(vec4(vl,1.0),0.000001,65000.);"
if ($c2[$B + 1] -cne '') { throw "expected blank after the underwater write" }
if ($c2[$B + 2] -cne "`t`t#if defined IRLITE_ACTIVE && defined IRLITE_VOLUMETRIC") { throw "c2 re-add block head unexpected" }
if ($c2[$B + 4] -cne "`t`t#endif") { throw "c2 re-add block tail unexpected" }
$c2BodyB = $c2[$B..($B + 4)]

# ---- composite3.fsh: include triplet + the ink block (before the fog merge) ----
$c3 = Lines "$mod\dimensions\composite3.fsh"
$c3Inc = IndexOfLine $c3 '#include "/lib/DistantHorizons_projections.glsl"'
AssertIncludeTriplet $c3 $c3Inc '#define IRLITE_COMPOSITE_PASS'
$V = IndexOfLine $c3 '  color *= vl.a*cloudAlpha ;'
$S3 = IndexOfLine $c3 '  #if defined IRLITE_ACTIVE && defined IRLITE_OUTLINE'
$c3Body = $c3[$S3..($V - 1)]
if ($c3Body[-1] -cne '') { throw "expected trailing blank in c3Body" }
if ($c3Body[-2] -cne '  #endif') { throw "c3Body tail unexpected" }

# ---- shaders.properties: features, main-screen row, screens block, sliders ----
$pr = Lines "$mod\shaders.properties"
$prP = Lines "$pris\shaders.properties"
$featIdx = IndexOfLineStarting $pr 'iris.features.optional = '
$featLine = $pr[$featIdx]
if (-not $featLine.EndsWith(' SSBO')) { throw "features line does not end with SSBO" }
$featAnchorIdx = IndexOfLineStarting $prP 'iris.features.optional = '
$featAnchor = $prP[$featAnchorIdx]
if ($featAnchor + ' SSBO' -cne $featLine) { throw "features anchor/body mismatch" }
$M = IndexOfLine $pr '[Misc_Settings] [Mod_support] \'
$miscBody = $pr[$M..($M + 2)]
if ($miscBody[2] -cne '[IRLITE_SETTINGS] <empty> \') { throw "main-screen row unexpected" }
$SB = IndexOfLine $pr '        screen.selection_box_outline = SELECT_BOX SELECT_BOX_COL_R SELECT_BOX_COL_G SELECT_BOX_COL_B'
$endS = IndexOfLineStarting $pr '        screen.IRLITE_OUTLINE_SCREEN = '
$scrBody = $pr[($SB + 1)..$endS]
if ($scrBody[0] -cne '' -or $scrBody[1] -cne '') { throw "expected 2 leading blanks in screens body" }
if (-not $scrBody[2].StartsWith('######## IRLITE')) { throw "screens banner unexpected" }
$slIdx2 = IndexOfLineStarting $pr 'sliders = '
$slTriple = 'LPV_SATURATION LPV_TINT_SATURATION LPV_NORMAL_STRENGTH'
$slPos = $pr[$slIdx2].IndexOf($slTriple)
if ($slPos -lt 0) { throw "sliders tail anchor not found" }
$slBody = $pr[$slIdx2].Substring($slPos)
if (-not $slBody.EndsWith('IRLITE_OUTLINE_DEPTH_THRESHOLD')) { throw "sliders body tail unexpected" }

# ---- lang: anchors and bodies read from the files (no cyrillic/section-sign
# literals in this script - PS 5.1 source encoding dodge) ----
$lgEn = Lines "$mod\lang\en_us.lang"
$YenIdx = IndexOfLineStarting $lgEn 'option.TRANSLUCENT_COLORED_SHADOWS.comment = '
$enAnchor = $lgEn[$YenIdx]
$enTail = $lgEn[($YenIdx + 1)..($lgEn.Count - 1)]
while ($enTail[-1] -eq '') { $enTail = $enTail[0..($enTail.Count - 2)] }
if ($enTail[0] -cne '' -or -not $enTail[1].StartsWith('######## IRLITE')) { throw "enTail head unexpected" }
$lgRu = Lines "$mod\lang\ru_RU.lang"
$YruIdx = IndexOfLineStarting $lgRu 'option.TRANSLUCENT_COLORED_SHADOWS.comment = '
$ruAnchor = $lgRu[$YruIdx]
$ruTail = $lgRu[($YruIdx + 1)..($lgRu.Count - 1)]
while ($ruTail[-1] -eq '') { $ruTail = $ruTail[0..($ruTail.Count - 2)] }
if ($ruTail[0] -cne '' -or -not $ruTail[1].StartsWith('######## IRLITE')) { throw "ruTail head unexpected" }

# ---- assemble the patch ----
$sb = New-Object System.Text.StringBuilder
function Emit($s) { [void]$sb.Append($s).Append("`n") }
function EmitBody($lines) { Emit '<<<'; foreach ($l in $lines) { Emit $l }; Emit '>>>' }
function EmitFile($relPath, $text) {
    Emit "+file $relPath"
    Emit '<<<'
    if ($text.EndsWith("`n")) {
        [void]$sb.Append($text)
        Emit ''                             # blank last body line -> the applier emits the trailing \n
    } else {
        [void]$sb.Append($text).Append("`n")
    }
    Emit '>>>'
}

Emit '# IRLite point + spot lights for Bliss (by X0nk, Chocapic13 lineage).'
Emit '@name    Bliss lights'
Emit '@target  Bliss'
Emit '@packversion V2.1.2'
Emit '@irlite  1'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + outline + volumetric) ---'
EmitFile 'shaders/lib/irlite/irlite_lights.glsl' $libText
Emit ''
Emit '# --- deferred opaque diffuse + specular (linear albedo, native masks) ---'
Emit '@file shaders/dimensions/composite1.fsh'
Emit ('after "' + (EscAnchor '#include "/lib/diffuse_lighting.glsl"') + '"')
EmitBody @('', '#define IRLITE_SURFACE_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit ('before "' + (EscAnchor "`t`tEmission(gl_FragData[0].rgb, albedo, SpecularTex.a, exposure);") + '"')
EmitBody $c1Body
Emit ''
Emit '# --- forward translucent diffuse + specular (water + translucent entities) ---'
Emit '@file shaders/dimensions/all_translucent.fsh'
Emit ('after "' + (EscAnchor '#include "/lib/diffuse_lighting.glsl"') + '"')
EmitBody @('', '#define IRLITE_SURFACE_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit ('after "' + (EscAnchor "`tvec3 FinalColor = (Indirect_lighting + Direct_lighting) * Albedo;") + '"')
EmitBody $atBody
Emit ''
Emit '# --- volumetric march inside the pack''s reduced-res VL pass ---'
Emit '@file shaders/dimensions/composite2.fsh'
Emit ('after "' + (EscAnchor '#include "/lib/waterBump.glsl"') + '"')
EmitBody @('', '#define IRLITE_VL_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit ('replace "' + (EscAnchor "`tgl_FragData[0] = clamp(VolumetricFog, 0.0, 65000.0);") + '"')
EmitBody $c2BodyA
Emit ('replace "' + (EscAnchor "`t`tgl_FragData[0] = clamp(vec4(vl,1.0),0.000001,65000.);") + '"')
EmitBody $c2BodyB
Emit ''
Emit '# --- rim outline ink in the merge pass (before the fog merge) ---'
Emit '@file shaders/dimensions/composite3.fsh'
Emit ('after "' + (EscAnchor '#include "/lib/DistantHorizons_projections.glsl"') + '"')
EmitBody @('', '#define IRLITE_COMPOSITE_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit ('before "' + (EscAnchor '  color *= vl.a*cloudAlpha ;') + '"')
EmitBody $c3Body
Emit ''
Emit '# --- SSBO feature flag, main-screen entry, screens + sliders ---'
Emit '@file shaders/shaders.properties'
Emit ('replace "' + (EscAnchor $featAnchor) + '"')
EmitBody @($featLine)
Emit ('replace "' + (EscAnchor '[Misc_Settings] [Mod_support] \') + '"')
EmitBody $miscBody
Emit ('after "' + (EscAnchor '        screen.selection_box_outline = SELECT_BOX SELECT_BOX_COL_R SELECT_BOX_COL_G SELECT_BOX_COL_B') + '"')
EmitBody $scrBody
Emit ('replace "' + (EscAnchor $slTriple) + '"')
EmitBody @($slBody)
Emit ''
Emit '# --- option labels + tooltips (English) ---'
Emit '@file shaders/lang/en_us.lang'
Emit ('after "' + (EscAnchor $enAnchor) + '"')
EmitBody $enTail
Emit ''
Emit '# --- option labels + tooltips (Russian - the pack ships ru_RU) ---'
Emit '@file shaders/lang/ru_RU.lang'
Emit ('after "' + (EscAnchor $ruAnchor) + '"')
EmitBody $ruTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
