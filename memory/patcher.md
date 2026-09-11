---
name: patcher
description: "IRLite shader patcher — anchor-based .irlights patch DSL (ext renamed from .irlpatch 2026-06-12), PatchEngine/applier semantics (EOL-tolerant anchors, zip sources, validate-first, marker), library/shaderpack discovery, settings UI, edit workflow. Движок в irl-core, потребляется обоими модами."
metadata:
  node_type: memory
  mod_scope: irl-core-shared
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
  modified: 2026-07-24T10:34:53.089Z
---

IRLite shader patcher (injects IRLite GLSL into an Iris shaderpack). Parent: [[MEMORY]]. Consumes contracts from [[addon-light-buffer-ssbo]] (SSBO binding 7) + [[addon-iris-integration]] (sampler names). UI hooked via [[addon-ui-config]] (irlite_patcher category). Code: src/client/java/qualet/irlite/client/patcher/ (IrlPatch, IrlPatchParser, PatchEngine, IrlPatchApplier, PatchResult + PatchLibrary/Shaderpacks) + ui/patcher/UIPatcherSection.

DESIGN DECISION: anchor-based edits (chosen over line-diff). Edits attach to unique literal substrings of the
shader source, NOT line numbers, so a patch survives unrelated pack changes. This is the implemented format.

REWORK 2026-06-12 (improvement round): added PatchEngine (in-memory validate-first core, ALL errors aggregated),
EOL-tolerant anchor matching + EOL-adaptive insertion, .zip sources, already-patched marker, +file conflict
detection, hard-fail output deletion, Validate UI button + patch metadata/mismatch UI, optional ops (after?),
anchor alternatives ("a" | "b"), @packversion/@irlite directives + CONTRACT_VERSION.

REBRAND 2026-06-12: patch file extension .irlpatch -> .irlights (PatchLibrary.EXTENSION; all 6 patches/* and
gen-*.ps1 outputs renamed, run\irlite\patches too), patched-pack suffix _IRLite -> _IRLights (UIPatcherSection
outputName), marker/tooltip texts say "IRLights". DSL, @-directives (incl. @irlite, @marker default IRLITE) and
IrlPatch* class names UNCHANGED. Older .irlpatch files are simply not listed by PatchLibrary anymore — rename to use.

.irlights patch DSL (IrlPatchParser)
- Header: @name <text>, @target <packName> (matched against pack names in the UI), @packversion <text>
  (informational: pack version the patch was authored against), @irlite <N> (GLSL contract version the patch
  needs; applier refuses on mismatch with IrlPatch.CONTRACT_VERSION, currently 1; 0/absent = no check),
  @marker <id> (default IRLITE, recorded in the output marker file).
- Comments: lines starting with # (only OUTSIDE a body). Blank lines ignored outside bodies.
- Body block: raw text between a line that is exactly `<<<` and a line that is exactly `>>>` (verbatim).
- Operations:
    +file <relpath>            <<<...>>>   create a NEW file (body = whole content); ERROR if the file already exists in the pack.
    @file <relpath>                         set the current target file for subsequent edits.
    after  "anchor"            <<<...>>>   insert body right AFTER the anchor.
    before "anchor"            <<<...>>>   insert body right BEFORE the anchor.
    replace "anchor"           <<<...>>>   replace the anchor literal with body.
- OPTIONAL ops: after?/before?/replace? — skipped (logged, counted) instead of failing when no anchor
  matches. Ambiguous anchor is still an error even for optional ops (authoring bug).
- ANCHOR ALTERNATIVES: after "a" | "b" | "c" — tried left to right, first anchor with exactly one match wins;
  combines with optional. For pack-version drift.
- Anchors are double-quoted literals with escapes \n \t \" \\; EMPTY anchor = parse error. Anchors may contain
  | inside quotes (the separator is parsed outside quotes only). Paths normalized (backslashes->/, leading / stripped).
- An op before any @file is a parse error; a patch with zero ops is a parse error.

Engine semantics (PatchEngine.run(root, patch) — the shared core of validate AND apply)
- Plays ALL ops in memory against the pack root sequentially (later ops see earlier edits, incl. anchors into
  +file-created files). Nothing is written by the engine itself.
- Records EVERY failed op instead of stopping at the first -> porting to a new pack version reports all broken
  anchors in one run. PatchResult summary = "N errors, first: <e1>", rest as ERROR log lines.
- Refuses immediately if <root>/irlite_patched.txt exists ("pack is already patched").
- Anchor ops: target file must exist; anchor must match EXACTLY ONCE (0 -> not found / optional skip; >1 -> ambiguous).
- EOL TOLERANCE (lifted the old "single-line anchors only" rule): \n in an anchor matches \r\n, \n or lone
  \r in the file — multi-line anchors now work on ANY checkout style. Insertion: bodies (parser-normalized LF)
  are converted to the target file's DOMINANT line ending (crlf>lf count); untouched bytes preserved exactly.
  Old applier inserted raw LF into CRLF files (mixed-EOL output); new output is uniform per-file. Generators
  still emit single-line anchors — harmless, keep for byte-stability.
- +file bodies are ALWAYS written LF; body's last line blank => trailing \n (readBody adds none otherwise) — unchanged.

Applier (IrlPatchApplier)
- validate(sourcePack, patch): engine dry-run only, writes NOTHING. Summary "Patch fits <pack>: N ops[, M skipped]".
- apply(sourcePack, outputPack, patch): contract check -> engine run on the SOURCE -> on any error: report all,
  existing output is NOT touched -> else delete existing output (deleteRecursive now FAILS LOUDLY listing
  undeletable/locked files instead of silently leaving a dirty tree) -> copy source -> write dirty files ->
  stamp irlite_patched.txt (marker id, patch name, target+packversion, date, how-to note). Rollback on IO error.
- ZIP SOURCES: source may be a .zip — opened via zip FileSystem; pack root = wherever shaders/ lives (zip root,
  or exactly one top-level folder). Output is always a folder. Zips with BACKSLASH entry names (PowerShell 5.1
  Compress-Archive artifact) are unreadable -> "no shaders/ folder found inside zip" (Iris can't read those
  either; real-world pack zips are forward-slash). Test zips with `jar -cMf out.zip -C <dir> .`.
- The 4+1 core classes (IrlPatch, IrlPatchParser, PatchEngine, IrlPatchApplier, PatchResult) stay pure
  java.nio/util — no MC/Iris/fabric deps.

VALIDATE A PATCH WITHOUT MINECRAFT (tools/PatchHarness.java)
- javac the 5 core classes + PatchHarness; `java PatchHarness <patch> <srcPack> <outPack>` applies,
  `java PatchHarness -validate <patch> <srcPack>` dry-runs.
- Full check: apply to pristine Shadres/Original/<pack>, DELETE irlite_patched.txt from the output (it's new vs
  the hand-edited tree), then `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol <out> Shadres/Modification/<pack>`
  must be empty. All 6 patches re-validated diff-clean after the 2026-06-12 rework (incl. from-zip Photon, both
  zip layouts).

Discovery / IO
- PatchLibrary: patches live in <gameDir>/irlite/patches/*.irlpatch. list() sorted; openFolder().
- Shaderpacks: dir via Iris.getShaderpacksDirectory() (fallback <gameDir>/shaderpacks); list() via Iris enumerate() with a direct dir-scan fallback (folders + .zip). packPath(name).
- The repo's canonical patches: patches/{iterationrp,photon,complementaryreimagined,bsl,solas,bliss}.irlpatch —
  all carry @irlite 1 since 2026-06-12 (bsl also @packversion v10, bliss @packversion V2.1.2); generators
  tools/gen-*-patch.ps1 emit the headers (photon has NO generator — hand-edit). Op counts: iterationrp 11,
  photon 20, complementaryreimagined 21, bsl 26, solas 19, bliss 16. run/irlite/patches refreshed with all 6.

Settings UI (UIPatcherSection.append, "irlite_patcher" settings category)
- Shaderpack list + Patch list (selection persists), refresh + open-folder icons.
- Patch META label under the patch list: "<name> -> <target> [<packversion>] (N ops)"; broken patch = red parse
  error; when the selected pack doesn't match @target (normalized lowercase-alphanumeric substring, .zip
  stripped) -> amber " - does not match the selected pack!". Selecting a patch with NO pack selected auto-selects
  the single matching pack.
- Toggle "Create new pack each time"; UI.row(Validate, Patch) buttons: Validate = dry-run (nothing written),
  Patch = apply. runPatch accepts FOLDER or ZIP source. Output name = "<pack minus .zip>_IRLite" (or _2, _3... if createNew). Full log -> "irlite" logger;
  one-line colored status (green/red).
- In-game VERIFIED 2026-07-23 (runClient 1.20.4, user). CUT-OFF FIX (irlite master commit 2e1b7b3): status + meta
  lines now UIText (word-wrap onto multiple lines + self-size height via getParentContainer().resize()), NOT UILabel.
  Root cause: UILabel.render() calls font.limitToWidth -> truncates to ONE line with "..." — in the narrow BBS
  settings panel this clipped long failed-patch error messages AND the target-pack name in the meta line
  ("...for a different shaderpack (BSL)."), so the user saw neither the cause nor which pack to pick = soft-lock.
  Editor PatcherPanel is NOT affected (already wraps via Widgets.textColoredWrapped). Addon-only, irl-core untouched.
  General rule: BBS UILabel = single-line truncating; UIText = multi-line word-wrap (font.wrap) + auto-height.

CURRENT IterationRP PATCH (patches/iterationrp.irlights) — take-3 NEW-GEN since 2026-06-12; RETARGETED 2026-07-24 to iterationRP **0.8.26** (was 0.8.24). 11 ops (was 12): two upstream drifts fixed — (a) sliders anchor `sliders=PT_VOXEL_RESOLUTION ` → `sliders=PT_VOXEL_RESOLUTION_X PT_VOXEL_RESOLUTION_Y ` (pack split voxel res into X/Y); (b) DROPPED the `iris.features.required=CUSTOM_IMAGES`→`+SSBO` replace-op entirely — 0.8.26 already declares `CUSTOM_IMAGES SSBO`, so the old op produced a `SSBO SSBO` duplicate. Also FSR2 tree moved `Lib/FSR2/`→`Lib/FidelityFX/FSR2/` (not a hook). GLSL contract intact (GbufferData/Material/SpecularGGX 4-arg+f0/VolumetricFog 7-arg all unchanged). ALL anchors single-line; before-op anchors carry their leading tabs as \t escapes (byte-exact applier output); generator = tools/gen-iterationrp-patch.ps1 (body-splice from Shadres/Modification/IterationRP + boundary asserts), validated via tools/PatchHarness.java applier-vs-Modification diff-clean. NO OUTLINE (the outline GLSL block breaks IterationRP by mere presence — see [[shader-iterationrp-pipeline]]). Contents:
- +file shaders/Lib/irlite_lights.glsl : SSBO struct (binding 7), option #defines, merged diffuse+specular surface loop (IRLITE_SURFACE_PASS), gather-PCF spot atlas + point cube-array PCSS shadows, VL march with per-step strided shadows (IRLITE_VL_PASS).
- @file shaders/Lib/Programs/Composite/Soild_FS.glsl : after include-anchor (+IRLITE_SURFACE_PASS), before/after the albedo+sunlightSpecular line (diffuse pre-albedo w/ bit-7 decode + bobbing-corrected positions, spec post-albedo).
- @file shaders/Lib/Programs/Composite/Volumetric_FS.glsl : after include-anchor (+IRLITE_VL_PASS), after the VolumetricFog call (bobbed-eye-origin VL add).
- @file shaders/Lib/Programs/Gbuffers/Entities_FS.glsl : replace (+128 materialID bit-7 flag).
- @file shaders/shaders.properties : replace x1 ([IRLIGHTS] main-screen entry; SSBO-feature replace dropped for 0.8.26) + after x2 (screen tree; sliders).
- @file shaders/lang/en_us.lang : after (colour-coded labels + tooltips).
Settings screen path in-game: Iris -> Shader Pack Settings -> [IRLights] on the main page, or Lighting -> IRLights.

EDIT WORKFLOW (project process)
1. Hand-edit the working copy under Shadres/Modification/<pack>/shaders until the change works (pristine baseline = Shadres/Original/<pack>/shaders; see [[sync-workflow]]).
2. For each edit, capture a UNIQUE literal substring of the original surrounding code as the anchor, and write an after/before/replace op.
3. New files become +file ops.
4. Keep anchors short but unique (applier rejects 0 or >1 matches); prefer anchoring to stable upstream code, not to IRLite's own injected text. Multi-line anchors are now safe (EOL-tolerant), but single-line stays the generator default.
5. For anchors expected to drift across pack versions, consider alternatives ("new" | "old") or optional ops (after?) instead of hard failure.

Связь: irl-core-shared. Авторитетный IRLite-референс СЕМАНТИКИ патчера (грамматика DSL, EOL, zip, validate-first); движок в irl-core (org.qualet.irl.patcher), потребляется обоими модами. UIPatcherSection + категория irlite_patcher = IRLite-only; содержимое .irlights + Shadres + gen-*.ps1 = IRLite-владелец (синк в redactor через copy-patches.ps1). См. [[project-irlite-base-ported]], [[project-irl-sync-strategy]], [[reference-edit-routing-by-area]]. Источник: память IRLite.
