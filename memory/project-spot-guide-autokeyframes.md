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
09-18 CHECKPOINT/PORTS по запросу пользователя: master a03c253 зафиксировал auto-keyframe + прежние API anchors/память; общий master source покрывает1.20.1/1.20.4.4файла guide implementation/test перенесены без адаптации в port/1.21.1 и port/1.21.11; ранее незакоммиченный effects/UI порт сохранён и включён в их чекпоинт. Build PASS все4MC;39guide checks на каждой (156),дополнительно те же39 наJVM17 для каждой1.20.x. Полный старый profiles suite не повторяли: его предыдущая проверка записана в project-effects-keyframes-version-ports; запрос только необходимых тестов.
Выдача: C:/Users/qualet/Desktop/IRLights-1.1.7-autokeyframes,4JAR в корне+README.txt+verification.json. Рекурсивная проверка каждого class в addon/вложенныхjar:1.20.1=186/major61,1.20.4=186/61,1.21.1=186/65,1.21.11=173/65. В каждой ровно1правильное release core1.1.7(SHA),актуальные7patches,всеmixin classes,SpotGuideKeyframes.class;SHA desktop-копий совпали. Скрипт выдачи в основном addon build/verify-autokey-release.py.
Core исходники не менялись. Сборки последовательно с публикацией соответствующего release core;после1.21.11 mavenLocal восстановлен на release core1.20.x/JDK17. Основной addon остаётся master,1.20.1 сборочный detached наa03c253;основной core2.0/stash не трогали. Push не выполнялся. Runtime портов пользователем отдельно не подтверждён.
