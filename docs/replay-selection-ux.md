# Lit / Outline Replays: keyframe workflow (1.20.4)

Create a light, create a Lit Replays or Outlined Replays keyframe in the existing
Replays group, then choose actors. No form switches are required. Group and track
order are unchanged. `Nobody` applies an empty filter; `Use light settings`
returns that track to the form's saved configuration. The two lists are independent.

## Playback and compatibility

The existing string payload gains an optional `mode`: `selected` activates the
filter; `inherit` uses the original form value and switches. Missing mode retains
legacy behavior, including legacy extrapolation before the first keyframe. Merely
opening an old keyframe does not convert it. Choosing targets or either mode button
edits the ordinary BBS keyframe, using native undo and auto-keyframing.

An automatic Outline selection supplies an enabled local outline profile and
uses the replay list as its target (including block forms). It uses the form's
outline appearance values, including animated strength, without changing its
persistent switches. Global outline may stay off. Lit has no effect on Outline.

`ReplaySelectionTrackMixin` suppresses backward extrapolation only before the first
key of a new-format replay selection track. The form settings apply before that
key; the first selection takes effect at its exact tick. Legacy tracks and other
properties retain normal BBS behavior. Auto-keyframe UI follows this same rule.

The old UI changed switches on the authoring form, while the film controller renders
a copy created by `BaseFilmController.createEntities`. The new UI changes only the
track. Its mode and targets are applied together to the live copy during playback,
so there is no form-switch synchronization step or need to reopen the form editor.

## Validation

- `gradlew build -Pmc=1.20.4`: PASS.
- `tools/verify-profiles-bbs.ps1`: 1306 checks PASS against real BBS 2.6 values,
  forms, copy/serialization, tracks and IRLights profile registration. The headless
  test supplies the production pre-first-key hook through a PropertyTrack wrapper;
  it does not run Mixin or automate Minecraft UI.
- Tests cover both light types, copied live forms, exact key transitions, independent
  lists, nobody/inherit, backward scrubbing, restored undo snapshots, 16 legacy
  switch combinations, global outline off, conflicting target masks, and missing
  or foreign-film actors. Persistent form values stay unchanged.
- 11 bytecode API anchors PASS, including the new PropertyTrack injection target.
- Bundle PASS: matching main 1.20.x core 1.1.7, all seven unchanged patches, mixin
  classes and Java 17 bytecode. Reports: `build/profiles-bbs-test/`, `build/replay-ux/`.

Only the 1.20.4 target was built/tested. Its source checkout is the existing shared
1.20.x worktree. No changes were ported to 1.21.x, the standalone editor, or DOF.
Runtime visual acceptance remains with the user. No commit/push.

## Version rollout, 2026-09-17

The user accepted the 1.20.4 iteration and requested the same UX on 1.20.1,
1.21.1 and 1.21.11. The existing 1.20.x checkout provides 1.20.1. Seven shared
implementation/test files were ported to the existing 1.21.x worktrees, with only
one targeted addition to each version's mixin configuration. Version-specific
rendering code is unchanged; no core or shader changes were necessary.

All three requested builds PASS. Each version passes 1306 real-BBS checks and
11 bytecode anchors (3918 checks + 33 anchors across the new targets). Each bundle
contains the matching per-version core 1.1.7 and all seven unchanged patches.
Shared UX code and track layout match across source lines. Native Minecraft/UI
runtime acceptance remains with the user; these checks do not claim visual PASS.

Deliverables: `BBS/deliverables/replay-ux-1.1.7/<mc>/irlite-1.1.7+mc<mc>.jar`,
with build verification reports beside each file. The previously tested 1.20.4
build is included. All four delivered JAR hashes match their verified outputs.
No commits or pushes were made.

The last published MavenLocal core is the 1.21.11 line. Republish the matching
core before running 1.20.x or 1.21.1. The 1.21.11 client is launched using its
existing `runs/1.21.11` directory, with no quickplay or UI automation.

## Final acceptance, 2026-09-17

The user confirmed the remaining 1.20.1 and 1.21.1 runtime checks and authorized
committing the verified checkpoint. All four versions now have user acceptance
for the new UX. Standalone editor synchronization remains the next roadmap step.
