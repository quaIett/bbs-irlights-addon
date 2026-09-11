---
name: _index-closed
description: "Индекс ЗАВЕРШЁННЫХ тем трилогии IRLights (порты, PASS-фиксы, done-планы); вынесен из MEMORY.md, НЕ грузится (префикс _). Файлы живы в складе, recall находит напрямую. Читать вручную."
metadata:
  node_type: memory
  type: reference
  originSessionId: 027d5eee-b729-4812-853e-28b1cf614c37
  modified: 2026-07-23T10:46:56.689Z
---

Завершённые темы, вынесенные из горячего MEMORY.md 2026-07-23 (консервативная архивация: только бесспорно закрытое, без открытых хвостов). Файлы остаются в складе — recall находит их по description напрямую; этот список — навигация для человека. НЕ грузится при старте. Тема снова стала активной -> вернуть строку в MEMORY.md.

## Порты (линии закрыты; порт-фаза отложена «в конце по команде»)
- [project-port-1211](project-port-1211.md) — порт 1.20.4->1.21.11 + дельты 1.21.1/1.21.4 + карта API; 1.21.11 = capture-queue.
- [project-port-12111-refresh](project-port-12111-refresh.md) — линия 1.21.11 ЗАКРЫТА.
- [project-trilogy-unify-11](project-trilogy-unify-11.md) — унификация: per-version ядро, версия 1.1, пуш.
- [project-port-1214](project-port-1214.md) — линия 1.21.4 ЗАКРЫТА; configure 3-арг; yaw-drop/PositionColor per-mod.
- [project-port-1201](project-port-1201.md) — порт 1.20.1: только деп-матрица + LWJGL-пин, ноль .java.
- [plan-port-1211-workflow](plan-port-1211-workflow.md) — порт 1.21.1 done; статус-блок внутри.

## Фиксы (закрытые, PASS)
- [fix-shadow-depthstate-repin](fix-shadow-depthstate-repin.md) — ре-пин depth/blend/матриц перед emit + feet-pivot AABB->сфера.
- [fix-shadow-slot-rank-stability](fix-shadow-slot-rank-stability.md) — фикс прыгающих теней при спросе>пула: rank-стабильность + spare; PASS.
- [fix-bone-attached-light-deadzone](fix-bone-attached-light-deadzone.md) — bone-свет: render-path забирает всегда.

## Расследования / планы (DONE)
- [project-point-shadow-square-root-cause](project-point-shadow-square-root-cause.md) — зернистый квадрат = point 512 vs 1024; закрыт tier0=1024.
- [plan-perf-fix-cluster-phase3](plan-perf-fix-cluster-phase3.md) — Phase 3 кластеризация DONE (binding 6, 67->112 FPS); тираж отложен.
- [plan-irlights-settings-unification](plan-irlights-settings-unification.md) — единый дизайн настроек + ребрендинг DONE.
- [plan-interactive-spot-guides](plan-interactive-spot-guides.md) — интерактивные гайды спота; PASS+коммит.
- [project-gui-lag-gpu-bound-diagnosis](project-gui-lag-gpu-bound-diagnosis.md) — лаг GUI = GPU-bound; рычаг = кластеризация (done); FrameProfiler откачен.
