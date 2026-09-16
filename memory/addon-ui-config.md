---
name: addon-ui-config
description: "IRLite UI and config — in-world light guides, form editor panels, BBS settings categories (irlite + irlite_patcher), IrliteConfig getters, L10n strings."
metadata:
  node_type: memory
  mod_scope: IRLite-only
  ported_from: IRLite
  consolidated: 2026-06-18
  type: reference
  originSessionId: 7caee3ab-d073-4ed1-bfcf-8daae6a18007
---

IRLite UI + configuration. Parent: [[addon-architecture]]. Forms: [[addon-forms]]. Patcher UI (UIPatcherSection) is Phase 2.

CONFIG (IrliteConfig, static BBS Value* refs, null-safe getters)
- showGuides() -> ValueBoolean show_guides (default false): draw in-world wireframe gizmos for placed lights.
- shadowQuality() -> ValueInt shadow_quality (default 1=MEDIUM, range 0..3 -> LOW/MEDIUM/HIGH/ULTRA). Drives [[addon-shadows]] presets.
- shadowCache() -> ValueBoolean shadow_cache (default true): only re-bake shadows when scene changes (per-light dirty cache).
- shadowBlocks() -> ValueBoolean shadow_blocks (default true): world blocks cast shadows by real shape; cutout blocks skip transparent texels. See [[addon-shadows]].
- shadowBlockRadius() -> ValueInt shadow_block_radius (default 24, range 4..96): how far (blocks) block occluders are collected around a light; blocks beyond it cast no shadow even if range is larger (find C14, made configurable 2026-06-09 commit a29794b). Read every frame in ShadowBaker.collectBlocks via min(range, radius); BlockShadowCache hashes the radius so a change re-collects all lamps next frame.

SETTINGS REGISTRATION (BBSSettingsMixin -> BBSSettings.register TAIL)
- builder.category("irlite", Icons.LIGHT): registers show_guides, shadow_quality (with IKey modes LOW/MEDIUM/HIGH/ULTRA), shadow_cache, shadow_blocks, shadow_block_radius (getInt(24,4,96), plain numeric — no .modes()) and stores the Value* into IrliteConfig.
- builder.category("irlite_patcher", Icons.WRENCH): EMPTY category; its body is injected at runtime by UISettingsOverlayPanelMixin (Phase 2 patcher UI).

L10n (L10nMixin -> L10n.<init> TAIL) registers English keys: bbs.config.irlite.title/tooltip, bbs.config.irlite_patcher.title/tooltip,
and per-setting label+comment for show_guides / shadow_quality / shadow_cache / shadow_blocks / shadow_block_radius.

IN-WORLD GUIDES (client/forms/)
- AbstractLightFormRenderer<T> extends BBS FormRenderer. render3D decides per context:
    - skip entirely if ShadowBakeState.isBaking() (avoid re-registering during shadow bake).
    - world path (MODEL_BLOCK or ENTITY, not picking/ui/iris-shadow-pass): register light unless scanner owns it (see [[addon-light-collection]]), then draw guide if showGuides().
    - editor preview / model-renderer / ui: draw guide only.
    - picking: draw a colored pick box (0.5^3) via BBS picker program.
  renderInUI: draws the form's icon tinted by light color (Icons.LIGHT for point, Icons.FRUSTUM for spot) in the form list.
- PointLightFormRenderer: icon LIGHT; guide = LightGuideRenderer.renderPointLight(radius); registerLight via IRLightPositionResolver + registerPoint.
- SpotlightFormRenderer: icon FRUSTUM; guide = renderSpotlight(range, outerAngle, innerAngle); registerLight computes dir (local +Z through inverseViewRot*stack) + cone cosines + registerSpot.
- LightGuideRenderer (package-private): POSITION_COLOR triangles, additive-ish wireframe (blend on, cull off, depth write off). Point = 3 axis circles (X/Y/Z) + axis lines at radius. Spot = forward axis line + cone wireframe to -Z cap + outer ring + inner ring (dimmer) at the range cap. Cone cap radius = tan(angle/2)*range.

EDITOR UI (client/ui/forms/editors/)
- UIPointLightForm / UISpotlightForm extend BBS UIForm; register a default panel + BBS default panels. Registered via UIFormEditorMixin.
- UIPointLightFormPanel / UISpotlightFormPanel: UIColor (with alpha) + UITrackpad per numeric field with the same limits as the form ranges ([[addon-forms]]) + UIToggle for entitiesOnly ("Entities only") / blocksOnly ("Blocks only", added 2026-06-09) / shadows ("Shadows", default ON, renamed 2026-06-09 from "No entity shadows"). entitiesOnly + blocksOnly are MUTUALLY EXCLUSIVE: each toggle's callback, when turned on, force-clears the other (form.set(false) + UIToggle.setValue(false); setValue does NOT fire the callback so no recursion). startEdit pushes current form values into widgets. Labels are hardcoded IKey.constant (e.g. "Bulb size (shadow softness)").

Связь: IRLite-only (BBS Form/Settings/L10n-фреймворк, которого нет у standalone-редактора). Те же кнобы движка у редактора реализованы через ImGui LightConfig + engineGroup() ([[project-irlite-base-ported]]). Источник: память IRLite.

ТРЕКИ РЕПЛЕЯ СВЕТА (2026-09-16): имена/цвета/иконки/группы задаёт LightTrackLayout через TrackCatalogMixin (см. [[project-per-light-profiles]]); UIReplaysEditorColorMixin удалён как мёртвый (BBS 2.6 берёт цвет из TrackStyle в каталоге, не из UIReplaysEditor.getColor).
