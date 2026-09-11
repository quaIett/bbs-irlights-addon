---
name: project-java-optimization-2026-09-11-iter1
description: "Java-итерация 1: 6 кандидатов, scratch/Member/heap/render/cookie/GL bind; 1.1.6, сборки PASS, игровой A/B OPEN."
metadata:
  node_type: memory
  type: project
  originSessionId: 2026-09-11-java-optimization-iter1
---

Запрос: начать оптимизации по Downloads/Telegram Desktop/IRLights-NEXT-SESSION-PROMPT.md. Реализованы 6 из 7 кандидатов. MC1.20.4, core main/addon master, версии1.1.6; BBS/VERSION был1.1.5 и приведён к1.1.6. Existing dirty сохранён; commit/push/runtime deployment не выполнялись.

Изменения: lazy immutable spot Member по retained slot только внутри bake; Model/Mob scratch с живыми capture, reference restore, nesting и cleanup exception safety; primitive max-heap индексов прежних128 SoA slots со strict ties/rejection; одна spot matrix при прежнем resolve API; cookie primitive long stamps по layer; glBindBufferBase заменяет лишний generic bind перед upload. UNKNOWN/point masks/качество/лимиты/частота и GLSL сохранены. TreeMap отложен: типичная польза не измерена, geometry уже frame-cached.

Измерения harness: Member24960→960 B/bake (26spots×20casters); Model4288→0 B/sample, Mob1648→88 (24groups/parts, snapshot+eval); spot192→112 B/call, cookie hit24→0 B/hit. Heap comparisons descending4096:503936→47848, random53340→4911;0 B/collect. Малые/all-rejected сцены не ускорились (последний замер+1.81%/+5.78%); timing шумный. Не выдавать за FPS/полный sample.

Проверки PASS: overlay44+27016, pool92208842, BBS145474 (real BBS/JOML/ModelPart; dispatcher boundary exceptions simulated), render/cookie627078 (production + fixtures), real GL2305073/201uploads и тот же baseline, block31/budget23/profiler23+CSV+4/lightcollector370491/pipeline3998684+21variants. Core build publishToMavenLocal и addon build -Pmc=1.20.4 PASS; nested один core1.1.6, exact SHA свежего core, Java major61. Независимое ревью без actionable замечаний.

Результат: C:/Users/qualet/Documents/Project/Minecraft/BBS/deliverables/optimization-java-2026-09-11-iter1. Addon SHA256=6B154C1BF5A3F457E1A429D162B50A999CA6E7672408764D3A49C602A685B703; core=56E4582D42110379B09D70317D1A42FF4C5489D1FB9FCA8D33DDD0E230ABD100. Отдельный source overlay ZIP/manifest, baseline sources+JAR, logs. Исходный локальный addon SHA4F58EC60... отличается от4B4101... старого перенесённого отчёта; его nested core совпадал с исходным local core. При базовой перепроверке core поменялись5class entries, это зафиксировано. Runtime CR реально называется ComplementaryReimagined_IRLights, SHA GLSL64FE36FE...C64CD совпал.17 runtime/mod/shader hashes сохранены.

Окружение: JDK C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot; GradleUserHome C:/Users/qualet/.gradle; Maven ~/.m2/repository; offline. Обновлены только два remap файла irl-core-a73a62ac/1.1.6, baseline copies сохранены. Полный отчёт addon/docs/performance-java-2026-09-11-iter1.md.

OPEN: игровой A/B, реальные sampler/dispatcher/mixin, BBS animation/reload, live actors/replay/BodyPart, cookie>32, плотный pool>128. Opt-in -Dirlite.checkCasterRevisions=true расширен до11 checks всех частей, здесь только скомпилирован, игра не запускалась. Предыдущий VL-тест отдельно остаётся OPEN. Пользователь попросил экономить токены/не затягивать, отвечать короче.

Связь: [[project-perf-followup-2026-09-11]], [[project-vl-followup-2026-09-11]], [[reference-chatgpt-memory-location]].

2026-09-12: по явному запросу пользователя сохранён commit checkpoint всех изменений Java/VL и тестов. irl-core=567de59; irlights=350e789; bbs-dof-addon=c625d0a. Checkpoint аддона включает эту запись и синхронизированную память; его hash смотреть в git log. Push не выполнялся. BBS/VERSION и deliverables находятся вне Git-репозиториев и сохранены отдельно. Использована прежняя Git identity пользователя через per-command config, глобальная конфигурация не менялась.
