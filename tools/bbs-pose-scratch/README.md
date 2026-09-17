# Проверки BBS pose scratch

Из корня аддона после компиляции client classes:

```powershell
./tools/verify-bbs-pose-scratch.ps1 `
  -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot' `
  -GradleUserHome 'C:\Users\qualet\.gradle'
```

Скрипт отдельно компилирует **production** `BbsModelPoseScratch` и
`BbsMobPoseScratch` вместе с harness. Используются фактический
`libs/bbs-2.3.1-1.20.4.jar`, JOML 1.10.5, named Minecraft 1.20.4 `ModelPart`
и production `BbsModelSilhouette.Signature` из client classes. Заглушек нет.

Проверяется равенство pose signature прежнему алгоритму со свежими массивами;
реальные `Model.resetPose()`/`applyPose()`; восстановление исходных
`current`/`color`/`orient` по ссылке и float-полей побитово, включая alpha,
rotate2, scale, lighting, NaN и signed zero; девять float и два boolean
каждого ModelPart; смена размеров, порядка и идентичности групп/частей;
изменение live list; вложенные scopes; отсутствие удерживаемых чужих ссылок.

Проверки отказов включают исключение list.get во время setup, реальные
исключения applyPose/signature и моделируемые getPose/dispatcher exceptions.
Production `release(Cleanup, Throwable)` проверяется при всех восьми
комбинациях отказов pose/overlay/cache, с исходной renderer-ошибкой и без неё:
порядок cleanup, восстановление частей до cache cleanup, первая ошибка,
suppressed-ошибки и возможность повторного использования scope.

Harness **не запускает полный BbsModelSilhouette.sample или
BbsMobSilhouette.sample**, Minecraft dispatcher и mixin-интеграцию. Полные
CasterRevision components, визуал, смена ресурсов и игровые callback-пути
остаются для игрового теста. Существующий opt-in fixture
`BbsMobSilhouetteChecks` расширен проверкой всех частей, model flags и
entity.hurtTime; включение: `-Dirlite.checkCasterRevisions=true`.
Этот проход не запускал игру; ожидаемая новая строка fixture — `PASS 11`.

Результат 2026-09-11: **145474 assertions PASS**. Лог по умолчанию выводится
в консоль; сохранённый лог текущего прохода:
`build/bbs-pose-scratch-test/verification.log`.

ThreadMXBean после 30000 warmup и 20000 измеряемых вызовов, 24 группы/части:

| Снимок | Прежний алгоритм, байт/вызов | Scratch, байт/вызов |
| --- | ---: | ---: |
| Model snapshot + reset/apply пустой позы + signature | 4288 | 0 |
| Mob snapshot + изменение/восстановление поля | 1648 | 88 |

Это аллокации измеряемого участка в данной JVM после прогрева. Они не
включают полный renderer.getPose/dispatcher/sample и не измеряют FPS.
Первое использование, рост capacity и новая глубина вложенности выделяют
память; актуальная поза и текущие списки считываются при каждом вызове.
