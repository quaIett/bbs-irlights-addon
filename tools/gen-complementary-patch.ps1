# Generates patches/complementaryreimagined.irlights by splicing the IRLite
# bodies VERBATIM out of Shadres/Modification/ComplementaryReimagined (so the
# patch reproduces the working tree byte-for-byte). Anchors are unique literals
# captured from the PRISTINE pack — verified before generation.
# Validate after generating: javac harness applies the patch to the pristine
# pack and `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol
# <out> <Modification>` must be empty. See memory complementary-port-plan
# Phase 5 + the VL perf-rework FOLLOW-UP (the added deferred2 pass ops).

param([string]$ModifiedShaders, [string]$OutputPatch)
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$mod = if ($ModifiedShaders) { $ModifiedShaders } else { Join-Path $repo 'Shadres/Modification/ComplementaryReimagined/shaders' }
$out = if ($OutputPatch) { $OutputPatch } else { Join-Path $repo 'patches/complementaryreimagined.irlights' }

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
$OStart = IndexOfLine $c1 '    #ifdef IRLITE_ACTIVE'
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
# Replay attachment (per-light profiles): the colortex9 format sits last in the comment
# block, the clear pair after ambientOcclusionLevel (the file's last line).
$RF = IndexOfLine $ps 'const int colortex9Format = RG32F;          //IRLite replay tag (r) and the depth it was written at (g)'
if ($ps[$RF + 1] -ne '*/') { throw "colortex9Format must be the last line of the format comment block" }
$psReplayFormat = @($ps[$RF])
$AO = IndexOfLine $ps 'const float ambientOcclusionLevel = 1.0;'
$psReplayClear = $ps[($AO + 1)..($AO + 2)]
if ($psReplayClear[0] -ne 'const bool colortex9Clear = true;' -or -not $psReplayClear[1].StartsWith('const vec4 colortex9ClearColor = vec4(0.0, 0.0, 0.0, 0.0);')) { throw "colortex9 clear pair unexpected" }
if ($ps.Count -ne $AO + 3) { throw "pipelineSettings must end with the colortex9 clear pair" }

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
$propsScreens = $pr[($X + 1)..($O - 1)]     # the single flat IRLIGHTS screen line
$slLine = $pr | Where-Object { $_.TrimStart().StartsWith('sliders=') }
if (@($slLine).Count -ne 1) { throw "sliders line not unique" }
# No sliders op any more: wave 0 moved every IRLITE_VL_* slider into the mod's
# globals UBO, wave 1 every IRLITE_OUTLINE_* one and wave 2 the last four
# (intensity, specular intensity, toon bands/smoothing). Tripwire: an IRLITE
# slider added to Modification would otherwise be silently missing from the patch.
if ($slLine -match 'IRLITE_') { throw "sliders line carries an IRLITE option but the patch has no sliders op" }

# Replay attachment tail of shaders.properties: from the IRLite comment to the end.
$RB = IndexOfLine $pr '# IRLite replay tag attachment (colortex9): written, never blended, by every gbuffers program (fallback wrappers included)'
if ($pr[$RB - 1] -ne '') { throw "expected blank before the replay attachment tail" }
$prReplay = @('') + $pr[$RB..($pr.Count - 1)]
foreach ($l in $pr[($RB + 1)..($pr.Count - 1)]) { if ($l -notmatch '^blend\.gbuffers_[a-z_]+\.colortex9=off$') { throw "unexpected line in the replay attachment tail: $l" } }
$RA = IndexOfLine $pr '    uniform.float.endFlashIntensityM=if(endFlashFactor0 > endFlashFactor1, sqrt(endFlashFactor0), endFlashFactor1)'
if ($RA -ne $RB - 2) { throw "the replay attachment tail must directly follow the endFlashIntensityM line" }

# gbuffers: every DRAWBUFFERS / RENDERTARGETS comment of the PRISTINE program gains the
# replay attachment (colortex9) and the write into it. The Modification copy must be
# exactly that — it is regenerated here, not hand-edited.
$gbOps = @()
$orig = Join-Path $repo 'Shadres/Original/ComplementaryReimagined/shaders'
foreach ($gb in (Get-ChildItem (Join-Path $orig 'program') -Filter 'gbuffers_*.glsl' | Sort-Object Name)) {
    $origText = FileText $gb.FullName
    $modText = FileText (Join-Path $mod "program\$($gb.Name)")
    $expected = $origText
    foreach ($m in [regex]::Matches($origText, '/\*\s*(DRAWBUFFERS|RENDERTARGETS):\s*([0-9,\s]+?)\s*\*/')) {
        # @() keeps a single target an array: assigning an if-block unrolls one element to a scalar.
        $ids = @(if ($m.Groups[1].Value -eq 'DRAWBUFFERS') { [string[]][char[]]$m.Groups[2].Value.Trim() } else { [string[]]($m.Groups[2].Value -split ',' | ForEach-Object { $_.Trim() }) })
        $anchor = $m.Value
        if (([regex]::Matches($origText, [regex]::Escape($anchor))).Count -ne 1) { throw "gbuffers anchor not unique in $($gb.Name): $anchor" }
        $body = @(('/* RENDERTARGETS: ' + (($ids + '9') -join ',') + ' */'), ('    gl_FragData[' + $ids.Count + '] = vec4(float(irlite_replayId), gl_FragCoord.z, 0.0, 1.0);'))
        $expected = $expected.Replace($anchor, ($body -join "`n"))
        $gbOps += ,@($gb.Name, $anchor, $body)
    }
    if ($modText -ne $expected) { throw "Modification program/$($gb.Name) is not the pristine file plus the replay attachment" }
}

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

Emit '# IRLite point + spot lights for Complementary (Reimagined/Unbound, by EminGT).'
Emit '@name    Complementary lights'
Emit '@target  ComplementaryReimagined'
Emit '@packversion r5.9.3'
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
Emit 'before "*/"'
EmitBody $psReplayFormat
Emit 'after "const float ambientOcclusionLevel = 1.0;"'
EmitBody $psReplayClear
Emit ''
Emit '# --- replay tag: the uniform every gbuffers program writes into the attachment ---'
Emit '@file shaders/lib/uniforms.glsl'
Emit 'after "uniform int entityId;"'
EmitBody @('uniform int irlite_replayId;')
Emit ''
Emit '# --- deferred2 program toggle + buffer size, settings screen ---'
Emit '@file shaders/shaders.properties'
Emit 'before "# Miscellaneous"'
EmitBody $prToggles
Emit 'after "        size.buffer.colortex7 = REFLECTION_RES REFLECTION_RES"'
EmitBody $prSize
Emit 'replace "VANILLAAO_I PLAYER_SHADOW"'
EmitBody @('VANILLAAO_I PLAYER_SHADOW [IRLIGHTS]')
Emit 'after "        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE"'
EmitBody $propsScreens
Emit 'after "    uniform.float.endFlashIntensityM=if(endFlashFactor0 > endFlashFactor1, sqrt(endFlashFactor0), endFlashFactor1)"'
EmitBody $prReplay
Emit ''
Emit '# --- replay tag attachment: every gbuffers program also writes (tag, depth) into colortex9 ---'
$gbFile = ''
foreach ($op in $gbOps) {
    if ($op[0] -ne $gbFile) { $gbFile = $op[0]; Emit "@file shaders/program/$gbFile" }
    Emit ('replace "' + $op[1] + '"')
    EmitBody $op[2]
}
Emit ''
Emit '# --- option labels + tooltips ---'
Emit '@file shaders/lang/en_US.lang'
Emit 'after "option.XLIGHT_CURVE.comment=Adjusts how quickly the intensity of blocklight fades away as it travels distance away from the light source."'
EmitBody $langTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
