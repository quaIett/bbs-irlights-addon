package qualet.irlite.client.diag;

import java.util.Arrays;

/** Exact short-window statistics; p95 uses the nearest-rank definition. */
final class TimingStats
{
    long sumNs;
    long maxNs;
    int samples;
    private long[] values = new long[128];
    private boolean sorted;

    void add(long ns)
    {
        if (samples == values.length) values = Arrays.copyOf(values, values.length * 2);
        values[samples++] = ns;
        sorted = false;
        sumNs += ns;
        maxNs = Math.max(maxNs, ns);
    }

    void addZeros(int count)
    {
        for (int i = 0; i < count; i++) add(0L);
    }

    double avgMs()
    {
        return samples == 0 ? 0D : sumNs / 1_000_000D / samples;
    }

    double medianMs()
    {
        if (samples == 0) return 0D;
        long[] sorted = sorted();
        int mid = samples / 2;
        return (samples % 2 == 0 ? sorted[mid - 1] / 2D + sorted[mid] / 2D : sorted[mid]) / 1_000_000D;
    }

    double p95Ms()
    {
        if (samples == 0) return 0D;
        return sorted()[(int) Math.ceil(samples * 0.95) - 1] / 1_000_000D;
    }

    private long[] sorted()
    {
        // Sample order is not part of a timing window. Sort its owned storage
        // once, then share it between console/HUD percentile reads until add().
        if (!sorted)
        {
            Arrays.sort(values, 0, samples);
            sorted = true;
        }
        return values;
    }
}
