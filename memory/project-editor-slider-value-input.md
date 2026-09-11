---
name: project-editor-slider-value-input
description: "Редактор (main): средний клик по слайдеру/drag-полю = инлайн текстовый ввод точного значения (стиль Blender). Реализовано ТОЛЬКО в editor/Widgets.java (trackpad + оба dragValue), commit 3b69a0d, рантайм PASS 2026-07-23, лог чист. Open: порт на 5 порт-веток редактора по команде."
metadata:
  node_type: memory
  type: project
  originSessionId: fad70489-235f-43c3-8e9e-52d165ce363f
  modified: 2026-07-23T10:15:27.791Z
---

Фича редактора (irl-editor, папка `BBS/irlights`, ветка **main**): middle-click по слайдеру открывает инлайн-поле точного ввода. commit **3b69a0d** (main, НЕ пушен), рантайм PASS у юзера 2026-07-23 (клавиша J, средний клик), лог чист (0 исключений `Widgets`/`irlredactor`).

Правка — ТОЛЬКО `src/client/java/org/qualet/irlredactor/editor/Widgets.java`. irl-core НЕ тронут → core-republish НЕ нужен. Компиляция: `JAVA_HOME=<jdk17> irlights/gradlew -p irlights compileClientJava` (Loom 1.9.2).

Механика:
- Слайдеры редактора КАСТОМНЫЕ (ImDrawList + invisibleButton); нативных `ImGui.slider*/drag*/inputFloat` в редакторе НЕТ (grep=0). Middle-click встроен вручную в 3 виджета: `trackpad` (все bounded-слайдеры с заливкой — intensity/radius/angle + ВСЕ настройки VL/тени/обводка/авто-свет/cookie) + `dragValue` float и double (позиция X/Y/Z).
- Статик-стейт (одно поле за раз): `editingId`, `ImString EDIT_BUF`, `editFocusPending`, `editSeenActive`, `editFieldW`, `double[] EDIT_OUT`.
- `middleClicked()` = `!isItemActive() && isItemHovered() && isMouseClicked(ImGuiMouseButton.Middle)` — зовётся в конце виджета, пока invisibleButton ещё текущий item.
- `beginEdit`: сид `EDIT_BUF` через `numToStr` (int без точки, иначе %.4f с обрезкой нулей) + фиксирует `editFieldW` (ширина поля НЕ дёргается при наборе).
- В начале виджета: `if (id.equals(editingId)) { editRow(...) → clamp/apply → return; }`. `editRow` = label слева + `ImGui.inputText(EnterReturnsTrue|AutoSelectAll)` c `setKeyboardFocusHere` на первом кадре. Коммит: Enter ИЛИ (`editSeenActive && isItemDeactivated`); Esc ревертит (штатное поведение inputText → парс = старое значение → no-op). `parseNum` терпит запятую-разделитель и юниты (`°` и т.п. срезаются).
- trackpad клампит в `[min,max]`; drag-поля без границ.

ГОЧА позиционирования (переделано по фидбеку юзера — первая версия «уезжала»): поле ПРИЖАТО к правому краю (`fieldX = pos.x + width - 9f - fieldW`, `fieldW` = ширина значения + запас, min 56), центрировано по вертикали — иначе значение прыгало с правого края в середину. Высота строки добивается `dummy` НИЖЕ поля (не перекрывая — перекрытие крадёт hover у inputText).

Open: порт на 5 порт-веток редактора (1.20.1/1.21.1/1.21.4/1.21.11) — по команде, вместе с прочим редизайном (правило [[project-editor-redesign]]: дизайн чиним на main, порты в самом конце). Build/run editor main = [[project-editor-redesign]] (JDK17, core main в mavenLocal, клавиша J). Роутинг «UI/виджеты редактора = только redactor» → [[reference-edit-routing-by-area]].
