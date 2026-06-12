# Generates patches/complementaryreimagined.irlpatch by splicing the IRLite
# bodies VERBATIM out of Shadres/Modification/ComplementaryReimagined (so the
# patch reproduces the working tree byte-for-byte). Anchors are unique literals
# captured from the PRISTINE pack — verified before generation.
# Validate after generating: javac harness applies the patch to the pristine
# pack and `git diff --no-index --ignore-cr-at-eol <out> <Modification>` must
# be empty. See memory complementary-port-plan Phase 5.

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLite"
$mod  = "$repo\Shadres\Modification\ComplementaryReimagined\shaders"
$out  = "$repo\patches\complementaryreimagined.irlpatch"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function IndexOfLine($lines, $text) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found: $text"
}
function IndexOfLineAfter($lines, $text, $from) {
    for ($i = $from; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found after ${from}: $text"
}

# ---- extract bodies from Modification ----
$libText = [IO.File]::ReadAllText("$mod\lib\irlite\irlite_lights.glsl").Replace("`r`n", "`n")

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

$pr = Lines "$mod\shaders.properties"
$X = IndexOfLine $pr '        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE'
$O = -1; for ($i = $X + 1; $i -lt $pr.Count; $i++) { if ($pr[$i].StartsWith('    screen.OTHER_SETTINGS=')) { $O = $i; break } }
if ($O -lt 0) { throw "OTHER_SETTINGS not found" }
$propsScreens = $pr[($X + 1)..($O - 1)]     # the 6 IRLITE screen lines

$lg = Lines "$mod\lang\en_US.lang"
$Y = IndexOfLine $lg 'option.XLIGHT_CURVE.comment=Adjusts how quickly the intensity of blocklight fades away as it travels distance away from the light source.'
$langTail = $lg[($Y + 1)..($lg.Count - 1)]  # 2 leading blanks + the whole IRLite block
while ($langTail[-1] -eq '') { $langTail = $langTail[0..($langTail.Count - 2)] }

# ---- assemble the patch ----
$sb = New-Object System.Text.StringBuilder
function Emit($s) { [void]$sb.Append($s).Append("`n") }
function EmitBody($lines) { Emit '<<<'; foreach ($l in $lines) { Emit $l }; Emit '>>>' }

Emit '# IRLite — point + spot lights for Complementary (Reimagined/Unbound), ported'
Emit '# from the Photon inject: diffuse + specular + real-time shadows + volumetrics'
Emit '# + toon + rim outline, plus an in-shader settings screen (Iris: Shader Pack'
Emit '# Settings -> Lighting Settings -> IRLite Lights).'
Emit '#'
Emit '# Complementary is FORWARD-shaded, so diffuse+specular hook DoLighting() in'
Emit '# lib/lighting/mainLighting.glsl (covers all 12 gbuffers programs; the entity'
Emit '# flag is compile-time). Volumetrics AND the outline both hook composite1 —'
Emit '# the outline must run after the translucent phase because BBS replay models'
Emit '# render through the translucent entity programs. No wrapper edits: the pack'
Emit '# is #version 130 and the inject carries its own in-file #extension lines'
Emit '# (the pack''s own SSBO idiom); the SSBO feature flag is already declared.'
Emit '@name    Complementary lights'
Emit '@target  ComplementaryReimagined'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + outline + volumetric) ---'
Emit '+file shaders/lib/irlite/irlite_lights.glsl'
Emit '<<<'
[void]$sb.Append($libText)                  # ends with \n -> blank last body line preserves the trailing newline
Emit '>>>'
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
Emit '# --- volumetrics (after pow 2.2, linear) + rim outline (before pow 2.2, gamma) ---'
Emit '@file shaders/program/composite1.glsl'
Emit 'after "#include \"/lib/util/spaceConversion.glsl\""'
EmitBody @('#include "/lib/irlite/irlite_lights.glsl"')
Emit 'before "    color = pow(color, vec3(2.2));"'
EmitBody $c1Outline
Emit 'after "    color = pow(color, vec3(2.2));"'
EmitBody $c1Vl
Emit ''
Emit '# --- settings screens + sliders ---'
Emit '@file shaders/shaders.properties'
Emit 'replace "VANILLAAO_I PLAYER_SHADOW"'
EmitBody @('VANILLAAO_I PLAYER_SHADOW [IRLITE_SETTINGS]')
Emit 'after "        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE"'
EmitBody $propsScreens
Emit 'replace "END_STAR_INTENSITY GENERATED_NORMAL_RES"'
EmitBody @('END_STAR_INTENSITY GENERATED_NORMAL_RES IRLITE_INTENSITY IRLITE_SPECULAR_INTENSITY IRLITE_SHADOW_QUALITY IRLITE_SHADOW_SIZE IRLITE_SHADOW_BIAS IRLITE_SHADOW_NORMAL_OFFSET IRLITE_VL_INTENSITY IRLITE_VL_STEPS IRLITE_VL_TIP_BOOST IRLITE_VL_TIP_RADIUS IRLITE_VL_MAX_DIST IRLITE_TOON_BANDS IRLITE_TOON_SMOOTH IRLITE_OUTLINE_STRENGTH IRLITE_OUTLINE_PIXEL_SIZE IRLITE_OUTLINE_WRAP IRLITE_OUTLINE_DEPTH_THRESHOLD')
Emit ''
Emit '# --- option labels + tooltips ---'
Emit '@file shaders/lang/en_US.lang'
Emit 'after "option.XLIGHT_CURVE.comment=Adjusts how quickly the intensity of blocklight fades away as it travels distance away from the light source."'
EmitBody $langTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))