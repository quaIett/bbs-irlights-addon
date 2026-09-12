---
name: project-caster-revision-gated-bbs231
description: "Порт силуэт-адаптера ревизий кастеров на BBS 2.5.2 DONE 2026-09-12: BbsSilhouetteBridge (MethodHandle-мост + layout-проверка AUDITED), гейты welds/hybrid, offset в позе, самотесты mob PASS 11 / cubic PASS 14, reuse на 2.5.2 доказан dev-клиентом (bake 21→0.01 мс); jar 1.1.6 в инстансе Prism; открыт пользовательский A/B."
metadata:
  node_type: memory
  type: project
  originSessionId: 2282deae-3269-424e-aab3-ed84c73278dc
  modified: 2026-09-12T11:54:19.379Z
---

ПРОБЛЕМА (замер 2026-09-12 #1, ин-гейм A/B 1.1.5 vs 1.1.6 на BBS 2.5.2, Complementary+IRLights, RTX 3060 Laptop): A≈B (frame p50 47.3 мс, gpu bake 20.8 vs 21.1), `sp.reuse=0`, `caster.unknown` до 2222. Корень: `BbsModelSilhouette.AUDITED` = true только для `2.3.1-1.20.4`; на 2.5.2 оба семплёра отдавали UNKNOWN. Не баг reuse-логики, а консервативный гейт [[project-performance-redesign-stage1a]].

ПОРТ (сессия dc53e10d, 2026-09-12 #2) — DONE, финальный отчёт и таблица дрейфа: `bbs-irlights-addon/docs/bbs-2.5.2-caster-revision-port-spec.md` (§3 дрейф, §4 файлы, §9 результаты).
- Новый `qualet/irlite/client/light/BbsSilhouetteBridge.java`: MethodHandle на каждый дрейфующий член BBS, резолв один раз при инициализации; `AUDITED` = версия ∈ {2.3.1-1.20.4, 2.5.2-1.20.4} И layout совпадает с проаудированным (legacy: Transform.rotate2, нет offset/welds; modern: rotationMode+quat, ModelGroup.offset, getWeldBindings). Лог при старте `[irlite] caster-revision bridge: bbs <ver> audited|NOT audited: ...`; `reportFailure` печатает первый сбой пробы один раз.
- `BbsModelSilhouette`: все чтения ModelInstance через мост (procedural/culling/scale/texture/view/items/armor), новые гейты 2.5.2 (welds ≠ ∅, hybrid VAO+CPU = `!getVaos().isEmpty() && !isVAORendered()`), `ModelGroup.offset` в сигнатуре позы и в `BbsModelPoseScratch`, `Signature.transform` хеширует rotate2 (2.3.1) либо rotationMode+quat (2.5.2). `IRLiteBbsCasterSource.revision()` в try/catch (RuntimeException|LinkageError → UNKNOWN), `modelConfigScale` → мост.
- Самотесты (`-Dirlite.checkCasterRevisions=true`): mob PASS 11 (вторая ротация = rotate2 либо quaternion); НОВЫЙ `BbsModelSilhouetteChecks` cubic PASS 14 на 2.5.2 / PASS 12 на 2.3.1 (quaternion-поза, восстановление живой позы incl. offset, shape key, цвет, visible, unsupported IK → UNKNOWN, resource reload).
- Трассировка (`-Dirlite.debugCasterRevisions=true`): `[irlite] caster-revision(cubic|mob): UNKNOWN because <причина>` один раз на причину + `[irlite] caster-collect: model blocks N (was M)` при смене набора собранных блоков. Так «нет reuse» больше не молчит.
- build.gradle: `-PbbsRuntimeJar=<jar>` — runClient против другого BBS (compile остаётся на libs 2.3.1).

ДРЕЙФ 2.3.1→2.5.2, чего в первом ТЗ НЕ было: `Transform.rotate2` удалён (NoSuchFieldError в Signature/mob-checks), `ModelIKDebug.enabled`/`ModelPhysicsDebug.enabled` удалены (гейт через BBSSettings.ikDebug + непустые chains), `ModelGroup.offset` добавлен, welds/partialVaos = hybrid-рендер. `view` НЕ удалён (`getView()`), `ModelInstance.model` был `IModel` уже в 2.3.1. Mob-путь цел (миксин-аксессоры применяются). apply*Once (IK/physics/constraints) при пустом конфиге формы — no-op.

РЕЗУЛЬТАТ dev-клиентом (runClient -Pmc=1.20.4, Complementary_IRLights, 854×480): 2.5.2 мир Flat (25 villager MobForm + spot): known 25/кадр, `sp.reuse` 25/кадр, gpu bake p50 0.01 мс (было 21), cpu frame p50 14.3 / pipeline 1.4 мс; с cubic chair-блоком — cubic PASS 14, 25 known. 2.3.1 регресс: mob PASS 11, cubic PASS 12, 25 known, bake 0.01. Байткод-чекер `<scratchpad>/checkrefs.py`: 177 mchorse-ссылок резолвятся против обоих jar (0 нерезолвленных).

СОСТОЯНИЕ: код в рабочем дереве addon НЕ закоммичен (checkpoint по команде [[commit-checkpoints]]); `build/libs/irlite-1.1.6+mc1.20.4.jar` (nested core 1.1.6) положен в `C:\prismlauncher\instances\BBS\minecraft\mods\` (бэкап прежнего jar: `<scratchpad dc53e10d>/results/instance-backup/`). Dev-env возвращён: миры Flat/Room восстановлены из бэкапов, iris.properties (enableShaders=false) и refreshedui-1.20.x.jar в run/mods как были; в `run/config/bbs/assets/models/mobs/chair` оставлена плоская cubic-модель для повторных cubic-проверок.

ОТКРЫТО: пользовательский ин-гейм A/B на инстансе Prism (сцена 09-12); `caster.unknown≈0` зависит от сцены — с `-Dirlite.debugCasterRevisions=true` в JvmArgs лог назовёт причины (не-villager мобы, items/armor/look_at в config модели, welds, state players, actions — by design UNKNOWN). Кандидаты на расширение известного подмножества, если сцена этого требует: не-villager MobForm, модели с items/armor-слотами без предметов.

ГОТЧИ ПРОГОНОВ: `refreshedui-1.20.x.jar` в run/mods крашит `UIFilmPanel` на BBS 2.5.2 (mixin redirect) — отключать; мир Room = film со сменой форм (villager→enderman→piglin→snow_golem) + ModelForm со state players — не для статик-теста; мир Flat = каноническая villager-сцена A/B 09-11; окно dev-клиента пользователь может закрыть/паузить вручную (прогоны 09-12 обрывались так, блок chair был снесён в мире); dev-клиент ставит игру на паузу при потере фокуса; убивать клиент: PowerShell `Get-CimInstance Win32_Process | ? {Name -eq 'java.exe' -and CommandLine -like '*fabric.dli*'}`.

Why: без порта 1.1.6 на текущем BBS пользователя не ускоряет вообще; гейт молчал. Теперь версия/лейаут логируются при старте, причины UNKNOWN трассируются, а один jar честно живёт на 2.3.1 и 2.5.2.
How to apply: новая версия BBS → прогнать checkrefs.py против её jar, добавить строку версии в BbsSilhouetteBridge.audit() только после diff-аудита pose-пути (Model.applyPose, ICubicRenderer, ModelFormRenderer.renderModel/apply*Once, MobFormRenderer/LivingEntityRendererMixin) и зелёных самотестов через `-PbbsRuntimeJar`.
