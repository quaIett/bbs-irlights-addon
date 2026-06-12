# Generates patches/bsl.irlights by splicing the IRLite bodies VERBATIM out of
# Shadres/Modification/BSL (so the patch reproduces the working tree
# byte-for-byte). Anchors are unique literals captured from the PRISTINE pack —
# verified before generation. BSL IS A CRLF PACK: every anchor below is
# SINGLE-LINE (a multi-line anchor's \n would never match the raw \r\n bytes).
# Validate after generating: javac harness applies the patch to the pristine
# pack and `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol
# <out> <Modification>` must be empty. See memory bsl-port-plan Phase 5.

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLite"
$mod  = "$repo\Shadres\Modification\BSL\shaders"
$out  = "$repo\patches\bsl.irlights"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function IndexOfLine($lines, $text) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found: $text"
}

# ---- extract bodies from Modification ----
$libText = FileText "$mod\lib\irlite\irlite_lights.glsl"
$d2Text  = FileText "$mod\program\deferred2.glsl"
$wrapTexts = @{}
foreach ($w in @("world0\deferred2.fsh","world0\deferred2.vsh","world1\deferred2.fsh","world1\deferred2.vsh","world-1\deferred2.fsh","world-1\deferred2.vsh")) {
    $wrapTexts[$w] = FileText "$mod\$w"
}

$fl = Lines "$mod\lib\lighting\forwardLighting.glsl"
$S2 = IndexOfLine $fl '    //albedo = vec3(0.5);'
$E2 = IndexOfLine $fl '    albedo *= max(sceneLighting + blockLighting + emissiveLighting + nightVisionLighting + minLighting, vec3(0.0));'
$flCompute = $fl[($S2 + 1)..($E2 - 1)]      # #if..#endif + trailing blank (before-op)
if (-not $flCompute[0].StartsWith('    #if defined IRLITE_ACTIVE')) { throw "flCompute head unexpected" }
if ($flCompute[-1] -ne '') { throw "flCompute must end with a blank line" }
$A3 = IndexOfLine $fl '    albedo *= vanillaDiffuse * smoothLighting * smoothLighting;'
$B3 = IndexOfLine $fl '    // albedo = blocklightCol * 0.25;'
$flAdd = $fl[($A3 + 1)..($B3 - 1)]          # leading blank + add block (after-op)
if ($flAdd[0] -ne '') { throw "flAdd must start with a blank line" }
if ($flAdd[-1] -ne '    #endif') { throw "flAdd tail unexpected" }

$cp = Lines "$mod\program\composite.glsl"
$R = IndexOfLine $cp "`tvec3 reflectionColor = pow(color.rgb, vec3(0.125)) * 0.5;"
$M = IndexOfLine $cp "`t#ifdef MCBL_SS"
$cpBlock = $cp[($R + 1)..($M - 2)]          # leading blank + outline block + blank + VL block (after-op; drop the blank before MCBL)
if ($cpBlock[0] -ne '') { throw "cpBlock must start with a blank line" }
if ($cp[$M - 1] -ne '') { throw "expected blank before #ifdef MCBL_SS" }
if ($cpBlock[-1] -ne "`t#endif") { throw "cpBlock tail unexpected" }

$pr = Lines "$mod\shaders.properties"
$S5 = IndexOfLine $pr '#IRLite SSBO Prerequisite (binding 7 is created and bound by the IRLite mod)'
$M5 = IndexOfLine $pr '#Multi-Colored Blocklight Prerequisites'
$prPrereq = $pr[$S5..($M5 - 1)]             # SSBO block + deferred2/size block + trailing blank (before-op)
if ($prPrereq[-1] -ne '') { throw "prPrereq must end with a blank line" }
$SAO = IndexOfLine $pr 'screen.AO=<empty> <empty> AO_METHOD <empty> <empty> <empty> AO_STRENGTH ambientOcclusionLevel'
$prScreens = $pr[($SAO + 1)..($SAO + 7)]    # blank + the 6 IRLITE screen lines (after-op)
if ($prScreens[0] -ne '') { throw "prScreens must start with a blank line" }
if (-not $prScreens[1].StartsWith('screen.IRLITE_SETTINGS=')) { throw "prScreens head unexpected" }
if (-not $prScreens[6].StartsWith('screen.IRLITE_OUTLINE_SETTINGS=')) { throw "prScreens tail unexpected" }
if ($pr[$SAO + 8] -ne '') { throw "expected blank after the IRLITE screens" }
$slLine = $pr | Where-Object { $_.StartsWith('sliders=') }
if (@($slLine).Count -ne 1) { throw "sliders line not unique" }
$slIdx = $slLine.IndexOf('RETRO_FILTER_DEPTH WORLD_CURVATURE_SIZE')
if ($slIdx -lt 0) { throw "sliders tail anchor not found" }
$slBody = $slLine.Substring($slIdx)
if (-not $slBody.EndsWith('IRLITE_OUTLINE_DEPTH_THRESHOLD')) { throw "sliders body tail unexpected" }

$lg = Lines "$mod\lang\en_US.lang"
$Y = IndexOfLine $lg 'option.WHITE_WORLD.comment=Replaces textures with flat white color.'
$langTail = $lg[($Y + 1)..($lg.Count - 1)]  # leading blank + the whole IRLite block
while ($langTail[-1] -eq '') { $langTail = $langTail[0..($langTail.Count - 2)] }
if ($langTail[0] -ne '') { throw "langTail must start with a blank line" }
if ($langTail[1] -ne '#IRLite') { throw "langTail head unexpected" }

# ---- assemble the patch ----
$sb = New-Object System.Text.StringBuilder
function Emit($s) { [void]$sb.Append($s).Append("`n") }
function EmitBody($lines) { Emit '<<<'; foreach ($l in $lines) { Emit $l }; Emit '>>>' }
function EmitFile($relPath, $text) {
    Emit "+file $relPath"
    Emit '<<<'
    [void]$sb.Append($text)                 # ends with \n -> blank last body line preserves the trailing newline
    Emit '>>>'
}

Emit '# IRLite point + spot lights for BSL Shaders v10 (by Capt Tatsu).'
Emit '@name    BSL lights'
Emit '@target  BSL'
Emit '@packversion v10'
Emit '@irlite  1'
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
Emit '# --- forward diffuse + specular in GetLighting ---'
Emit '@file shaders/lib/lighting/forwardLighting.glsl'
Emit 'before "void GetLighting(inout vec3 albedo, out vec3 shadow, vec3 viewPos, vec3 worldPos, vec3 normal,"'
EmitBody @('#define IRLITE_SURFACE_PASS', '#include "/lib/irlite/irlite_lights.glsl"', '')
Emit 'before "    albedo *= max(sceneLighting + blockLighting + emissiveLighting + nightVisionLighting + minLighting, vec3(0.0));"'
EmitBody $flCompute
Emit 'after "    albedo *= vanillaDiffuse * smoothLighting * smoothLighting;"'
EmitBody $flAdd
Emit ''
Emit '# --- per-program flags: entity/hand/block lightMask gate + distant-LOD skip ---'
Emit '@file shaders/program/gbuffers_entities.glsl'
Emit 'after "#ifdef FSH"'
EmitBody @('#define IRLITE_NONTERRAIN // IRLite: entity/hand/block program (lightMask gate)')
Emit '@file shaders/program/gbuffers_entities_glowing.glsl'
Emit 'after "#ifdef FSH"'
EmitBody @('#define IRLITE_NONTERRAIN // IRLite: entity/hand/block program (lightMask gate)')
Emit '@file shaders/program/gbuffers_hand.glsl'
Emit 'after "#ifdef FSH"'
EmitBody @('#define IRLITE_NONTERRAIN // IRLite: entity/hand/block program (lightMask gate)')
Emit '@file shaders/program/gbuffers_block.glsl'
Emit 'after "#ifdef FSH"'
EmitBody @('#define IRLITE_NONTERRAIN // IRLite: entity/hand/block program (lightMask gate)')
Emit '@file shaders/program/dh_terrain.glsl'
Emit 'after "#ifdef FSH"'
EmitBody @('#define IRLITE_SKIP // IRLite: distant-LOD pass, IRLite lights stay out (see lib/irlite)')
Emit '@file shaders/program/dh_water.glsl'
Emit 'after "#ifdef FSH"'
EmitBody @('#define IRLITE_SKIP // IRLite: distant-LOD pass, IRLite lights stay out (see lib/irlite)')
Emit ''
Emit '# --- rim outline + volumetric upsample in composite ---'
Emit '@file shaders/program/composite.glsl'
Emit 'after "#include \"/lib/atmospherics/waterFog.glsl\""'
EmitBody @('', '#define IRLITE_COMPOSITE_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit 'after "vec3 reflectionColor = pow(color.rgb, vec3(0.125)) * 0.5;"'
EmitBody $cpBlock
Emit ''
Emit '# --- the reduced-res VL buffer format (the pack declares formats inside this comment block) ---'
Emit '@file shaders/program/final.glsl'
Emit 'after "const int colortex9Format = RGB16F; //colored light"'
EmitBody @('const int colortex10Format = RGB16F; //IRLite reduced-res volumetric light (deferred2)')
Emit ''
Emit '# --- SSBO feature flag, deferred2 toggle + buffer size, settings screens + sliders ---'
Emit '@file shaders/shaders.properties'
Emit 'replace "iris.features.optional=CUSTOM_IMAGES FADE_VARIABLE"'
EmitBody @('iris.features.optional=CUSTOM_IMAGES FADE_VARIABLE SSBO')
Emit 'before "#Multi-Colored Blocklight Prerequisites"'
EmitBody $prPrereq
Emit 'replace "DYNAMIC_HANDLIGHT HALF_LAMBERT"'
EmitBody @('DYNAMIC_HANDLIGHT HALF_LAMBERT [IRLITE_SETTINGS]')
Emit 'after "screen.AO=<empty> <empty> AO_METHOD <empty> <empty> <empty> AO_STRENGTH ambientOcclusionLevel"'
EmitBody $prScreens
Emit 'replace "RETRO_FILTER_DEPTH WORLD_CURVATURE_SIZE"'
EmitBody @($slBody)
Emit ''
Emit '# --- option labels + tooltips ---'
Emit '@file shaders/lang/en_US.lang'
Emit 'after "option.WHITE_WORLD.comment=Replaces textures with flat white color."'
EmitBody $langTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
