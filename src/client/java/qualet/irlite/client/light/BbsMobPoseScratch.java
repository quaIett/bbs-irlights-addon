package qualet.irlite.client.light;

import net.minecraft.client.model.ModelPart;

import java.util.Arrays;

/** Reuses snapshots, but captures every live part before each evaluation. */
final class BbsMobPoseScratch
{
    private BbsMobPoseScratch nested;
    private boolean active;
    private PartState[] states = new PartState[0];
    private int captured;

    BbsMobPoseScratch acquire()
    {
        if (active)
        {
            if (nested == null) nested = new BbsMobPoseScratch();
            return nested.acquire();
        }
        active = true;
        return this;
    }

    void capture(ModelPart part)
    {
        // Traversal/capture finishes before the caller changes any model state.
        if (captured == states.length)
        {
            PartState[] grown = Arrays.copyOf(states, states.length * 2 + 8);
            for (int i = states.length; i < grown.length; i++) grown[i] = new PartState();
            states = grown;
        }
        states[captured].capture(part);
        captured++;
    }

    void release()
    {
        while (captured > 0) states[--captured].restore();
        active = false;
    }

    /** BBS cleanup can fail through a missing accessor or optional integration.
     * Always unwind every stage, preserving the original evaluation failure. */
    void release(Cleanup cleanup, Throwable evaluationFailure)
    {
        Throwable failure = evaluationFailure;
        try { cleanup.clearPose(); }
        catch (RuntimeException | Error error) { failure = accumulate(failure, error); }
        try { cleanup.clearOverlay(); }
        catch (RuntimeException | Error error) { failure = accumulate(failure, error); }
        try { release(); }
        catch (RuntimeException | Error error) { failure = accumulate(failure, error); }
        try { cleanup.clearCache(); }
        catch (RuntimeException | Error error) { failure = accumulate(failure, error); }
        if (evaluationFailure == null)
        {
            if (failure instanceof RuntimeException error) throw error;
            if (failure instanceof Error error) throw error;
        }
    }

    private static Throwable accumulate(Throwable first, Throwable next)
    {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    interface Cleanup
    {
        void clearPose();
        void clearOverlay();
        void clearCache();
    }

    private static final class PartState
    {
        ModelPart part;
        float x, y, z, pitch, yaw, roll, sx, sy, sz;
        boolean visible, hidden;

        void capture(ModelPart part)
        {
            // Read before retaining the reference (including on a null entry).
            x = part.pivotX; y = part.pivotY; z = part.pivotZ;
            pitch = part.pitch; yaw = part.yaw; roll = part.roll;
            sx = part.xScale; sy = part.yScale; sz = part.zScale;
            visible = part.visible; hidden = part.hidden;
            this.part = part;
        }

        void restore()
        {
            part.pivotX = x; part.pivotY = y; part.pivotZ = z;
            part.pitch = pitch; part.yaw = yaw; part.roll = roll;
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
            part.visible = visible; part.hidden = hidden;
            part = null;
        }
    }
}
