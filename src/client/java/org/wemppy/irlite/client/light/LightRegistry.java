package org.wemppy.irlite.client.light;

/**
 * Per-frame accumulator for all collected lights, from two sources:
 *  - the scanner (ModelBlocks) at renderWorld HEAD, and
 *  - the form render-path (live actors / film replays) during world render.
 *
 * Single buffer: {@link #flush()} (called at HEAD after the scanner) packs the
 * current set into {@link LightBuffer} and clears it. Render-path registrations
 * land after the flush and are uploaded on the next frame's flush (one frame
 * stale — acceptable for moving lights). Dedup by identity keeps a light that
 * gets rendered more than once per frame from registering twice.
 */
public final class LightRegistry
{
    private static final int MAX = LightBuffer.MAX_LIGHTS;

    private static final int[] type = new int[MAX];
    private static final float[] px = new float[MAX];
    private static final float[] py = new float[MAX];
    private static final float[] pz = new float[MAX];
    private static final float[] cr = new float[MAX];
    private static final float[] cg = new float[MAX];
    private static final float[] cb = new float[MAX];
    private static final float[] intensity = new float[MAX];
    private static final float[] radius = new float[MAX];
    private static final float[] dx = new float[MAX];
    private static final float[] dy = new float[MAX];
    private static final float[] dz = new float[MAX];
    private static final float[] cosOuter = new float[MAX];
    private static final float[] cosInner = new float[MAX];
    private static final boolean[] entitiesOnly = new boolean[MAX];
    private static final long[] id = new long[MAX];

    private static int count;

    private LightRegistry()
    {}

    public static void registerPoint(float x, float y, float z, float r, float g, float b, float in, float rad, boolean eOnly, long identity)
    {
        int i = slot(identity);
        if (i < 0)
        {
            return;
        }

        type[i] = 0;
        px[i] = x; py[i] = y; pz[i] = z;
        cr[i] = r; cg[i] = g; cb[i] = b;
        intensity[i] = in; radius[i] = rad;
        dx[i] = 0F; dy[i] = 0F; dz[i] = 0F;
        cosOuter[i] = 1F; cosInner[i] = 1F;
        entitiesOnly[i] = eOnly;
    }

    public static void registerSpot(float x, float y, float z, float ndx, float ndy, float ndz, float r, float g, float b, float in, float range, float cosO, float cosI, boolean eOnly, long identity)
    {
        int i = slot(identity);
        if (i < 0)
        {
            return;
        }

        type[i] = 1;
        px[i] = x; py[i] = y; pz[i] = z;
        cr[i] = r; cg[i] = g; cb[i] = b;
        intensity[i] = in; radius[i] = range;
        dx[i] = ndx; dy[i] = ndy; dz[i] = ndz;
        cosOuter[i] = cosO; cosInner[i] = cosI;
        entitiesOnly[i] = eOnly;
    }

    /** Returns the slot for this identity (existing = overwrite, else a new one), or -1 if full. */
    private static int slot(long identity)
    {
        for (int i = 0; i < count; i++)
        {
            if (id[i] == identity)
            {
                return i;
            }
        }

        if (count >= MAX)
        {
            return -1;
        }

        id[count] = identity;
        return count++;
    }

    /** Pack the accumulated set into the GPU buffer and reset for the next frame. */
    public static void flush()
    {
        LightBuffer.begin();

        for (int i = 0; i < count; i++)
        {
            if (type[i] == 0)
            {
                LightBuffer.addPoint(px[i], py[i], pz[i], cr[i], cg[i], cb[i], intensity[i], radius[i], entitiesOnly[i]);
            }
            else
            {
                LightBuffer.addSpot(px[i], py[i], pz[i], dx[i], dy[i], dz[i], cr[i], cg[i], cb[i], intensity[i], radius[i], cosOuter[i], cosInner[i], entitiesOnly[i]);
            }
        }

        LightBuffer.upload();
        count = 0;
    }
}
