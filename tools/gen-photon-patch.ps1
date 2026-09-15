# Generates patches/photon.irlights from Shadres/Modification/Photon.
# Photon is a DEFERRED pack (by SixthSurge): the surface half hooks
# program/d4_deferred_shading.fsh (PROGRAM_DEFERRED4) and the volumetric half
# hooks the native quarter-res program/c0_vl.fsh (PROGRAM_COMPOSITE0). Programs
# are #version 400 compatibility with SEPARATE .fsh/.vsh, and thin world{0,1,-1}
# wrappers carry the SSBO + 420pack #extension (SSBO + the binding=7 UBO are not
# core at 400). Unlike the forward packs there is no added deferred2 pass and no
# colortex10 upsample; VL is folded into fog_scattering.
#
# The +file lib body and the two lang blocks are spliced VERBATIM out of
# Modification; the fixed inject blocks (d4/c0_vl/gbuffers hooks, properties,
# wrappers) are literals here. Validate: the javac harness applies this patch to
# the pristine pack and `git ... diff --no-index --ignore-cr-at-eol <out> <Mod>`
# is empty.

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-irlights-addon"
$mod  = "$repo\Shadres\Modification\Photon\shaders"
$orig = "$repo\Shadres\Original\Photon\shaders"
$out  = "$repo\patches\photon.irlights"

function Lines($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") -split "`n" }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }

# Extract the single contiguous insertion of `mod` over `orig`; returns a NON-BLANK
# anchor (leading blanks between the anchor and the diverging line fall INTO the
# block, matching the hand-authored patch convention) and the inserted block.
function ExtractInsertion($orig, $mod) {
    $i = 0
    while ($i -lt $orig.Count -and $i -lt $mod.Count -and $orig[$i] -ceq $mod[$i]) { $i++ }
    if ($i -eq 0) { throw "insertion starts at line 0 (no common prefix)" }
    # back up over blank common lines so the anchor is a specific, unique line
    $a = $i
    while ($a -gt 0 -and $orig[$a - 1] -eq '') { $a-- }
    if ($a -eq 0) { throw "no non-blank anchor before the insertion" }
    $blockLen = $mod.Count - $orig.Count
    if ($blockLen -le 0) { throw "no net insertion" }
    $block = $mod[$a..($a + $blockLen - 1)]
    # verify pure insertion: orig[a..] == mod[a+blockLen..]
    $oTail = if ($a -lt $orig.Count) { $orig[$a..($orig.Count - 1)] } else { @() }
    $mTail = if (($a + $blockLen) -lt $mod.Count) { $mod[($a + $blockLen)..($mod.Count - 1)] } else { @() }
    if (($oTail -join "`n") -cne ($mTail -join "`n")) { throw "not a single clean insertion (tail mismatch)" }
    return @{ Anchor = $orig[$a - 1]; Block = $block }
}

# ---- verbatim +file / lang bodies from Modification ----
$libText = FileText "$mod\include\irlite\irlite_lights.glsl"
$enIns = ExtractInsertion (Lines "$orig\lang\en_US.lang") (Lines "$mod\lang\en_US.lang")
$ruIns = ExtractInsertion (Lines "$orig\lang\ru_RU.lang") (Lines "$mod\lang\ru_RU.lang")

# No sliders op any more: wave 0 moved every IRLITE_VL_* slider into the mod's
# globals UBO, wave 1 every IRLITE_OUTLINE_* one and wave 2 the last four
# (intensity, specular intensity, toon bands/smoothing). Tripwire: an IRLITE
# slider added to Modification would otherwise be silently missing from the patch.
$slLine = Lines "$mod\shaders.properties" | Where-Object { $_ -match '^\s*sliders\s*=' }
if (@($slLine).Count -ne 1) { throw "sliders line not unique" }
if ($slLine -match 'IRLITE_') { throw "sliders line carries an IRLITE option but the patch has no sliders op" }

# ---- assemble ----
$sb = New-Object System.Text.StringBuilder
function Emit($s) { [void]$sb.Append($s).Append("`n") }
function EmitBody($lines) { Emit '<<<'; foreach ($l in $lines) { Emit $l }; Emit '>>>' }
function EmitBodyText($text) {
    Emit '<<<'
    if ($text.EndsWith("`n")) { [void]$sb.Append($text); Emit '' } else { [void]$sb.Append($text).Append("`n") }
    Emit '>>>'
}
function EmitFile($relPath, $text) { Emit "+file $relPath"; EmitBodyText $text }

Emit '# IRLite point + spot lights for Photon (by SixthSurge).'
Emit '@name    Photon lights'
Emit '@target  Photon'
Emit '@irlite  1'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + volumetric halves) ---'
EmitFile 'shaders/include/irlite/irlite_lights.glsl' $libText
Emit ''

Emit '# --- diffuse + specular + outline in the main deferred opaque shading pass ---'
Emit '@file shaders/program/d4_deferred_shading.fsh'
Emit ''
Emit 'after "#include \"/include/utility/space_conversion.glsl\""'
EmitBody @('', '// IRLite SSBO.', '#include "/include/irlite/irlite_lights.glsl"')
Emit ''
Emit 'after "uint material_mask = uint(255.0 * data[1].y);"'
EmitBody @(
    "        // IRLite: bit 7 = entity flag; cleared for Photon's material-id compares.",
    '        bool irlite_nonTerrain = (material_mask & 128u) != 0u;',
    '        material_mask &= 127u;')
Emit ''
Emit 'before "        // Specular highlight"'
EmitBody @(
    '        // IRLite diffuse + specular + outline in one pass; the enables and both',
    '        // intensities are live UBO values (see IRLITE_SURFACE_OK), nothing is compile-gated.',
    '        vec3 irlite_diffuse;',
    '        vec3 irlite_specular;',
    '        vec3 irlite_outline;',
    '        irlite_lightSurface(',
    '            position_scene,',
    '            normal,',
    '            -direction_world,',
    '            material,',
    '            irlite_nonTerrain,',
    '            irlite_diffuse,',
    '            irlite_specular,',
    '            irlite_outline',
    '        );',
    '        fragment_color += IRLITE_INTENSITY_LIVE * irlite_diffuse * material.albedo;',
    '        fragment_color += (IRLITE_INTENSITY_LIVE * IRLITE_SPECULAR_INTENSITY_LIVE) * irlite_specular;',
    '        // outline is runtime-gated inside irlite_lightSurface (UBO bit8); adds 0 when off',
    '        fragment_color += IRLITE_INTENSITY_LIVE * irlite_outline * material.albedo;',
    '')
Emit ''

Emit '# --- per-light volumetrics in the quarter-res VL pass ---'
Emit '@file shaders/program/c0_vl.fsh'
Emit ''
Emit 'after "#include \"/include/utility/space_conversion.glsl\""'
EmitBody @(
    '// IRLite per-light volumetrics (Iris include lines take NO trailing //).',
    '#include "/include/utility/color.glsl"',
    '#include "/include/irlite/irlite_lights.glsl"')
Emit ''
Emit 'after "    fog_scattering +=\n        get_lpv_fog_scattering(world_start_pos, world_end_pos, dither);\n#endif"'
EmitBody @(
    '',
    '#ifdef IRLITE_VOLUMETRIC',
    '    // IRLite VL: add inscatter to fog_scattering.',
    '    vec3 irlite_worldDir = normalize(world_end_pos - world_start_pos);',
    '    fog_scattering +=',
    '        irlite_volumetric(gbufferModelViewInverse[3].xyz, scene_pos, irlite_worldDir, dither);',
    '#endif')
Emit ''

Emit '# --- flag entity / block-entity / hand fragments as non-terrain (bit 7 of the'
Emit '#     material id, masked back to the clean id in d4) for "entities only" lights ---'
Emit '@file shaders/program/gbuffers_all_solid.fsh'
Emit ''
Emit 'before "    gbuffer_data_0.x = pack_unorm_2x8(base_color.rg);"'
EmitBody @(
    '    // IRLite: flag entity/block/hand with bit 7 for "entities only" lights.',
    '    uint irlite_material_mask = material_mask;',
    '#if defined PROGRAM_GBUFFERS_ENTITIES || defined PROGRAM_GBUFFERS_BLOCK || \',
    '    defined PROGRAM_GBUFFERS_HAND',
    '    irlite_material_mask |= 128u;',
    '#endif',
    '')
Emit ''
Emit 'replace "clamp01(float(material_mask) * rcp(255.0))"'
EmitBody @('clamp01(float(irlite_material_mask) * rcp(255.0))')
Emit ''

Emit '# --- Iris: enable the SSBO + register the flat IRLights settings screen ---'
Emit '@file shaders/shaders.properties'
Emit ''
Emit 'replace "[post] [misc]"'
EmitBody @('[post] [misc] [IRLIGHTS]')
Emit ''
Emit 'after "screen.box          = BOX_MODE BOX_LINE_WIDTH BOX_COLOR_R BOX_COLOR_G BOX_COLOR_B BOX_EMISSION"'
EmitBody @(
    '',
    '# IRLights (point/spot lights addon)',
    'screen.IRLIGHTS            = <empty> <empty> IRLITE_VOLUMETRIC <empty> <empty> <empty> IRLITE_SHADOWS')
Emit ''
Emit 'replace "iris.features.optional = CUSTOM_IMAGES ENTITY_TRANSLUCENT"'
EmitBody @('iris.features.optional = CUSTOM_IMAGES ENTITY_TRANSLUCENT SSBO')
Emit ''

Emit '# --- Iris: option labels (en_US) ---'
Emit '@file shaders/lang/en_US.lang'
Emit ''
Emit "after `"$($enIns.Anchor)`""
EmitBody $enIns.Block
Emit ''

Emit '# --- Iris: option labels (ru_RU) ---'
Emit '@file shaders/lang/ru_RU.lang'
Emit ''
Emit "after `"$($ruIns.Anchor)`""
EmitBody $ruIns.Block
Emit ''

Emit '# --- SSBO + 420pack #extension on every wrapper that reads the binding-7'
Emit '#     buffer/UBO: the d4 surface pass + the c0_vl volumetric pass, in all three'
Emit '#     dimensions (Photon programs are #version 400, where neither is core). ---'
foreach ($w in @('world0/deferred4.fsh', 'world1/deferred4.fsh', 'world-1/deferred4.fsh',
                 'world0/composite.fsh', 'world1/composite.fsh', 'world-1/composite.fsh')) {
    Emit "@file shaders/$w"
    Emit 'after "#version 400 compatibility"'
    EmitBody @('#extension GL_ARB_shader_storage_buffer_object : enable',
               '#extension GL_ARB_shading_language_420pack : enable')
    Emit ''
}

# trim the trailing blank the loop leaves
$text = $sb.ToString()
$text = $text.TrimEnd("`n") + "`n"
[IO.File]::WriteAllText($out, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($text -split "`n").Count)
