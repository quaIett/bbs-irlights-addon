---
name: project-shadow-softness-perf
description: Замер GPU-цены слайдера Softness (размытие теней) на spot/point, 2026-09-12; кривая немонотонна, пик в бленд-зоне PCF+EVSM; бейк от Softness не зависит.
metadata:
  type: project
---

ЗАМЕРЕНО 2026-09-12 (закрывает «FPS-замер» из [[plan-shadow-filtering-refactor]]). Стенд: 2 автономных GPU-harness по образцу `tools/vl/VlGpuTest.java` (LWJGL 4.5 hidden ctx, production `irlite_lights.glsl` CR компилируется дословно через гейт `IRLITE_VL_PASS`, MAIN зовёт `irlite_spotShadow`/`irlite_pointShadow` на пиксель): CPU-бейк честной перспективной depth-карты + min/max пирамиды + EVSM4 (warp 42/-8) / MSM4 cube-array — все ветки PCSS активны. Softness гоняется через UBO `vlF.z` — ОДНА скомпилированная программа на весь свип. Файлы: `<scratchpad>/ShadowSoftnessGpuTest.java`, `PointSoftnessGpuTest.java`, слитый `softness-perf-all.csv` (584 строки, медиана 20-25 GL_TIME_ELAPSED, IQR узкий). ЖЕЛЕЗО: RTX 3060 Laptop, 1920x1080, 4 лампы на 100% экрана, MEDIUM (spot cell 1024 / point face 1024).

ЦЕНА ФИЛЬТРА НА ЛАМПУ, полный экран (вычтен пол «кода теней нет» 0.12-0.15 мс/кадр):
- spot MED: 0.141 мс (Softness 0) -> 0.156 (0.1) -> 0.224 (0.4) -> 0.410 (0.8) = x2.9
- spot LOW(512): 0.152 -> 0.279 = x1.8; spot HIGH(2048): 0.131 -> 0.496 = x3.8 (чем выше разрешение теней, тем ДОРОЖЕ размытие; при Softness 0 высокое разрешение не дороже)
- point MED: 0.357 -> 0.490 (0.1) -> 0.631 (0.8) = x1.8 — point стартует в 2.5x дороже спота, но растёт вдвое слабее (MSM обслуживает ВСЕ полутени, PCF-цикл мёртв)
- жёсткие тени (QUALITY 0): 0.08 spot / 0.07 point — ПЛОСКАЯ линия, Softness не читается вообще
- без префильтра: spot x5.8, point x4.8 (до 2.4 мс/лампа) — EVSM/MSM экономят до 4x именно на размытии, это их главный вклад

НЕМОНОТОННОСТЬ (важно): максимум цены НЕ на конце слайдера. Пики воспроизводимы (узкий IQR): spot MED «контакт» 1.83 мс при 0.4 против 1.60 при 0.6; spot LOW «решётка» 2.53 при 0.05 против 1.81 при 0.8. Причина в коде, не шум: бленд-зона `smoothstep(minPenE, minPenE*1.5, penDepthTex)` (полутень 3..4.5 depth-текселя) считает И полный PCF-цикл С ОТКЛЮЧЁННЫМ early-out, И EVSM-фетч. Дорого не «максимальное размытие», а попадание большой доли экрана в эту зону.

ЧТО ОТ SOFTNESS НЕ ЗАВИСИТ ВООБЩЕ: (1) вся Java-сторона — `shadowSoftness` живёт только в `VlGlobalsBuffer.setShadow` (UBO-аплоад из LightCollector), бейк/EVSM/пирамиды пекутся по dirty-тайлам и слайдер им невидим (подтверждает аудит из [[plan-shadow-filtering-refactor]] на 1.1.6); смена слайдера НЕ ре-бейкает; (2) волюметрика — в VL-половине GLSL нет ни одного чтения `IRLITE_SHADOW_SIZE_LIVE`/`lightSize`; (3) `IRLITE_SHADOW_LOD` fast-путь (тусклые лампы) — 1 тап/1 MSM-фетч.

ВОТЧЛИСТ: `irlite_outlineInk` (COMPOSITE1) делает ВТОРОЙ полный вызов теней с `fast=false`. Гейт `contourFactor <= 1e-4` ограничивает его кромками — НО при включённом outline glow (`vlC.w` bit10) contourFactor = Френель почти везде, и цена теней вместе с размытием удваивается на большой площади.

ПЕРЕВОД В КАДР: сцена «10 ламп, каждая ~30% экрана» ~= 3 полноэкранные лампы -> spot 0.42 мс (Softness 0) против 1.23 мс (0.8): +0.8 мс, при бюджете 16.7 мс (60 FPS) это ~5% кадра, то есть 60 -> ~57 FPS. Для ин-гейм подтверждения свип надо повторить профайлером ([[plan-vl-profiler]]) — числа тут синтетические, per-pixel-per-light.
