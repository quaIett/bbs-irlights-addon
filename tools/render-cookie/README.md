# Render/cookie differential harness

Run `tools/verify-render-cookie.ps1 -JavaHome <JDK17-or-newer> -GradleUserHome <cache>`.
The script compiles the current production resolver, complete spotlight renderer,
CookieArray, and core LightMath against real Minecraft 1.20.4 JOML 1.10.5.
Only Minecraft/BBS form values, renderer base/guide APIs, asset reads, GL/STB/native
allocation, logging and registry consumption are fixtures. No modified algorithm
is replaced. Real CookieArrayBase is unchanged and its GL guards are not tested
by this CPU harness. The legacy position API used by point lights is also tested.

`reference/` freezes the three pre-optimization files from the source snapshot
`optimization-java-2026-09-11-iter1/baseline/source/bbs-irlights-addon`.
Only their class names and references to each other gain the `Baseline` suffix.
Original SHA-256:

- IRLightPositionResolver.java: `04912264FB075ABF17273B3D088E82470EC0F46D403AB4B6B2B8A7221FD9B57B`
- SpotlightFormRenderer.java: `746366BA2CEA22F1C8EC213E2A3E873ED3576CFDC33DF47CA875017607C003E5`
- CookieArray.java: `1F58E8555A5058108B42A01A5F47FCD031E7C1DE1B5977A8971A9C73275A674D`

Tests compare registry arguments and matrix elements by raw bits for every
non-NaN value, including signed zero and infinity. Arithmetic NaNs must match by
classification: Java/JOML JIT does not preserve NaN sign/payload, and the same
unchanged arithmetic produced different NaN payloads across harness runs.
They also compare cache results/state plus ordered read/decode/upload/free/create/delete
events after every operation. Exception cases preserve existing cache behavior;
they intentionally do not introduce a new upload-retry or deletion policy.
The two original independent HashMaps have the same key insertion/removal history;
the production single-map traversal therefore preserves the old LRU order.
Unique stamps choose the same minimum, including signed counter overflow.
Forced ties and colliding String keys cover iteration order and HashMap tree bins.

Allocation uses ThreadMXBean after 1.8 million warm calls per implementation;
timing is the median of nine alternating 300,000-call pairs. Default JIT and
escape analysis remain enabled, with `-Xms256m -Xmx256m -XX:+UseSerialGC`.
The cookie warm-hit benchmark excludes actual BBS Link string construction;
the spotlight benchmark consumes arguments at a registry fixture. These results
describe these CPU paths under this harness and do not establish FPS gains.
Game checks remain live actors, film replay, BodyPart-attached lights, camera roll,
animated cookies longer than 32 frames, and resource reload.
