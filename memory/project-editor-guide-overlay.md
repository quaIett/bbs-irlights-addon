---
name: project-editor-guide-overlay
description: "Редактор (irlights, main): гайды света + интерактивный драг спота портированы из аддона как ImGui-оверлей (screen-space, на background draw list как ImGuizmo). Коммит a7859ed. Драг = 2D hit-test + 3D-ray. Только выбранный источник (как гизмо)."
metadata:
  node_type: memory
  type: project
  originSessionId: aebbe0cc-2fc7-4040-a4dd-7a382a288c73
  modified: 2026-07-22T18:16:30.448Z
---

Фича DONE + коммит **a7859ed** на ветке **main** редактора (2026-07-22, рантайм PASS у юзера, лог чист). Порт аддонного `LightGuideRenderer`+`SpotGuideDrag` (BBS-стенсил) в BBS-free редактор как ImGui-оверлей. Источник-оригинал = [[plan-interactive-spot-guides]] (аддон); канон редактора = [[project-irlite-base-ported]]; активная UI-фаза = [[project-editor-redesign]].

Файлы (все `irlights/src/client/java/org/qualet/irlredactor/`):
- **НОВЫЙ** `editor/GuideOverlay.java` — весь оверлей + драг.
- `editor/LightEditorPanel.java` — поле `guides` + связка в `draw()` рядом с `drawGizmo()`.
- `light/LightGuideRenderer.java` — гейт старого 3D-wire паса.

Подход (ключевое): рисуем на `ImGui.getBackgroundDrawList()` (поверх мира, под панелью) — ТОЧНО как ImGuizmo. World→screen проекция через реконструированные view=`Rx(pitch)·Ry(yaw+180)` / proj=`perspective(fov,fbAspect,0.05,1000)` — те же матрицы, что `LightEditorPanel.drawGizmo()`, точки camera-relative (`pos - camPos`). НЕ 3D-wire в мире. Примитивы = `ImDrawList.addLine`/`addCircleFilled` посегментно (проекция круга = эллипс → рисуем N=48 сегментов, не `addCircle`). Цвет = RGB света, пол `MIN_VIS=0.28`; hot-хват = `EditorTheme.accentU32()` толще.

Визуал (повторяет аддон): point = 3 кольца радиуса `radius` по осям мира + 3 оси-линии; spot = ось(0→range) + 4 спицы + кольцо `outer` + кольцо `inner`(если < outer-0.5) + диск `range` на торце (`origin + fwd*range`).

Драг спота (ТОЛЬКО выбранный): hit-test 2D (кольцо=min dist курсор→сегмент ≤7px, диск≤10px), апдейт значения 3D — mouse-ray unproject (`invViewProj.transform(Vector4f)`, БЕЗ `transformProject` — joml 1.10.5 перегрузка ненадёжна) → cone-local (базис u,v,fwd) → cap-plane intersect z=range для угла (`angle=2·atan2(radial,range)`), closest-point-to-axis для range. Математика = `SpotGuideDrag.updateAngle/updateRange` аддона, минус BBS-стенсил. Хендлы: OUTER→`state.angle`, INNER→`state.soft`(=outer−inner), RANGE→`state.range`. Пишет `LightState`, `LightSync.push` коммитит live (маппинг угла: outer=angle, inner=clamp(angle−soft)).

Приоритет ввода (в `LightEditorPanel.draw()`): если `guides.isDragging()` → `drawGizmo()` СКИПАЕТСЯ (не воюют за мышь); иначе рисуем гизмо + `guides.frame(state, selected, canGuide)`, где `canGuide = !getWantCaptureMouse() && !ImGuizmo.isUsing()`. Старт драга только при canGuide + курсор на хвате + `isMouseClicked(0)`.

Область = **только выбранный источник** (директива юзера 2026-07-22 «гизмо/оверлей только на выделенном»). Невыбранные не рисуются в оверлее (первая версия рисовала все — откатано). Общий scratch origin/basis → выбранный рисуется в конце `frame()` после переустановки фрейма.

Гейт старого паса: `LightGuideRenderer.onRender` (LAST, 3D DEBUG_LINES, ВСЕ источники) делает ранний return при `LightEditorScreen.isOverlayActive()`. Итог: редактор ОТКРЫТ → богатый оверлей (только выбранный); ЗАКРЫТ → старый лёгкий каркас всех источников. Создаёт зависимость light→editor (import LightEditorScreen) — ок.

Билд/рантайм: republish core **main** (Loom 1.9, JDK17) в mavenLocal ПЕРЕД `compileClientJava` (гоча из [[project-editor-redesign]]); клавиша **J**. imgui-java **1.89.0**.

Открытые хвосты:
- Порт на порт-ветки редактора (1.20.1/1.21.1/1.21.4/1.21.11) — по команде юзера (директива: порт в самом конце).
- Гайды при ЗАКРЫТОМ редакторе сейчас = старый лёгкий каркас ВСЕХ источников; юзеру предложено ограничить/убрать — ждёт решения.
- Point-драг radius НЕ сделан (как в аддоне — там драга point не было).
- Тюнинг: толщины линий (LINE_T=1.7/hot 3.0), зоны хвата (7/10px), диск 5.5px — по желанию.
