package qualet.irlite.client.diag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/** Joins asynchronous query results by issue frame, never by readback time. */
final class FrameGpuTimings
{
    private static final int MAX_PENDING_FRAMES = 128;
    private final Map<Long, Frame> frames = new LinkedHashMap<>();
    private final BiConsumer<Long, Map<String, Long>> completed;
    private final LongConsumer rejected;

    private static final class Frame
    {
        final Map<String, Long> nanos = new HashMap<>();
        int pending;
        boolean sealed;
        boolean invalid;
    }

    FrameGpuTimings(BiConsumer<Long, Map<String, Long>> completed, LongConsumer rejected)
    {
        this.completed = completed;
        this.rejected = rejected;
    }

    void begin(long frame)
    {
        if (frames.size() >= MAX_PENDING_FRAMES)
        {
            long oldest = frames.keySet().iterator().next();
            frames.remove(oldest);
            rejected.accept(oldest);
        }
        frames.put(frame, new Frame());
    }

    void issued(long frame)
    {
        Frame f = frames.get(frame);
        if (f != null) f.pending++;
    }

    void invalidate(long frame)
    {
        Frame f = frames.get(frame);
        if (f != null) f.invalid = true;
    }

    void resolved(long frame, String pass, long nanos)
    {
        Frame f = frames.get(frame);
        if (f == null) return; // retired session or bounded eviction
        if (nanos < 0) f.invalid = true;
        else f.nanos.merge(pass, nanos, Long::sum);
        f.pending--;
        finish(frame, f);
    }

    void seal(long frame)
    {
        Frame f = frames.get(frame);
        if (f == null) return;
        f.sealed = true;
        finish(frame, f);
    }

    private void finish(long frame, Frame f)
    {
        if (!f.sealed || f.pending != 0) return;
        frames.remove(frame);
        if (f.invalid) rejected.accept(frame);
        else if (!f.nanos.isEmpty()) completed.accept(frame, f.nanos);
    }

    void clear()
    {
        frames.clear();
    }
}
