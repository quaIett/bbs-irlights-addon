---
name: project-dof-combo-sync
description: "Актуализация 7 комбо-патчей <pack>-irl-dof.irlights (свет+DOF) 2026-07-26: комбо = тело главного патча + DOF-хвост; генератор tools/gen-combo.ps1 + гейт tools/verify-combos.ps1 в bbs-dof-addon; ручные правки только в хвосте."
metadata:
  node_type: memory
  mod_scope: shader-inject
  type: project
  originSessionId: 8af69920-d2bc-4694-97d7-bf5ac892953f
  modified: 2026-07-26T15:09:57.870Z
---

Комбо-патчи `bbs-dof-addon/patches/<pack>-irl-dof.irlights` (7 паков) структурно =
**[шапка комбо] + [ТЕЛО главного патча света `bbs-irlights-addon/patches/<pack>.irlights` целиком] + [DOF-хвост]**.
Шапка отличается от главного патча только `@name "<Pack> IRL + DOF"` и `@dof 1`; `@target/@packversion/@irlite/@marker` берутся из main.
DOF-хвост (Слой 2, focus-mask glue) живёт ТОЛЬКО в комбо, начинается с баннера `#  IRL-DOF: focus-mask hooks…` (у Photon — `#  DOF: focus-mask write…`) и идёт до конца файла.

**Why:** IRL-половина комбо дрейфует при каждой правке главного патча. К 2026-07-26 отставание было 28 коммитов (комбо от 09.07, main 22–24.07): не было UBO `IrliteVlGlobals`, W2 `IRLITE_CLUSTER`, VL 3b/3c, UI-рефактора, ретаргета IterationRP 0.8.26. Комбо IterationRP и Solas ФИЗИЧЕСКИ НЕ ПРИМЕНЯЛИСЬ (anchor not found) — «+DOF» паки были сломаны. Старая ручная синхра (сплайс из `Shadres/Modification`) себя не оправдала: комбо разошлись даже с main своей же даты.

**How to apply:**
- Регенерация: `bbs-dof-addon\tools\gen-combo.ps1 -Pack <PackDir>` (in-place; `-OutDir` = dry-run). Идемпотентен. Ручные правки — ТОЛЬКО в DOF-хвосте (ретаргет якорей под новую версию пака), генератор сохраняет хвост байт-в-байт.
- Гейт: `bbs-dof-addon\tools\verify-combos.ps1` — применяет main и комбо к `Shadres/Original/<pack>` через javac-harness (`bbs-irlights-addon/tools/build`, java на PATH) и печатает: main ok / combo ok / число файлов дельты main↔комбо. **Инвариант: дельта = РОВНО файлы DOF-хвоста; любой IRL-файл в дельте = регресс.**
- Отменяет старое правило «правки инжекта вносить в ОБА патча руками» из [[project-photon-outline-switch-to-old]]: теперь правится только main, комбо пересобирается.

Результат 2026-07-26 (**закоммичено `bbs-dof-addon master = 2d6c230`**, 10 файлов +7546/−1527; не пушено — origin у репо не заведён): 7/7 применяются, DOF-дельта = 13/6/15/6/13/12/10 файлов (Bliss/BSL/CR/IterationRP/Photon/RV/Solas), все выходы несут `IrliteVlGlobals` + binding=7 + `IRLITE_CLUSTER`. EOL остались LF (`git ls-files --eol` = i/lf w/lf; .gitattributes в репо нет).

Гочи:
- **Solas V3.7 потребовал ручного ретаргета 2 якорей DOF-хвоста**: (1) `programs/gbuffers_entities.glsl` — пак разбил запись на `DRAWBUFFERS:0367 / #else / :03`, mask-write перенесён ПОСЛЕ `#endif` (покрывает обе ветки), якорь = `gl_FragData[1] = vec4(0.0, 0.0, 0.25, 1.0);\n    #endif\n}`; (2) `screen.DOF_CONFIG` потерял ведущие `<empty> <empty>` → якорь сузили до подстроки `MANUAL_FOCUS DOF_FOCUS TILT SHIFT`.
- `Shadres/Modification` устарел у BSL (10 файлов), Bliss (только хвостовой newline в lang), IterationRP (irlite_lights.glsl) — это ПРЕДСУЩЕСТВУЮЩИЙ дрейф main-линии, к комбо отношения не имеет; source of truth для комбо = сам главный патч, не Modification.
- DOF-хвост местами якорится на строки, вставленные IRL-частью (напр. `size.buffer.colortex10=IRLITE_VL_RESOLUTION …`) — переименование в главном патче ломает комбо, ловится гейтом.
- Комбо теперь требуют рантайм-контракты irl-core 1.1.5 (UBO binding 7, cluster binding 6, атлас теней) — «+DOF» пак с устаревшим irlite не заработает. `bbs-dof-addon` сам от irl-core не зависит: он лишь бандлит `patches/*.irlights` в jar (`assets/irl_dof/patches`) и роняет их патчеру IRLite.
- BBS-пак пересобран 07-26 (`build-bbs-pack.ps1`, 5/5 OK, 136s): в `irl_dof-1.20.x.jar` все 7 комбо байт-в-байт == `patches/`. **ГОТЧА: под Claude-обёрткой `JAVA_HOME` = JDK 8 (x86) → все 5 сборок падают «Gradle requires JVM 17»; лечится `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'` перед запуском скрипта.**
- Ин-гейм НЕ проверялось (за пользователем): поставить jar в инстанс → перепатчить паки (`<pack>_IRLights+DOF`).

Связь: [[patcher]], [[project-photon-outline-switch-to-old]], [[plan-post-trilogy-port-rollout]] (шаг 3 = порт DoF на port/1.21.1, ещё не делался), [[reference-edit-routing-by-area]].
