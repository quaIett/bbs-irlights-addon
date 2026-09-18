---
name: project-spot-guide-autokeyframes
description: Spotlight guides support BBS 2.6 auto-keyframes on release master.
type: project
---

09-18 DONE code in main addon folder/master; no commit/push. Scope: current 1.20.x sources, build/checks only MC1.20.4; no version-branch ports.
Request: guide drag creates keys when BBS auto-keyframe enabled; only necessary tests.
SpotGuideDrag reads BBSSettings.autoKeyframe when gesture starts; SpotGuideKeyframes writes range/radius/inner_radius via FormProperties TrackId. Auto ON: insertInheriting at current film cursor, including missing/empty channels, same tick updates existing key. Auto OFF: governing-keyframe behavior preserved, empty track still refuses drag. Click without movement creates nothing. Form-editor direct writes unchanged.
Undo/sync: BaseValue.edit(properties, FLAG_BATCH) snapshots before channel creation; finish marks FLAG_UNMERGEABLE. Runtime undo/redo and UI guide motion NOT verified.
Validation: gradlew build -Pmc=1.20.4 PASS; tools/verify-spot-guide.ps1 with JDK21 PASS39 against real BBS2.6 tracks/forms/serialization/playback. BBSMod singleton stub only; game not launched. Tests cover all3properties, absent/empty track, auto off, insertion/neighbours/interpolation, same-tick updates, advancing cursor, save/playback and before-state restoration. BodyPart construction requires Minecraft bootstrap, excluded from headless check.
OPEN: user runtime acceptance on MC1.20.4; later version ports if requested.