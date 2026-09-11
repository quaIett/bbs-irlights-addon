package qualet.irlite.client.diag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.qualet.irl.light.VlGlobalsBuffer;
import qualet.irlite.IrliteConfig;

/**
 * Level-2 differential sweep of the VL march (deferred2) cost, unlocked by the
 * zero-recompile VlGlobals UBO: the flags are per-frame runtime data, so the
 * sweep can cycle configs frame-by-frame with no shader reload and attribute
 * the march cost to shadows / noise / morph / Hi-Z / cluster-cull.
 *
 * <p>Timeline (frames, driven by {@link VlProfiler#frameTick}): wait until the
 * deferred2 pass produces its first timer sample (shaders on, VL compiled in),
 * warm up, then run each config for {@link #SPAN} frames — the first
 * {@link #SKIP} frames of each config are discarded (query latency + caches).
 * Samples are attributed to configs by the frame the query was ISSUED on, so
 * the 2-3 frame readback lag cannot smear a config into its neighbour.</p>
 *
 * <p>The override rides the existing per-frame push: LightCollector re-pushes
 * the user config every frame and the sweep re-issues the push with this
 * config's transform right after it (last write wins before the UBO upload),
 * so simply stopping restores the user values on the very next frame.</p>
 */
final class VlSweep
{
    private static final int WARMUP_FRAMES = 240;
    private static final int SPAN = 120;
    private static final int SKIP = 20;
    private static final int MIN_SAMPLES = 10;

    /** Extra frames after the last config window so its in-flight timer queries
     *  (2-3 frame readback lag) drain before the report; no override is applied
     *  during the grace, so the user config is already restored. */
    private static final int DRAIN_GRACE = 10;

    /** Frames without a single deferred2 sample during RUN after which the pass
     *  is presumed gone (shaders off / F3+R to a non-VL pack) — the partial run
     *  is discarded and the sweep re-arms instead of burning its one shot. */
    private static final int STALL_FRAMES = 120;

    /** UBO flag bits (VlGlobalsBuffer contract): 0 shadows, 1 noise, 2 blue-noise,
     *  3 temporal dither, 4 cluster cull, 5 Hi-Z skip, 6 bilateral upsample
     *  (composite1-side, not part of the deferred2 march this sweep measures). */
    private static final int BIT_SHADOWS = 1;
    private static final int BIT_NOISE = 2;
    private static final int BIT_CULL = 16;
    private static final int BIT_HIZ = 32;

    private static final Config[] CONFIGS = {
        new Config("baseline (all on)", ~0, false, false),
        new Config("shadows OFF", ~BIT_SHADOWS, false, false),
        new Config("noise OFF", ~BIT_NOISE, false, false),
        new Config("morph 0", ~0, true, false),
        new Config("Hi-Z OFF", ~BIT_HIZ, false, true),
        new Config("cluster cull OFF", ~BIT_CULL, false, true),
        new Config("bare march (no shadows/noise/morph)", ~(BIT_SHADOWS | BIT_NOISE), true, false),
    };

    private enum Phase
    {
        WAIT_VL, WARMUP, RUN, DONE
    }

    private static Phase phase = Phase.WAIT_VL;
    private static boolean vlSeen;
    private static boolean waitLogged;
    private static long warmupStart;
    private static long runStart;
    private static long currentFrame;
    private static long lastSampleFrame;

    private static final List<List<Long>> samples = new ArrayList<>();

    static
    {
        for (int i = 0; i < CONFIGS.length; i++)
        {
            samples.add(new ArrayList<>());
        }
    }

    private VlSweep()
    {}

    static void reset()
    {
        phase = Phase.WAIT_VL;
        vlSeen = waitLogged = false;
        warmupStart = runStart = currentFrame = lastSampleFrame = 0L;
        for (List<Long> list : samples) list.clear();
    }

    private static final class Config
    {
        final String label;
        final int flagMask;
        final boolean zeroMorph;
        /** true = disabling this config's bit turns a pure-perf optimization off,
         *  so the report phrases its delta as "saves" instead of "costs". */
        final boolean optimization;

        Config(String label, int flagMask, boolean zeroMorph, boolean optimization)
        {
            this.label = label;
            this.flagMask = flagMask;
            this.zeroMorph = zeroMorph;
            this.optimization = optimization;
        }
    }

    /* ---- state machine (called once per frame from VlProfiler.frameTick) --- */

    static void tick(long frameNo)
    {
        currentFrame = frameNo;
        switch (phase)
        {
            case WAIT_VL ->
            {
                if (!waitLogged)
                {
                    waitLogged = true;
                    VlProfiler.log("armed — waiting for the first " + VlProfiler.PASS_VL + " GPU sample (world + shaders + VL required)");
                }
                if (vlSeen)
                {
                    phase = Phase.WARMUP;
                    warmupStart = frameNo;
                    VlProfiler.log("VL pass detected, warming up " + WARMUP_FRAMES + " frames");
                }
            }
            case WARMUP ->
            {
                if (frameNo - warmupStart >= WARMUP_FRAMES)
                {
                    phase = Phase.RUN;
                    runStart = frameNo;
                    lastSampleFrame = frameNo;
                    VlProfiler.log("sweep started: " + CONFIGS.length + " configs x " + SPAN + " frames");
                    VlProfiler.chat("[IRLite] VL sweep started (" + CONFIGS.length + " configs, ~"
                        + (CONFIGS.length * SPAN) + " frames) — keep the camera still");
                }
            }
            case RUN ->
            {
                if (frameNo - lastSampleFrame > STALL_FRAMES)
                {
                    phase = Phase.WAIT_VL;
                    vlSeen = false;
                    runStart = 0;
                    for (List<Long> list : samples)
                    {
                        list.clear();
                    }
                    VlProfiler.log("VL pass lost mid-sweep — discarding partial run, re-arming");
                }
                else if (frameNo - runStart >= (long) CONFIGS.length * SPAN + DRAIN_GRACE)
                {
                    phase = Phase.DONE;
                    report();
                }
            }
            case DONE ->
            {
            }
        }
    }

    /** Attribution by issue-frame; -1 = not measuring (warmup/skip/grace window). */
    private static int configIndexFor(long frame)
    {
        if (runStart == 0 || frame < runStart)
        {
            return -1;
        }
        long offset = frame - runStart;
        int idx = (int) (offset / SPAN);
        if (idx >= CONFIGS.length)
        {
            return -1;
        }
        return offset % SPAN >= SKIP ? idx : -1;
    }

    /** VlProfiler.record: a deferred2 timer sample landed (ns), issued on {@code frame}. */
    static void addSample(long frame, long ns)
    {
        vlSeen = true;
        if (phase != Phase.RUN)
        {
            return;
        }
        lastSampleFrame = currentFrame;
        int idx = configIndexFor(frame);
        if (idx >= 0)
        {
            samples.get(idx).add(ns);
        }
    }

    /** Re-issues the VlGlobals push with the active config's override applied.
     *  Arg list mirrors LightCollector.collect's push — keep the two in sync. */
    static void overrideVlGlobals()
    {
        if (phase != Phase.RUN)
        {
            return;
        }
        long offset = currentFrame - runStart;
        int idx = (int) (offset / SPAN);
        if (idx <= 0 || idx >= CONFIGS.length)
        {
            return;
        }
        Config config = CONFIGS[idx];
        int userFlags = (IrliteConfig.vlShadowsLive() ? 1 : 0) | (IrliteConfig.vlNoiseLive() ? 2 : 0)
            | (IrliteConfig.vlBlueNoise() ? 4 : 0) | (IrliteConfig.vlDitherTemporal() ? 8 : 0)
            | (IrliteConfig.vlClusterCull() ? 16 : 0) | (IrliteConfig.vlShadowHiz() ? 32 : 0)
            | (qualet.irlite.client.light.LightCollector.VL_BILATERAL ? 64 : 0);
        VlGlobalsBuffer.set(
            IrliteConfig.vlIntensity(),
            IrliteConfig.vlMaxDist(),
            IrliteConfig.vlTipBoost(),
            IrliteConfig.vlTipRadius(),
            IrliteConfig.vlNoiseAmount(),
            IrliteConfig.vlNoiseScale(),
            IrliteConfig.vlNoiseSpeed(),
            config.zeroMorph ? 0F : IrliteConfig.vlNoiseMorph(),
            IrliteConfig.vlSteps(),
            IrliteConfig.vlShadowStride(),
            IrliteConfig.vlNoiseStride(),
            userFlags & config.flagMask
        );
    }

    /** One-line live status for the HUD; null when nothing interesting is happening. */
    static String statusLine()
    {
        return switch (phase)
        {
            case WAIT_VL -> "vl-sweep: waiting for VL pass";
            case WARMUP -> String.format(Locale.ROOT, "vl-sweep: warmup %d/%d",
                currentFrame - warmupStart, WARMUP_FRAMES);
            case RUN ->
            {
                long offset = currentFrame - runStart;
                if (offset >= (long) CONFIGS.length * SPAN)
                {
                    yield "vl-sweep: draining";
                }
                int idx = Math.min((int) (offset / SPAN), CONFIGS.length - 1);
                yield String.format(Locale.ROOT, "vl-sweep: %d/%d %s (%d/%d)",
                    idx + 1, CONFIGS.length, CONFIGS[idx].label, offset % SPAN, SPAN);
            }
            case DONE -> null;
        };
    }

    /* ---- report ------------------------------------------------------------ */

    private static double medianMs(int idx)
    {
        List<Long> list = samples.get(idx);
        if (list.size() < MIN_SAMPLES)
        {
            return Double.NaN;
        }
        List<Long> sorted = new ArrayList<>(list);
        Collections.sort(sorted);
        int n = sorted.size();
        long median = (n % 2 == 1) ? sorted.get(n / 2)
            : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2L;
        return median / 1_000_000D;
    }

    private static void report()
    {
        double base = medianMs(0);
        List<String> lines = new ArrayList<>();
        lines.add("===== VL differential sweep — " + VlProfiler.PASS_VL + " GPU median =====");
        for (int i = 0; i < CONFIGS.length; i++)
        {
            double ms = medianMs(i);
            int n = samples.get(i).size();
            if (Double.isNaN(ms))
            {
                lines.add(String.format(Locale.ROOT, "%-36s insufficient samples (n=%d)", CONFIGS[i].label, n));
                continue;
            }
            String delta = "";
            if (i > 0 && !Double.isNaN(base))
            {
                // OFF-configs: base - off = what the feature costs.
                // Hi-Z / cluster cull: off - base = what the optimization saves.
                boolean isSkipOpt = CONFIGS[i].optimization;
                double d = isSkipOpt ? ms - base : base - ms;
                String verb = isSkipOpt ? "saves" : "costs";
                double pct = base > 0.0005 ? d / base * 100D : 0D;
                delta = String.format(Locale.ROOT, "  -> %s %.3f ms (%.1f%%)", verb, d, pct);
            }
            lines.add(String.format(Locale.ROOT, "%-36s %8.3f ms (n=%d)%s", CONFIGS[i].label, ms, n, delta));
        }
        lines.add("user config restored; numbers are medians over ~" + (SPAN - SKIP) + " frames each");

        for (String line : lines)
        {
            VlProfiler.log(line);
        }
        for (String line : lines)
        {
            VlProfiler.chat("[IRLite] " + line);
        }
    }
}
