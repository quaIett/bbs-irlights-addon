package qualet.irlite.client.light.cookie;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.qualet.irl.light.CookieArrayBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@code GL_TEXTURE_2D_ARRAY} of grayscale gobo/cookie masks — one layer per
 * loaded image — bound into every Iris program as {@code irl_cookieArray} (see
 * {@code ProgramSamplersBuilderMixin} + {@code SamplerBindingCubeArrayMixin}).
 *
 * <p>The spot shader projects a fragment into the light's frustum and multiplies
 * the light by the sampled luminance (white = pass, black = block) — a projected
 * mask, NOT a shadow: no depth, no bake, one texture tap.</p>
 *
 * <p>Unlike the standalone editor (which reads a config folder), the addon sources
 * pixels from BBS-managed textures: a spotlight's cookie field is a
 * {@link Link} picked with BBS's native texture picker, and {@link #resolve(Link)}
 * pulls the asset bytes via {@link BBSMod#getProvider()}. The array texture, the
 * guarded upload and the STB decode/resample live in {@link CookieArrayBase}; this
 * subclass keeps the BBS asset source plus the LRU cache.</p>
 */
public final class CookieArray extends CookieArrayBase
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    /** Per-layer square resolution; loaded images are resampled to this. */
    public static final int RES = CookieArrayBase.RES;
    /** Array depth: distinct cookies resident at once. When full, the least-recently-
     *  used layer is evicted and reused, so long keyframe flipbooks (image_1..image_N
     *  animated by the film's LINK interpolation) keep loading past the cap. */
    public static final int MAX_LAYERS = 32;

    private static final CookieArray INSTANCE = new CookieArray();

    /** link string -> array layer (successfully loaded cookies only). */
    private final Map<String, Integer> layerByKey = new HashMap<>();
    /** Last resolve() stamp by resident layer; avoids another map lookup and
     *  boxed Long on every successful hit. Only layerByKey entries are live. */
    private final long[] lastUse = new long[MAX_LAYERS];
    /** links whose asset failed to read/decode, so a broken image isn't re-decoded every frame. */
    private final Set<String> failed = new HashSet<>();
    private long useCounter = 0;
    private int nextLayer = 0;

    private CookieArray()
    {
        super(MAX_LAYERS);
    }

    /** Lazy — 0 until the first cookie is uploaded (no VRAM if unused). */
    public static int getGlTextureId()
    {
        return INSTANCE.textureId();
    }

    /** Resolve a cookie texture link to its array layer, loading on first use.
     *  Render thread only (uploads to GL). Returns -1 for a null/empty link or a
     *  failed load (failures are cached per link). A full array evicts the
     *  least-recently-used cookie instead of refusing, so keyframe flipbook
     *  sequences longer than {@link #MAX_LAYERS} keep animating. */
    public static int resolve(Link link)
    {
        return INSTANCE.resolve0(link);
    }

    private int resolve0(Link link)
    {
        if (link == null)
        {
            return -1;
        }
        String key = link.toString();
        if (key.isEmpty())
        {
            return -1;
        }
        Integer cached = layerByKey.get(key);
        if (cached != null)
        {
            lastUse[cached] = ++useCounter;
            return cached;
        }
        if (failed.contains(key))
        {
            return -1;
        }

        ByteBuffer pixels = decode(link, key);
        if (pixels == null)
        {
            failed.add(key);
            return -1;
        }
        try
        {
            int layer = (nextLayer < MAX_LAYERS) ? nextLayer++ : evictLru();
            uploadLayer(pixels, layer);
            layerByKey.put(key, layer);
            lastUse[layer] = ++useCounter;
            LOG.debug("Cookie loaded '{}' -> layer {}", key, layer);
            return layer;
        }
        finally
        {
            MemoryUtil.memFree(pixels);
        }
    }

    /** Drop the least-recently-used cookie and hand its layer to the caller. Only
     *  called with a full array, which always has (way) more layers than there are
     *  cookies used in a single frame — the evicted key is never in active use. */
    private int evictLru()
    {
        String lruKey = null;
        long lruStamp = Long.MAX_VALUE;
        for (Map.Entry<String, Integer> e : layerByKey.entrySet())
        {
            long stamp = lastUse[e.getValue()];
            if (stamp < lruStamp)
            {
                lruStamp = stamp;
                lruKey = e.getKey();
            }
        }
        int layer = layerByKey.remove(lruKey);
        LOG.debug("Cookie evicted '{}' (array full) -> reusing layer {}", lruKey, layer);
        return layer;
    }

    /** Read + decode + resample the asset to a RES*RES single-channel buffer.
     *  Returns null (and logs) on failure; the caller owns/frees the buffer. */
    private ByteBuffer decode(Link link, String key)
    {
        byte[] raw;
        try (InputStream in = BBSMod.getProvider().getAsset(link))
        {
            raw = in.readAllBytes();
        }
        catch (Exception e)
        {
            LOG.warn("Cookie asset read failed: {}", key, e);
            return null;
        }

        ByteBuffer pixels = CookieArrayBase.decode(raw);
        if (pixels == null)
        {
            LOG.warn("Cookie decode failed: {} ({})", key, STBImage.stbi_failure_reason());
        }
        return pixels;
    }

    /** Forget all loaded cookies and free the GL texture, so a next {@link #resolve}
     *  reloads from the BBS assets (picks up an edited texture). */
    public static void reload()
    {
        INSTANCE.reload0();
    }

    private void reload0()
    {
        layerByKey.clear();
        Arrays.fill(lastUse, 0L);
        failed.clear();
        useCounter = 0;
        nextLayer = 0;
        deleteTexture();
    }

    public static void delete()
    {
        INSTANCE.deleteTexture();
    }
}
