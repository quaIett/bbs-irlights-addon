# Generates patches/complementaryreimagined.irlpatch by splicing the IRLite
# bodies VERBATIM out of Shadres/Modification/ComplementaryReimagined (so the
# patch reproduces the working tree byte-for-byte). Anchors are unique literals
# captured from the PRISTINE pack — verified before generation.
# Validate after generating: javac harness applies the patch to the pristine
# pack and `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol
# <out> <Modification>` must be empty. See memory complementary-port-plan
# Phase 5 + the VL perf-rework FOLLOW-UP (the added deferred2 pass ops).

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLite"
$mod  = "$repo\Shadres\Modification\ComplementaryReimagined\shaders"
$out  = "$repo\patches\complementaryreimagined.irlpatch"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function IndexOfLine($lines, $text) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found: $text"
}
function IndexOfLineAfter($lines, $text, $from) {
    for ($i = $from; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found after ${from}: $text"
}

# ---- extract bodies from Modification ----
$libText = FileText "$mod\lib\irlite\irlite_lights.glsl"
$d2Text  = FileText "$mod\program\deferred2.glsl"
$wrapTexts = @{}
foreach ($w in @("world0\deferred2.fsh","world0\deferred2.vsh","world1\deferred2.fsh","world1\deferred2.vsh","world-1\deferred2.fsh","world-1\deferred2.vsh")) {
    $wrapTexts[$w] = FileText "$mod\$w"
}

$ml = Lines "$mod\lib\lighting\mainLighting.glsl"
$S = IndexOfLine $ml '    finalDiffuse = sqrt(max(finalDiffuse, vec3(0.0))); // sqrt() for a bit more realistic light mix, max() to prevent NaNs'
$A = IndexOfLineAfter $ml '    // Apply Lighting' $S
$mlBlock = $ml[($S + 1)..($A - 2)]          # leading blank + block; drop the trailing blank (A-1)
if ($ml[$A - 1] -ne '') { throw "expected blank before // Apply Lighting" }
if ($mlBlock[0] -ne '') { throw "expected leading blank in mlBlock" }
$H = IndexOfLine $ml '    color.rgb += lightHighlight;'
$D = IndexOfLine $ml '    color.rgb *= pow2(1.0 - darknessLightFactor);'
$mlSpec = $ml[($H + 1)..($D - 1)]           # the 3-line spec add, no surrounding blanks

$c1 = Lines "$mod\program\composite1.glsl"
$P = IndexOfLine $c1 '    color = pow(color, vec3(2.2));'
$OStart = IndexOfLine $c1 '    #if defined IRLITE_ACTIVE && defined IRLITE_OUTLINE'
if ($c1[$P - 1] -ne '') { throw "expected blank before pow line" }
$c1Outline = $c1[$OStart..($P - 1)]         # block + trailing blank (before-op needs the trailing \n)
$L = IndexOfLineAfter $c1 '    #ifdef LIGHTSHAFTS_ACTIVE' $P
if ($c1[$L - 1] -ne '') { throw "expected blank before LIGHTSHAFTS" }
$c1Vl = $c1[($P + 1)..($L - 2)]             # leading blank + VL block; drop the trailing blank
if ($c1Vl[0] -ne '') { throw "expected leading blank in c1Vl" }

$ps = Lines "$mod\lib\pipelineSettings.glsl"
$F = IndexOfLine $ps 'const int colortex10Format = RGB16F;        //IRLite reduced-res volumetric (added deferred2 pass)'
if ($ps[$F - 1] -ne 'const int colortex8Format = RGBA16F;        //SSR results for WSR, topmost translucent opacity') { throw "colortex10Format must directly follow colortex8Format" }
$psFormat = @($ps[$F])

$pr = Lines "$mod\shaders.properties"
$T = IndexOfLine $pr '    # IRLite reduced-resolution volumetric pass (added deferred2 -> colortex10)'
$prToggles = $pr[$T..($T + 3)]              # comment + the 3 program toggles
if ($prToggles[3] -ne '    program.world1/deferred2.enabled=IRLITE_VOLUMETRIC') { throw "toggle block tail unexpected" }
if ($pr[$T + 4] -ne '') { throw "expected blank after the toggle block" }
if ($pr[$T + 5] -ne '# Miscellaneous') { throw "expected # Miscellaneous after the toggle block" }
$prToggles += ''                            # trailing blank (before-op reproduces the blank above the anchor)
$SB = IndexOfLine $pr '        # IRLite reduced-res volumetric buffer (written by the added deferred2 pass)'
$prSize = $pr[$SB..($SB + 1)]               # comment + size.buffer.colortex10
if ($prSize[1] -ne '        size.buffer.colortex10 = IRLITE_VL_RESOLUTION IRLITE_VL_RESOLUTION') { throw "size.buffer body unexpected" }
if ($pr[$SB - 1] -ne '        size.buffer.colortex7 = REFLECTION_RES REFLECTION_RES') { throw "size.buffer block must follow the colortex7 line" }
$X = IndexOfLine $pr '        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE'
$O = -1; for ($i = $X + 1; $i -lt $pr.Count; $i++) { if ($pr[$i].StartsWith('    screen.OTHER_SETTINGS=')) { $O = $i; break } }
if ($O -lt 0) { throw "OTHER_SETTINGS not found" }
$propsScreens = $pr[($X + 1)..($O - 1)]     # the 6 IRLITE screen lines
$slLine = $pr | Where-Object { $_.TrimStart().StartsWith('sliders=') }
if (@($slLine).Count -ne 1) { throw "sliders line not unique" }
$slIdx = $slLine.IndexOf('END_STAR_INTENSITY GENERATED_NORMAL_RES')
if ($slIdx -lt 0) { throw "sliders tail anchor not found" }
$slBody = $slLine.Substring($slIdx)
if (-not $slBody.EndsWith('IRLITE_OUTLINE_DEPTH_THRESHOLD')) { throw "sliders body tail unexpected" }
if ($slBody -notmatch 'IRLITE_VL_SHADOW_STRIDE') { throw "sliders body missing IRLITE_VL_SHADOW_STRIDE" }

$lg = Lines "$mod\lang\en_US.lang"
$Y = IndexOfLine $lg 'option.XLIGHT_CURVE.comment=Adjusts how quickly the intensity of blocklight fades away as it travels distance away from the light source.'
$langTail = $lg[($Y + 1)..($lg.Count - 1)]  # 2 leading blanks + the whole IRLite block
while ($langTail[-1] -eq '') { $langTail = $langTail[0..($langTail.Count - 2)] }

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

Emit '# IRLite — point + spot lights for Complementary (Reimagined/Unbound), ported'
Emit '# from the Photon inject: diffuse + specular + real-time shadows + volumetrics'
Emit '# + toon + rim outline, plus an in-shader settings screen (Iris: Shader Pack'
Emit '# Settings -> Lighting Settings -> IRLite Lights).'
Emit '#'
Emit '# Complementary is FORWARD-shaded, so diffuse+specular hook DoLighting() in'
Emit '# lib/lighting/mainLighting.glsl (covers all 12 gbuffers programs; the entity'
Emit '# flag is compile-time). The volumetric march runs in an ADDED reduced-'
Emit '# resolution pass (deferred2 -> colortex10, sized by the IRLITE_VL_RESOLUTION'
Emit '# option through the pack''s own REFLECTION_RES size.buffer mechanism);'
Emit '# composite1 bilinear-upsamples it and hosts the rim outline — the outline'
Emit '# must run after the translucent phase because BBS replay models render'
Emit '# through the translucent entity programs. No wrapper edits beyond the added'
Emit '# deferred2 pair: the pack is #version 130 and the inject carries its own'
Emit '# in-file #extension lines (the pack''s own SSBO idiom); the SSBO feature'
Emit '# flag is already declared.'
Emit '@name    Complementary lights'
Emit '@target  ComplementaryReimagined'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + outline + volumetric) ---'
EmitFile 'shaders/lib/irlite/irlite_lights.glsl' $libText
Emit ''
Emit '# --- the added reduced-resolution volumetric pass ---'
EmitFile 'shaders/program/deferred2.glsl' $d2Text
EmitFile 'shaders/world0/deferred2.fsh' $wrapTexts['world0\deferred2.fsh']
EmitFile 'shaders/world0/deferred2.vsh' $wrapTexts['world0\deferred2.vsh']
EmitFile 'shaders/world1/deferred2.fsh' $wrapTexts['world1\deferred2.fsh']
EmitFile 'shaders/world1/deferred2.vsh' $wrapTexts['world1\deferred2.vsh']
EmitFile 'shaders/world-1/deferred2.fsh' $wrapTexts['world-1\deferred2.fsh']
EmitFile 'shaders/world-1/deferred2.vsh' $wrapTexts['world-1\deferred2.vsh']
Emit ''
Emit '# --- forward diffuse + specular in DoLighting ---'
Emit '@file shaders/lib/lighting/mainLighting.glsl'
Emit 'after "#include \"/lib/lighting/ggx.glsl\""'
EmitBody @('#define IRLITE_SURFACE_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit 'after "    finalDiffuse = sqrt(max(finalDiffuse, vec3(0.0))); // sqrt() for a bit more realistic light mix, max() to prevent NaNs"'
EmitBody $mlBlock
Emit 'after "    color.rgb += lightHighlight;"'
EmitBody $mlSpec
Emit ''
Emit '# --- volumetric upsample (after pow 2.2, linear) + rim outline (before pow 2.2, gamma) ---'
Emit '@file shaders/program/composite1.glsl'
Emit 'after "#include \"/lib/util/spaceConversion.glsl\""'
EmitBody @('#include "/lib/irlite/irlite_lights.glsl"')
Emit 'before "    color = pow(color, vec3(2.2));"'
EmitBody $c1Outline
Emit 'after "    color = pow(color, vec3(2.2));"'
EmitBody $c1Vl
Emit ''
Emit '# --- the reduced-res VL buffer format (the pack declares formats inside this comment block) ---'
Emit '@file shaders/lib/pipelineSettings.glsl'
Emit 'after "const int colortex8Format = RGBA16F;        //SSR results for WSR, topmost translucent opacity"'
EmitBody $psFormat
Emit ''
Emit '# --- deferred2 program toggle + buffer size, settings screens + sliders ---'
Emit '@file shaders/shaders.properties'
Emit 'before "# Miscellaneous"'
EmitBody $prToggles
Emit 'after "        size.buffer.colortex7 = REFLECTION_RES REFLECTION_RES"'
EmitBody $prSize
Emit 'replace "VANILLAAO_I PLAYER_SHADOW"'
EmitBody @('VANILLAAO_I PLAYER_SHADOW [IRLITE_SETTINGS]')
Emit 'after "        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE"'
EmitBody $propsScreens
Emit 'replace "END_STAR_INTENSITY GENERATED_NORMAL_RES"'
EmitBody @($slBody)
Emit ''
Emit '# --- option labels + tooltips ---'
Emit '@file shaders/lang/en_US.lang'
Emit 'after "option.XLIGHT_CURVE.comment=Adjusts how quickly the intensity of blocklight fades away as it travels distance away from the light source."'
EmitBody $langTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
