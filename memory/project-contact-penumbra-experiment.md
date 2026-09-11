---
name: project-contact-penumbra-experiment
description: "Эксперимент contact-true оценщика полутени (2026-08-11, dev photon_v1.3b_IRLights ONLY, патчеры/другие паки не тронуты): IRLITE_SHADOW_PENUMBRA_MODE 0 classic/1 contact + IRLITE_CONTACT_BLOCKER_TAPS; backprojection-валидность + вес 1/p² + центр-тап + стратификация + raw-фолбэк; point на zPersp; ин-гейм PENDING."
metadata:
  node_type: memory
  type: project
  originSessionId: 5f41968d-ae5b-41fd-9279-b4db04a50ca8
  modified: 2026-08-11T01:37:44.467Z
---

Запрос юзера (со схемой 0→0.5→1): размытие тени должно быть 0 у контакта с объектом и расти с расстоянием от окклюдера; classic-оценщик (среднее по всем блокерам поискового диска + кламп lightSize) при большом Softness сатурировал оценку → равномерное мыло, корень тени пропадал. Приоритет = качество, цена вторична. Всё в дев-клиенте run\shaderpacks\photon_v1.3b_IRLights (hot-reload), файлы: irlite_lights.glsl + shaders.properties (screen.IRLIGHTS+sliders) + lang/en_us.lang.

РЕАЛИЗАЦИЯ (mode 1, spot+point, меняется ТОЛЬКО оценка ширины — фильтрация PCF/EVSM/MSM нетронута):
- Per-tap: p = lightSize·(recv−d)/max(d,near); валидность backprojection offset ≤ pF+1.5·texel (недосягаемый блокер = шум, не тень); вес 1/pF² (покрытие светового диска — контакт-блокер доминирует над корпусом объекта). В сумму идёт СЫРОЕ p (флор texelWorld только в весе/валидности) → средняя уходит суб-тексель → SHARPEN на полную (иначе контакт у mode 1 выходил МЯГЧЕ classic).
- POINT НА zPersp во всех per-tap формулах (центр-тап/петля/фолбэк): микс евклидова refDist с dominant-axis d давал у контакта фиктивный зазор refDist·(1−maxComp) → корень мылился к диагоналям куба (0 только у центров граней; classic имеет тот же микс — там лишь сдвиг средней). Осевой масштаб сокращается в отношении → формула точная (урок E4).
- Гарантированный центр-тап (без маржи): умбра-контакт (пластина над ресивером) не проскальзывает между разреженными кольцами vogel.
- Стратификация: каждый 3-й тап на кольце min(4·texel, search) — поимка контакт-блокера детерминирована, иначе 1/p²-средняя бимодальна (пойман/нет = монетка IGN → прыжки MSM-мипов, пятна резкий/кремовый).
- Raw-фолбэк внешней кромки: rawDistSum/rawCount по ВСЕМ депт-хитам до валидности; 0 валидных + raw>0 → classic-средняя (у внешней границы зона валидности = кольцо ~1.5 текселя, тапы мажут → без фолбэка сыпь lit/soft). Spot return 1.0 только при raw==0; point legacy-exit после MSM-гейта тоже raw-aware.
- Grazing-масштаб slope-маржи: irlGraze = max(dot(nN,Loff),0.2), tapMargin = depthBias(offset/irlGraze) — 45°-потолок плоской маржи + 1/p² = один ложный self-блокер схлопывал оценку в резкость (шахматная сыпь на пологих полах). Spot заодно получил slope-маржу вообще (в classic-споте её нет).
- Spot: offWorld от ФАКТИЧЕСКОГО пост-кламп смещения (кламп обода конуса сжимал краевые тапы, номинальный оффсет отвергал реальные блокеры). EVSM-бленд расширен smoothstep(minPenE, 3×) (mode-1 only) против дребезга веток на шумной оценке.
- Опции: IRLITE_SHADOW_PENUMBRA_MODE 1 // [0 1] (default 1 = contact, 0 = classic для A/B) + IRLITE_CONTACT_BLOCKER_TAPS 24 // [12 16 24 32 48] (слайдер, spot+point). Classic-ветки в #else БАЙТ-вербатим (parity подтверждён verify-wf).

ПРОЦЕСС: ревью-wf (3 линзы × adversarial verify) — 10 находок, 8 confirmed, ВСЕ закрыты фиксами выше; verify-wf 3/3 OK (компиляция d4+c0_vl × mode × PREFILTER × QUALITY × PYRAMID; фиксы полны; parity mode 0).

ВАХТА ИН-ГЕЙМ (pending): (1) F3×F4 — на крутых склонах (graze-флор 0.2) у ламп ближе 5·lightSize маржа может резать реальные блокеры → внешняя каёмка контактной тени обрывается в свет; лечение = кап маржи в несколько текселей или signed receiver-plane предикция; (2) остаточное зерно спот-бленд-зоны (порог ~3-9 текселей); (3) стоимость: центр-тап+24 тапа против 5 (point)/10 (spot) — замер FPS по желанию. Тираж на патчеры/паки/классика-выпил — СТРОГО по команде юзера после ин-гейм вердикта.

Связь: [[plan-shadow-filtering-refactor]] (финальный point-стек MSM4, куда встал оценщик), [[shader-shadow-sampling]] (контракт чтения), [[addon-shadows]] (бейк), [[sync-workflow]] (правки прямо в run-копию, патч — отдельным шагом по команде).
