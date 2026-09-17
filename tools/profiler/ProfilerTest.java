package qualet.irlite.client.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure-Java regression checks; no Minecraft, GL context or extra test dependency. */
public final class ProfilerTest
{
    private static int assertions;

    public static void main(String[] args)
    {
        List<Map<String, Long>> complete = new ArrayList<>();
        List<Long> completeIds = new ArrayList<>();
        List<Long> rejected = new ArrayList<>();
        FrameGpuTimings frames = new FrameGpuTimings((id, values) -> {
            completeIds.add(id);
            complete.add(values);
        }, rejected::add);

        // 25 copies at 0.1 ms cost 2.5 ms in the frame, not 0.1 ms.
        frames.begin(1);
        for (int i = 0; i < 25; i++) frames.issued(1);
        frames.seal(1);
        for (int i = 0; i < 24; i++) frames.resolved(1, "bake-spot-copy", 100_000);
        check(complete.isEmpty(), "partial frame must not escape");
        frames.resolved(1, "bake-spot-copy", 100_000);
        check(complete.get(0).get("bake-spot-copy") == 2_500_000, "sum repeated segments");

        frames.begin(2);
        frames.issued(2);
        frames.resolved(2, "deferred2", 900_000);
        check(complete.size() == 1, "early results must wait for seal");
        frames.issued(2);
        frames.seal(2);
        frames.begin(3);
        frames.issued(3);
        frames.seal(3);
        frames.resolved(3, "deferred2", 3_000_000);
        frames.resolved(2, "deferred2", 100_000);
        check(completeIds.equals(List.of(1L, 3L, 2L)), "attribute to issue frame, including late readback");
        check(complete.get(2).get("deferred2") == 1_000_000, "do not mix adjacent frames");

        frames.begin(4);
        frames.issued(4);
        frames.invalidate(4);
        frames.seal(4);
        frames.resolved(4, "bake-spot", 1_000_000);
        check(rejected.equals(List.of(4L)) && complete.size() == 3, "dropped query rejects whole frame");
        frames.begin(5);
        frames.issued(5);
        frames.seal(5);
        frames.resolved(5, "bake-spot", -1);
        check(rejected.equals(List.of(4L, 5L)), "stuck query rejects frame");
        frames.begin(6);
        frames.seal(6);
        check(complete.size() == 3, "zero-query frame is not a zero-cost GPU sample");
        frames.begin(7);
        frames.issued(7);
        frames.seal(7);
        frames.clear();
        frames.resolved(7, "bake-spot", 99);
        check(complete.size() == 3, "old session results must not leak after reset");

        for (int i = 100; i < 229; i++)
        {
            frames.begin(i);
            frames.issued(i);
            frames.seal(i);
        }
        check(rejected.contains(100L), "pending state must be bounded");
        frames.resolved(100, "bake-spot", 99);
        check(complete.size() == 3, "evicted frame cannot be resurrected");

        TimingStats times = new TimingStats();
        for (int i = 1; i <= 100; i++) times.add(i * 1_000_000L);
        check(times.avgMs() == 50.5 && times.medianMs() == 50.5, "average and even median");
        check(times.p95Ms() == 95 && times.maxNs == 100_000_000, "nearest-rank tail");
        TimingStats intermittent = new TimingStats();
        intermittent.addZeros(3);
        intermittent.add(4_000_000);
        check(intermittent.avgMs() == 1 && intermittent.medianMs() == 0, "include frames without copy work");
        TimingStats empty = new TimingStats();
        check(empty.avgMs() == 0 && empty.medianMs() == 0 && empty.p95Ms() == 0, "empty stats");

        TimingStats reused = new TimingStats();
        reused.add(9_000_000);
        reused.add(1_000_000);
        check(reused.medianMs() == 5 && reused.p95Ms() == 9, "sort unordered samples");
        check(reused.p95Ms() == 9 && reused.medianMs() == 5, "repeat percentile reads in reverse order");
        reused.add(2_000_000);
        check(reused.medianMs() == 2 && reused.p95Ms() == 9, "invalidate sorted window after add");
        reused.addZeros(3);
        check(reused.medianMs() == 0.5 && reused.p95Ms() == 9, "invalidate sorted window after zero work");
        check(reused.avgMs() == 2 && reused.maxNs == 9_000_000 && reused.samples == 6,
            "sorting preserves aggregate statistics");

        TimingStats growing = new TimingStats();
        for (int i = 256; i >= 1; i--)
        {
            growing.add(i * 1_000_000L);
            if (i % 32 == 0) growing.p95Ms();
        }
        check(growing.medianMs() == 128.5 && growing.p95Ms() == 244,
            "grow storage after percentile reads without losing samples");
        growing.add(0);
        check(growing.medianMs() == 128 && growing.p95Ms() == 244,
            "odd median after another capacity growth");

        TimingStats large = new TimingStats();
        large.add(Long.MAX_VALUE - 2);
        large.add(Long.MAX_VALUE);
        check(large.medianMs() == ((Long.MAX_VALUE - 2) / 2D + Long.MAX_VALUE / 2D) / 1_000_000D,
            "even median remains overflow-safe");
        System.out.println("Profiler checks passed: " + assertions);
    }

    private static void check(boolean ok, String message)
    {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
}
