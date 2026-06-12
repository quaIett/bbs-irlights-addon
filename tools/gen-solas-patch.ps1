# Generates patches/solas.irlpatch by splicing the IRLite bodies VERBATIM out
# of Shadres/Modification/Solas (so the patch reproduces the working tree
# byte-for-byte). Anchors are unique literals captured from the PRISTINE pack.
# Solas is a CRLF pack -> SINGLE-LINE anchors only (see memory patcher.md).
# Validate after generating: javac the 4 pure patcher classes + PatchHarness,
# apply to the pristine pack, then
#   git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol <out> <Mod>
# must be empty. See memory solas-port-plan Phase 5.

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\IRLite"
$mod  = "$repo\Shadres\Modification\Solas\shaders"
$out  = "$repo\patches\solas.irlpatch"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function IndexOfLine($lines, $text) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found: $text"
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

# ---- extract bodies from Modification ----
$libText = FileText "$mod\lib\irlite\irlite_lights.glsl"
$d2Text  = FileText "$mod\programs\deferred2.glsl"
$wrapTexts = @{}
foreach ($w in @("deferred2.fsh","deferred2.vsh","world1\deferred2.fsh","world1\deferred2.vsh","world-1\deferred2.fsh","world-1\deferred2.vsh")) {
    $wrapTexts[$w] = FileText "$mod\$w"
}

# gbuffersLighting: the compute+add block between the finalColor line and the
# pow-encode line (leading blank included; the blank before the encode line is
# the pristine remainder).
$gl = Lines "$mod\lib\lighting\gbuffersLighting.glsl"
$F = IndexOfLine $gl '    vec3 finalColor = diffuseAlbedo + specularHighlight * vanillaDiffuse;'
$A = IndexOfLine $gl '    albedo.rgb = pow(max(finalColor, vec3(0.0)), vec3(1.0 / 2.2));'
if ($gl[$A - 1] -ne '') { throw "expected blank before the pow-encode line" }
$glBlock = $gl[($F + 1)..($A - 2)]
if ($glBlock[0] -ne '') { throw "expected leading blank in glBlock" }
if ($glBlock[-1] -ne '    #endif') { throw "glBlock tail unexpected" }

# composite1: include block (before // Global Variables //) + the ink+VL body
# (before the DRAWBUFFERS line).
$c1 = Lines "$mod\programs\composite1.glsl"
$G = IndexOfLine $c1 '// Global Variables //'
$CS = IndexOfLineStarting $c1 '// IRLite: included BELOW'
$c1Inc = $c1[$CS..($G - 1)]
if ($c1Inc[-1] -ne '') { throw "expected trailing blank in c1Inc" }
if ($c1Inc[-2] -ne '#include "/lib/irlite/irlite_lights.glsl"') { throw "c1Inc include line unexpected" }
$D = IndexOfLine $c1 "`t/* DRAWBUFFERS:0 */"
$IS = IndexOfLine $c1 "`t#if defined IRLITE_ACTIVE && defined IRLITE_OUTLINE"
$c1Body = $c1[$IS..($D - 1)]
if ($c1Body[-1] -ne '') { throw "expected trailing blank in c1Body" }

# final.glsl: nothing spliced (single static line), asserted against Modification.
$fg = Lines "$mod\programs\final.glsl"
[void](IndexOfLine $fg 'const int colortex10Format = RGB16F; //IRLite reduced-res volumetrics')

# shaders.properties: toggles+size.buffer block, screen tail+screens, sliders tail.
$pr = Lines "$mod\shaders.properties"
$T = IndexOfLine $pr 'program.world0/deferred2.enabled=IRLITE_VOLUMETRIC'
$prToggles = $pr[$T..($T + 5)]
if ($prToggles[2] -ne 'program.world1/deferred2.enabled=IRLITE_VOLUMETRIC') { throw "toggle block unexpected" }
if ($prToggles[3] -ne '') { throw "expected blank inside the toggles body" }
if (-not $prToggles[4].StartsWith('#IRLite reduced-res')) { throw "expected the size.buffer comment" }
if ($prToggles[5] -ne 'size.buffer.colortex10=IRLITE_VL_RESOLUTION IRLITE_VL_RESOLUTION') { throw "size.buffer line unexpected" }
$SL = IndexOfLineStarting $pr 'screen.LIGHTING='
$tIdx = $pr[$SL].IndexOf('VANILLA_AO SSAO AO_STRENGTH')
if ($tIdx -lt 0) { throw "screen.LIGHTING tail anchor not found" }
$screenTail = $pr[$SL].Substring($tIdx)
if (-not $screenTail.EndsWith('[IRLITE_SETTINGS]')) { throw "screen.LIGHTING tail unexpected" }
$screens = $pr[($SL + 1)..($SL + 6)]
if (-not $screens[0].StartsWith('screen.IRLITE_SETTINGS=')) { throw "screens block head unexpected" }
if (-not $screens[5].StartsWith('screen.IRLITE_OUTLINE_SETTINGS=')) { throw "screens block tail unexpected" }
$slLine = $pr | Where-Object { $_.StartsWith('sliders=') }
if (@($slLine).Count -ne 1) { throw "sliders line not unique" }
$slIdx = $slLine.IndexOf('WAVING_AMPLITUDE FIREFLIES_BRIGHTNESS')
if ($slIdx -lt 0) { throw "sliders tail anchor not found" }
$slBody = $slLine.Substring($slIdx)
if (-not $slBody.EndsWith('IRLITE_OUTLINE_DEPTH_THRESHOLD')) { throw "sliders body tail unexpected" }

# lang: everything after the pack's true last line, both locales. The ru anchor
# line is taken from the file itself (no cyrillic literals in this script).
$lgEn = Lines "$mod\lang\en_US.lang"
$Yen = IndexOfLine $lgEn 'option.FIREFLIES_BRIGHTNESS=Fireflies Brightness'
$enTail = $lgEn[($Yen + 1)..($lgEn.Count - 1)]
while ($enTail[-1] -eq '') { $enTail = $enTail[0..($enTail.Count - 2)] }
if ($enTail[0] -ne '' -or $enTail[1] -ne '') { throw "expected 2 leading blanks in enTail" }
$lgRu = Lines "$mod\lang\ru_RU.lang"
$Yru = IndexOfLineStarting $lgRu 'option.FIREFLIES_BRIGHTNESS='
$ruAnchor = $lgRu[$Yru]
$ruTail = $lgRu[($Yru + 1)..($lgRu.Count - 1)]
while ($ruTail[-1] -eq '') { $ruTail = $ruTail[0..($ruTail.Count - 2)] }
if ($ruTail[0] -ne '' -or $ruTail[1] -ne '') { throw "expected 2 leading blanks in ruTail" }

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

Emit '# IRLite — point + spot lights for Solas (by Septonious), ported from the BSL'
Emit '# inject: diffuse + specular + real-time shadows + volumetrics + toon + rim'
Emit '# outline, plus an in-shader settings screen (Iris: Shader Pack Settings ->'
Emit '# Lighting -> IRLite Lights; English AND Russian labels).'
Emit '#'
Emit '# Solas is FORWARD-shaded: diffuse+specular hook gbuffersLighting() inside'
Emit '# its linear window (the function pow-2.2s albedo, mixes lights, re-encodes)'
Emit '# — one hook covers all 11 includer programs, and the entity flag is'
Emit '# compile-time via the pack''s own line-1 program self-defines. The'
Emit '# volumetric march runs in an ADDED reduced-resolution pass (deferred2 ->'
Emit '# colortex10, sized by the IRLITE_VL_RESOLUTION option via size.buffer);'
Emit '# composite1 bilinear-upsamples it and hosts the rim outline (subject mask ='
Emit '# colortex3 alpha 1.0 with blue < 0.1 — entities + block entities, water'
Emit '# excluded). The pack is #version 130 and the inject carries its own in-file'
Emit '# #extension lines; iris.features gets SSBO appended (the flag list is'
Emit '# parsed from the RAW properties — one line is enough for every branch).'
Emit '@name    Solas lights'
Emit '@target  Solas'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + outline + volumetric) ---'
EmitFile 'shaders/lib/irlite/irlite_lights.glsl' $libText
Emit ''
Emit '# --- the added reduced-resolution volumetric pass ---'
EmitFile 'shaders/programs/deferred2.glsl' $d2Text
EmitFile 'shaders/deferred2.fsh' $wrapTexts['deferred2.fsh']
EmitFile 'shaders/deferred2.vsh' $wrapTexts['deferred2.vsh']
EmitFile 'shaders/world1/deferred2.fsh' $wrapTexts['world1\deferred2.fsh']
EmitFile 'shaders/world1/deferred2.vsh' $wrapTexts['world1\deferred2.vsh']
EmitFile 'shaders/world-1/deferred2.fsh' $wrapTexts['world-1\deferred2.fsh']
EmitFile 'shaders/world-1/deferred2.vsh' $wrapTexts['world-1\deferred2.vsh']
Emit ''
Emit '# --- forward diffuse + specular inside gbuffersLighting''s linear window ---'
Emit '@file shaders/lib/lighting/gbuffersLighting.glsl'
Emit 'before "void gbuffersLighting("'
EmitBody @('#define IRLITE_SURFACE_PASS', '#include "/lib/irlite/irlite_lights.glsl"', '')
Emit 'after "    vec3 finalColor = diffuseAlbedo + specularHighlight * vanillaDiffuse;"'
EmitBody $glBlock
Emit ''
Emit '# --- rim outline ink + volumetric upsample (composite1, linear colour) ---'
Emit '@file shaders/programs/composite1.glsl'
Emit 'before "// Global Variables //"'
EmitBody $c1Inc
Emit 'before "\t/* DRAWBUFFERS:0 */"'
EmitBody $c1Body
Emit ''
Emit '# --- the reduced-res VL buffer format (inside the pack''s format comment block) ---'
Emit '@file shaders/programs/final.glsl'
Emit 'after "const int colortex7Format = RGBA16; //Voxy transparent color"'
EmitBody @('const int colortex10Format = RGB16F; //IRLite reduced-res volumetrics')
Emit ''
Emit '# --- SSBO feature flag, deferred2 toggles + buffer size, screens + sliders ---'
Emit '@file shaders/shaders.properties'
Emit 'replace "iris.features.optional=CUSTOM_IMAGES"'
EmitBody @('iris.features.optional=CUSTOM_IMAGES SSBO')
Emit 'after "program.world1/shadowcomp.enabled=VX_SUPPORT"'
EmitBody $prToggles
Emit 'replace "VANILLA_AO SSAO AO_STRENGTH"'
EmitBody (@($screenTail) + $screens)
Emit 'replace "WAVING_AMPLITUDE FIREFLIES_BRIGHTNESS"'
EmitBody @($slBody)
Emit ''
Emit '# --- option labels + tooltips (English) ---'
Emit '@file shaders/lang/en_US.lang'
Emit 'after "option.FIREFLIES_BRIGHTNESS=Fireflies Brightness"'
EmitBody $enTail
Emit ''
Emit '# --- option labels + tooltips (Russian — the pack ships ru_RU) ---'
Emit '@file shaders/lang/ru_RU.lang'
Emit ('after "' + $ruAnchor + '"')
EmitBody $ruTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
