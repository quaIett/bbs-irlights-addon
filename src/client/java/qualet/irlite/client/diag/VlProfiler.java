package qualet.irlite.client.diag;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL33C;

/**
 * Dev-only GPU profiler for the IRLite volumetric/light pipeline, enabled with
 * {@code -Dirlite.profileVl=true} (same boot-time pattern as irlite.profileShadows).
 *
 * <p>Level 1 — GL timer queries: every Iris fullscreen pass (deferred*, composite*,
 * begin*, prepare*, shadowcomp*) is bracketed with GL_TIME_ELAPSED via
 * CompositeRendererTimerMixin, and the mod-side shadow bake is bracketed around
 * FramePipeline.frame in GameRendererLightMixin (the bake runs strictly before the
 * Iris pass sequence, so the sibling brackets never nest). The bake bracket is
 * further PARTITIONED into sibling segments at the bakeInner seams by the
 * core-side {@code ShadowBakeProbe} (always installed; its hooks are no-ops
 * while the profiler is off):
 * bake-head (collect/prioritize + pre-loop setup) -> bake-spot -> bake-spot-pyr
 * -> bake-spot-evsm -> bake-point -> bake-point-pyr -> bake-point-evsm ->
 * bake-tail, with a derived "bake=SUM" cell
 * in the window line. The probe also feeds per-window WORK COUNTERS (full static
 * bakes per type+tier, overlay draws, static->live copies, faces baked/copied,
 * pyramid/EVSM flush sizes) printed as a second "[irlite] bake:" line. Results
 * are read back asynchronously from a query pool a few frames later — the
 * pipeline is never stalled — and aggregated into 1-second windows printed to
 * the log and mirrored onto a small HUD overlay.</p>
 *
 * <p>Level 2 — opt-in differential sweep ({@code -Dirlite.profileVlSweep=true}): cycles VlGlobals UBO flag
 * configs frame-by-frame to attribute the deferred2 (VL march) cost to shadows /
 * noise / morph / Hi-Z / cluster-cull, printing a table to the log and the chat.</p>
 */
public final class VlProfiler
{
    /** Live gate for the whole profiler. Starts from -Dirlite.profileVl so the
     *  old boot-time workflow still works, but the settings UI can flip it.
     *  Volatile: written from the UI thread, read on the render thread. */
    private static volatile boolean enabled = Boolean.getBoolean("irlite.profileVl");

    /** Toggle requested from the UI, applied at the top of the next frameTick.
     *  Flipping mid-frame could strand an open GL_TIME_ELAPSED query (endPass
     *  would early-return past its close), so the switch waits for the frame
     *  boundary, where no bracket is ever open. */
    private static volatile Boolean pendingEnabled;

    public static boolean isEnabled()
    {
        return enabled;
    }

    /** Requested state, i.e. what a UI button should paint right now. */
    public static boolean isEnabledOrPending()
    {
        Boolean pending = pendingEnabled;

        return pending != null ? pending : enabled;
    }

    public static void toggle()
    {
        pendingEnabled = !isEnabledOrPending();
    }

    /** Synthetic pass name for the mod-side shadow bake bracket opened at
     *  renderWorld HEAD. The core-side ShadowBakeProbe sections then switch it
     *  to bake-spot/-spot-pyr/-spot-evsm/-point/-point-pyr/-point-evsm/-tail
     *  siblings, so this name ends up covering only the pre-spot-loop head
     *  (collect/prioritize, quality apply, beginBake) — the derived "bake"
     *  total in the window line is the old whole-bracket number. */
    public static final String PASS_BAKE = "bake-head";

    /** Every bake segment (the head bracket + the probe-switched siblings)
     *  carries this prefix; the window flush sums them into the derived total. */
    private static final String BAKE_PREFIX = "bake-";

    /** The Iris pass name of our VL march program (the sweep target). */
    public static final String PASS_VL = "deferred2";

    private static final int POOL_LIMIT = 4096;
    private static final boolean DETAILED = Boolean.getBoolean("irlite.profileShadowDetail");
    private static final boolean SWEEP = Boolean.getBoolean("irlite.profileVlSweep");
    private static final long WINDOW_NS = 1_000_000_000L;

    /** Frames after which an unavailable FIFO head is presumed poisoned (a query
     *  whose begin never took) and evicted so the drain can continue behind it. */
    private static final int STUCK_FRAMES = 120;

    /** Iris Program -> pass name, filled at pipeline construction (createProgram
     *  RETURN inject). Weak keys: programs are dropped wholesale on F3+R reload. */
    private static final Map<Object, String> PASS_NAMES = new WeakHashMap<>();

    /** GL query ids not currently in flight (allocated lazily, never deleted). */
    private static final ArrayDeque<Integer> freeQueries = new ArrayDeque<>();
    private static int allocatedQueries;

    /** In-flight queries, FIFO — timer queries complete in submission order, so
     *  the drain can stop at the first unavailable result. */
    private static final ArrayDeque<Pending> pending = new ArrayDeque<>();

    private static int activeQuery = -1;
    private static String activePass;
    private static long frameNo;
    private static int droppedSamples;
    private static int externalTimerSkips;

    private static final Map<String, TimingStats> window = new HashMap<>();
    private static int completedGpuFrames;
    private static int rejectedGpuFrames;
    private static final FrameGpuTimings frameTimings = new FrameGpuTimings(
        VlProfiler::recordFrame, frame -> {
            rejectedGpuFrames++;
            ProfileCapture.sample("frame", frame, "gpu-rejected", 1L);
        });
    private static boolean sessionStarted;
    private static boolean pipelineChanged;
    private static WeakReference<Object> sessionWorld;
    private static long windowStart;
    private static volatile List<String> hudLines = List.of();

    /** CPU-side per-window stats: nanoTime brackets around render-thread work
     *  that issues no GL (light collect/prioritize/cluster build, SSBO upload)
     *  plus the frame-to-frame delta ("frame" — real frame time, so the log
     *  carries FPS next to the GPU pass costs). Flushed with the same 1-second
     *  window as a separate "[irlite] cpu:" line. */
    private static final Map<String, TimingStats> cpuWindow = new HashMap<>();
    private static long lastFrameTickNs;

    /** Per-window work counters fed by the core-side ShadowBakeProbe (bakes per
     *  type+tier, faces copied/cleared, pyramid/EVSM flush sizes). Values are
     *  WINDOW SUMS; the flush line prints the window's frame count next to them
     *  so per-frame rates can be read off. Render thread only. */
    private static final Map<String, long[]> counters = new HashMap<>();
    /** Frames seen since the last window flush (normalizes the counters). */
    private static int windowFrames;

    // --- VRAM telemetry (GL_NVX_gpu_memory_info, NVIDIA only) ----------------
    // One "[irlite] vram:" line per window: free dedicated VRAM + the driver's
    // cumulative eviction count/size with per-window deltas. Diagnoses residency
    // thrash: a growing filter-pass time at CONSTANT work + climbing evictions
    // = the driver is demoting our atlas/EVSM textures, not an algorithm bug.
    private static final int NVX_DEDICATED_VIDMEM = 0x9047;
    private static final int NVX_TOTAL_AVAILABLE = 0x9048;
    private static final int NVX_CURRENT_AVAILABLE = 0x9049;
    private static final int NVX_EVICTION_COUNT = 0x904A;
    private static final int NVX_EVICTED_MEMORY = 0x904B;
    /** null = not probed yet; probed once on the render thread. */
    private static Boolean nvxMemoryInfo;
    private static long lastEvictionCount = -1;
    private static long lastEvictedKb = -1;

    private VlProfiler()
    {}

    private static final class Pending
    {
        final int query;
        final String pass;
        final long frame;

        Pending(int query, String pass, long frame)
        {
            this.query = query;
            this.pass = pass;
            this.frame = frame;
        }
    }

    /* ---- wiring from mixins ------------------------------------------------ */

    /** CompositeRendererTimerMixin, at createProgram RETURN: remembers which Iris
     *  Program object is which pass ("deferred2", "composite1", ...).
     *
     *  <p>Deliberately NOT gated on {@code enabled}: createProgram only runs at
     *  shaderpack load, long before anyone toggles the overlay on. Skipping the
     *  put while off left PASS_NAMES empty, so a later runtime enable timed every
     *  composite pass under the single name "unnamed" — and because the stats
     *  window is keyed by name, they all summed into one meaningless bucket
     *  instead of reporting per-pass. The cost is a handful of WeakHashMap puts
     *  per pack load; the entries die with the programs on F3+R either way.</p> */
    public static void registerPassName(Object program, String name)
    {
        if (program == null || name == null)
        {
            return;
        }
        PASS_NAMES.put(program, name);
        pipelineChanged = true;
    }

    public static String irisPassName(Object program)
    {
        String name = PASS_NAMES.get(program);
        return name == null ? "unnamed" : name;
    }

    /**
     * Once per frame, at renderWorld HEAD, before any bracket of this frame is
     * opened: drains finished queries, advances the sweep state machine and
     * flushes the 1-second stats window. All GL work happens on the render
     * thread with the context current.
     */
    public static void frameTick()
    {
        if (enabled)
        {
            // An exception or a changed Iris anchor must not strand our timer.
            if (activeQuery != -1)
            {
                frameTimings.invalidate(frameNo);
                endPass();
            }
            frameTimings.seal(frameNo);
        }
        // Frame boundary: no GL bracket is open here, so this is the only safe
        // place to flip the gate. Must run BEFORE the guard below, or turning
        // the profiler back on would never take effect.
        Boolean requested = pendingEnabled;

        if (requested != null)
        {
            pendingEnabled = null;

            if (requested != enabled)
            {
                enabled = requested;

                if (!enabled)
                {
                    hudLines = List.of();
                    resetSession();
                }
            }
        }

        if (!enabled)
        {
            return;
        }
        Object world = MinecraftClient.getInstance().world;
        if (!sessionStarted || pipelineChanged || sessionWorld == null || sessionWorld.get() != world)
        {
            resetSession();
            sessionStarted = true;
            pipelineChanged = false;
            sessionWorld = new WeakReference<>(world);
            ProfileCapture.start(DETAILED, SWEEP);
        }
        frameNo += 1;
        frameTimings.begin(frameNo);
        long nowNs = System.nanoTime();
        if (lastFrameTickNs != 0L)
        {
            cpuWindow.computeIfAbsent("frame", key -> new TimingStats()).add(nowNs - lastFrameTickNs);
            ProfileCapture.sample("cpu", frameNo - 1, "frame", nowNs - lastFrameTickNs);
        }
        lastFrameTickNs = nowNs;
        drainCompleted();
        if (SWEEP) VlSweep.tick(frameNo);
        maybeFlushWindow();
        // AFTER the flush: this tick's bake counters land after the flush too,
        // so the window's tick count and its bake-frame span coincide exactly
        // (incrementing before the flush over-counted the first window by one).
        windowFrames += 1;
    }

    public static boolean detailedTimings()
    {
        return enabled && DETAILED;
    }

    public static void invalidateFrame()
    {
        if (enabled) frameTimings.invalidate(frameNo);
    }

    private static void resetSession()
    {
        for (Pending p : pending)
        {
            GL33C.glDeleteQueries(p.query);
            allocatedQueries--;
        }
        pending.clear();
        frameTimings.clear();
        window.clear();
        cpuWindow.clear();
        counters.clear();
        completedGpuFrames = rejectedGpuFrames = windowFrames = 0;
        droppedSamples = externalTimerSkips = 0;
        lastFrameTickNs = windowStart = 0L;
        lastEvictionCount = lastEvictedKb = -1L;
        sessionStarted = false;
        sessionWorld = null;
        hudLines = List.of();
        ProfileCapture.close();
        if (SWEEP) VlSweep.reset();
    }

    /**
     * Core-side ShadowBakeProbe.section: close the current bake bracket and
     * open the named sibling. Fired at the bakeInner seams while the mixin's
     * bake-head bracket is active, so the whole bake stays covered by
     * consecutive sibling brackets (GL_TIME_ELAPSED cannot nest). If the head
     * bracket never opened this frame (F3 timer active, pool exhausted), both
     * halves degrade to the same no-op/skip and the segment samples drop
     * consistently.
     */
    public static void switchPass(String name)
    {
        if (!enabled)
        {
            return;
        }
        endPass();
        beginPass(name);
    }

    /** Render-thread CPU bracket (a nanoTime delta) accumulated into the
     *  window; printed on the "[irlite] cpu:" line. For no-GL work only — GPU
     *  brackets go through beginPass/endPass. */
    public static void cpuSample(String name, long ns)
    {
        if (!enabled)
        {
            return;
        }
        cpuWindow.computeIfAbsent(name, key -> new TimingStats()).add(ns);
        ProfileCapture.sample("cpu", frameNo, name, ns);
    }

    /** Core-side ShadowBakeProbe.counter: accumulate into the 1-second window. */
    public static void counter(String key, int amount)
    {
        if (!enabled)
        {
            return;
        }
        counters.computeIfAbsent(key, k -> new long[1])[0] += amount;
        ProfileCapture.sample("work", frameNo, key, amount);
    }

    /** Opens a GL_TIME_ELAPSED bracket. No-ops (and drops the sample) if a
     *  bracket is already active — timer queries cannot nest. */
    public static void beginPass(String name)
    {
        if (!enabled)
        {
            return;
        }
        if (activeQuery != -1)
        {
            droppedSamples += 1;
            frameTimings.invalidate(frameNo);
            return;
        }
        // Vanilla GlTimer (F3 GPU% / debug recorder) owns GL_TIME_ELAPSED for the
        // whole frame — a nested begin would GL_INVALID_OPERATION, and ending it
        // would kill the vanilla query and poison our FIFO. Skip those frames.
        if (GL33C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL33C.GL_CURRENT_QUERY) != 0)
        {
            externalTimerSkips += 1;
            frameTimings.invalidate(frameNo);
            return;
        }
        int query = allocQuery();
        if (query == -1)
        {
            droppedSamples += 1;
            frameTimings.invalidate(frameNo);
            return;
        }
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        if (GL33C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL33C.GL_CURRENT_QUERY) != query)
        {
            // The begin didn't take (an external timer raced in) — recycle the id
            // and drop the sample instead of tracking a query that never began.
            freeQueries.addLast(query);
            droppedSamples += 1;
            frameTimings.invalidate(frameNo);
            return;
        }
        activeQuery = query;
        activePass = name;
        frameTimings.issued(frameNo);
    }

    /** Closes the currently active bracket, if any. */
    public static void endPass()
    {
        if (!enabled || activeQuery == -1)
        {
            return;
        }
        if (GL33C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL33C.GL_CURRENT_QUERY) != activeQuery)
        {
            // Never close a query owned by another profiler.
            GL33C.glDeleteQueries(activeQuery);
            allocatedQueries--;
            droppedSamples++;
            frameTimings.resolved(frameNo, activePass, -1L);
            activeQuery = -1;
            activePass = null;
            return;
        }
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        pending.addLast(new Pending(activeQuery, activePass, frameNo));
        activeQuery = -1;
        activePass = null;
    }

    /**
     * LightCollector.collect, right after the user-config VlGlobals push: while
     * the sweep is measuring a non-baseline config it re-issues the push with
     * that config's flag/morph override — last write wins before the upload.
     * The next frame's ordinary push restores the user values automatically.
     */
    public static void overrideVlGlobals()
    {
        if (!enabled || !SWEEP)
        {
            return;
        }
        VlSweep.overrideVlGlobals();
    }

    /* ---- query pool -------------------------------------------------------- */

    private static int allocQuery()
    {
        Integer free = freeQueries.pollFirst();
        if (free != null)
        {
            return free;
        }
        if (allocatedQueries >= POOL_LIMIT)
        {
            return -1;
        }
        allocatedQueries += 1;
        return GL33C.glGenQueries();
    }

    private static void drainCompleted()
    {
        Pending head;
        while ((head = pending.peekFirst()) != null)
        {
            if (GL33C.glGetQueryObjecti(head.query, GL33C.GL_QUERY_RESULT_AVAILABLE) == 0)
            {
                // Self-heal: a head whose begin silently failed would report
                // "unavailable" forever and dam the whole FIFO — evict it and
                // keep draining the valid results queued behind it.
                if (frameNo - head.frame > STUCK_FRAMES)
                {
                    pending.pollFirst();
                    GL33C.glDeleteQueries(head.query);
                    allocatedQueries -= 1;
                    frameTimings.resolved(head.frame, head.pass, -1L);
                    System.out.println("[irlite] gpu: evicted stuck timer query for pass " + head.pass);
                    continue;
                }
                break;
            }
            long ns = GL33C.glGetQueryObjecti64(head.query, GL33C.GL_QUERY_RESULT);
            pending.pollFirst();
            freeQueries.addLast(head.query);
            frameTimings.resolved(head.frame, head.pass, ns);
        }
    }

    private static void recordFrame(long frame, Map<String, Long> nanos)
    {
        long bake = 0L;
        boolean hasBake = false;
        for (Map.Entry<String, Long> e : nanos.entrySet())
        {
            if (e.getKey().startsWith(BAKE_PREFIX))
            {
                bake += e.getValue();
                hasBake = true;
            }
        }
        if (hasBake) nanos.put("bake", bake);
        for (Map.Entry<String, TimingStats> e : window.entrySet())
        {
            if (!nanos.containsKey(e.getKey())) e.getValue().add(0L);
        }
        for (Map.Entry<String, Long> e : nanos.entrySet())
        {
            TimingStats stats = window.get(e.getKey());
            if (stats == null)
            {
                stats = new TimingStats();
                stats.addZeros(completedGpuFrames);
                window.put(e.getKey(), stats);
            }
            stats.add(e.getValue());
            ProfileCapture.sample("gpu", frame, e.getKey(), e.getValue());
        }
        completedGpuFrames++;
        ProfileCapture.sample("frame", frame, "gpu-complete", 1L);
        if (SWEEP && nanos.containsKey(PASS_VL)) VlSweep.addSample(frame, nanos.get(PASS_VL));
    }

    /* ---- 1-second window + HUD -------------------------------------------- */

    private static void maybeFlushWindow()
    {
        long now = System.nanoTime();
        if (windowStart == 0L)
        {
            windowStart = now;
            return;
        }
        if (now - windowStart < WINDOW_NS)
        {
            return;
        }

        List<Map.Entry<String, TimingStats>> entries = new ArrayList<>(window.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().sumNs, a.getValue().sumNs));

        StringBuilder line = new StringBuilder("[irlite] gpu:");
        List<String> hud = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, TimingStats> e : entries)
        {
            TimingStats s = e.getValue();
            String cell = timingCell(e.getKey(), s);
            line.append(shown == 0 ? " " : " | ").append(cell);
            if (hud.size() < 14)
            {
                hud.add(hudCell(e.getKey(), s));
            }
            shown += 1;
        }
        if (shown == 0)
        {
            line.append(" (no samples — shaders off or no passes yet)");
        }
        line.append(" | complete frames ").append(completedGpuFrames)
            .append(" | rejected frames ").append(rejectedGpuFrames);
        if (droppedSamples > 0)
        {
            line.append(" | dropped ").append(droppedSamples);
            droppedSamples = 0;
        }
        if (externalTimerSkips > 0)
        {
            line.append(" | skipped ").append(externalTimerSkips).append(" (F3 timer active)");
            externalTimerSkips = 0;
        }
        System.out.println(line);

        // CPU line: frame time + the no-GL render-thread brackets, so a frame
        // far slower than the GPU pass sum is attributable at a glance.
        if (!cpuWindow.isEmpty())
        {
            List<Map.Entry<String, TimingStats>> cpuEntries = new ArrayList<>(cpuWindow.entrySet());
            cpuEntries.sort((a, b) -> Long.compare(b.getValue().sumNs, a.getValue().sumNs));
            StringBuilder cpuLine = new StringBuilder("[irlite] cpu:");
            boolean firstCell = true;
            for (Map.Entry<String, TimingStats> e : cpuEntries)
            {
                TimingStats s = e.getValue();
                String cell = timingCell(e.getKey(), s);
                cpuLine.append(firstCell ? " " : " | ").append(cell);
                if (hud.size() < 20)
                {
                    hud.add(hudCell(e.getKey(), s));
                }
                firstCell = false;
            }
            // Census: lights the shader loop actually paid for last frame
            // (post-cap), vs merely registered. Gates the mask-redesign call.
            int uploaded = org.qualet.irl.light.LightRegistry.getUploadedCount();
            String uploadedCell = "uploaded " + uploaded;
            cpuLine.append(" | ").append(uploadedCell);
            hud.add(uploadedCell);
            System.out.println(cpuLine);
            cpuWindow.clear();
        }

        // Second line: the bake work counters (window sums + the frame count
        // to read per-frame rates off), mirrored onto the HUD in packed rows.
        if (!counters.isEmpty())
        {
            List<String> keys = new ArrayList<>(counters.keySet());
            Collections.sort(keys);
            StringBuilder bakeLine = new StringBuilder("[irlite] bake:");
            List<String> cells = new ArrayList<>(keys.size());
            for (String key : keys)
            {
                String cell = key + " " + counters.get(key)[0];
                bakeLine.append(cells.isEmpty() ? " " : " | ").append(cell);
                cells.add(cell);
            }
            bakeLine.append(" | ").append(windowFrames).append(" frames");
            System.out.println(bakeLine);

            for (int i = 0; i < cells.size(); i += 4)
            {
                hud.add(String.join(" | ", cells.subList(i, Math.min(i + 4, cells.size()))));
            }
            hud.add(windowFrames + " frames");
            counters.clear();
        }
        windowFrames = 0;

        String vram = vramLine();
        if (vram != null)
        {
            System.out.println("[irlite] vram: " + vram);
            hud.add(vram);
        }

        hudLines = hud;
        window.clear();
        completedGpuFrames = rejectedGpuFrames = 0;
        ProfileCapture.flush();
        windowStart = now;
    }

    private static String timingCell(String name, TimingStats stats)
    {
        return String.format(Locale.ROOT, "%s avg %.2f p50 %.2f p95 %.2f max %.2f ms (n=%d)",
            name, stats.avgMs(), stats.medianMs(), stats.p95Ms(), stats.maxNs / 1_000_000D, stats.samples);
    }

    private static String hudCell(String name, TimingStats stats)
    {
        return String.format(Locale.ROOT, "%s %.2f | p50 %.2f p95 %.2f ms",
            name, stats.avgMs(), stats.medianMs(), stats.p95Ms());
    }

    /** One-line VRAM/eviction snapshot via GL_NVX_gpu_memory_info, or null when
     *  the extension is absent (non-NVIDIA). Values arrive in KiB; the eviction
     *  count/size deltas are per window — a climbing delta while the filter
     *  passes slow down at constant work is residency thrash, caught red-handed. */
    private static String vramLine()
    {
        if (nvxMemoryInfo == null)
        {
            nvxMemoryInfo = org.lwjgl.opengl.GL.getCapabilities().GL_NVX_gpu_memory_info;
        }
        if (!nvxMemoryInfo)
        {
            return null;
        }
        long dedicatedKb = GL33C.glGetInteger(NVX_DEDICATED_VIDMEM) & 0xffffffffL;
        long totalKb = GL33C.glGetInteger(NVX_TOTAL_AVAILABLE) & 0xffffffffL;
        long freeKb = GL33C.glGetInteger(NVX_CURRENT_AVAILABLE) & 0xffffffffL;
        long evictions = GL33C.glGetInteger(NVX_EVICTION_COUNT) & 0xffffffffL;
        long evictedKb = GL33C.glGetInteger(NVX_EVICTED_MEMORY) & 0xffffffffL;
        long dEvictions = lastEvictionCount < 0 ? 0 : evictions - lastEvictionCount;
        long dEvictedKb = lastEvictedKb < 0 ? 0 : evictedKb - lastEvictedKb;
        lastEvictionCount = evictions;
        lastEvictedKb = evictedKb;
        return String.format(Locale.ROOT,
            "free %d/%d MiB (total avail %d) | evictions %d (+%d), evicted %d MiB (+%d)",
            freeKb >> 10, dedicatedKb >> 10, totalKb >> 10,
            evictions, dEvictions, evictedKb >> 10, dEvictedKb >> 10);
    }

    /** HudRenderCallback, registered unconditionally in IrliteClient — so it
     *  carries its own gate now instead of relying on never being hooked up. */
    public static void renderHud(DrawContext ctx)
    {
        if (!enabled)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null)
        {
            return;
        }
        int y = 4;
        List<String> lines = hudLines;
        String sweepStatus = SWEEP ? VlSweep.statusLine() : null;
        if (sweepStatus != null)
        {
            ctx.drawText(mc.textRenderer, sweepStatus, 4, y, 0xFFFFD080, true);
            y += 10;
        }
        for (String lineText : lines)
        {
            ctx.drawText(mc.textRenderer, lineText, 4, y, 0xFFE0E0E0, true);
            y += 10;
        }
    }

    /* ---- output helpers (also used by VlSweep) ----------------------------- */

    static void log(String message)
    {
        System.out.println("[irlite] vl-sweep: " + message);
    }

    static void chat(String message)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.world != null && mc.inGameHud != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.literal(message));
        }
    }
}
