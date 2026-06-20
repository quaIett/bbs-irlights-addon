package qualet.irlite.client.light.cookie;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

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
 * pulls the asset bytes via {@link BBSMod#getProvider()}. Images are resampled to
 * a fixed {@link #RES} square, single channel (R8), {@code CLAMP_TO_BORDER} black
 * so everything outside the image area is blocked (the "slide projector" look).</p>
 */
public final class CookieArray
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    /** Per-layer square resolution; loaded images are resampled to this. */
    public static final int RES = 512;
    /** Hard cap on simultaneously loaded distinct cookies (array depth). */
    public static final int MAX_LAYERS = 16;

    private static int glTextureId = 0;
    private static boolean initialized = false;

    /** link string -> array layer (or -1 cached for a known-bad asset, so a broken
     *  image isn't re-decoded every frame). */
    private static final Map<String, Integer> keyToLayer = new HashMap<>();
    private static int nextLayer = 0;

    private CookieArray()
    {}

    /** Lazy — 0 until the first cookie is uploaded (no VRAM if unused). */
    public static int getGlTextureId()
    {
        return glTextureId;
    }

    /** Resolve a cookie texture link to its array layer, loading on first use.
     *  Render thread only (uploads to GL). Returns -1 for a null/empty link, a
     *  failed load, or a full array. The result (incl. -1) is cached per link. */
    public static int resolve(Link link)
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
        Integer cached = keyToLayer.get(key);
        if (cached != null)
        {
            return cached;
        }
        int layer = (nextLayer < MAX_LAYERS) ? load(link, key) : -1;
        keyToLayer.put(key, layer);
        return layer;
    }

    private static int load(Link link, String key)
    {
        byte[] raw;
        try (InputStream in = BBSMod.getProvider().getAsset(link))
        {
            raw = in.readAllBytes();
        }
        catch (Exception e)
        {
            LOG.warn("Cookie asset read failed: {}", key, e);
            return -1;
        }

        ByteBuffer rawBuf = MemoryUtil.memAlloc(raw.length);
        rawBuf.put(raw).flip();

        ByteBuffer img = null;
        ByteBuffer resized = null;
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);

            img = STBImage.stbi_load_from_memory(rawBuf, w, h, c, 1);   // force 1 channel (grayscale)
            if (img == null)
            {
                LOG.warn("Cookie decode failed: {} ({})", key, STBImage.stbi_failure_reason());
                return -1;
            }

            resized = MemoryUtil.memAlloc(RES * RES);
            STBImageResize.stbir_resize_uint8(img, w.get(0), h.get(0), 0, resized, RES, RES, 0, 1);

            int layer = upload(resized);
            LOG.info("Cookie loaded '{}' -> layer {}", key, layer);
            return layer;
        }
        finally
        {
            if (img != null)
            {
                STBImage.stbi_image_free(img);
            }
            if (resized != null)
            {
                MemoryUtil.memFree(resized);
            }
            MemoryUtil.memFree(rawBuf);
        }
    }

    /** Upload one RES*RES grayscale buffer into the next free layer, allocating the
     *  array on first use. Returns the layer index. Saves and restores the GL state
     *  it touches.
     *
     *  <p>Unlike the standalone editor, we run inside BBS / Sodium / Minecraft, which
     *  routinely leave a {@code GL_PIXEL_UNPACK_BUFFER} (PBO) bound and the pixel-store
     *  unpack params non-default. With a PBO bound, our client {@link ByteBuffer}
     *  pointer is reinterpreted by the driver as an offset INTO that PBO and it reads
     *  out of bounds — a native {@code EXCEPTION_ACCESS_VIOLATION} crash. So force a
     *  clean, tightly-packed client-memory upload (no PBO, alignment 1, no row/skip),
     *  then restore the prior state.</p> */
    private static int upload(ByteBuffer pixels)
    {
        int prevTex = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        int prevPbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int prevAlign = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int prevRowLen = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int prevSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int prevSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int prevImgHeight = GL11.glGetInteger(GL12.GL_UNPACK_IMAGE_HEIGHT);
        int prevSkipImages = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_IMAGES);

        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);

        if (!initialized)
        {
            init();
        }
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, glTextureId);

        int layer = nextLayer++;
        GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, RES, RES, 1,
            GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, pixels);

        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, prevTex);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, prevPbo);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevAlign);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRowLen);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevSkipRows);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevSkipPixels);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, prevImgHeight);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, prevSkipImages);
        return layer;
    }

    private static void init()
    {
        int prev = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);
        glTextureId = GL11.glGenTextures();
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, glTextureId);

        GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_R8, RES, RES, MAX_LAYERS, 0,
            GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer border = stack.floats(0f, 0f, 0f, 0f);   // outside the image = black = blocked
            GL11.glTexParameterfv(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_BORDER_COLOR, border);
        }

        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, prev);
        initialized = true;
    }

    /** Forget all loaded cookies and free the GL texture, so a next {@link #resolve}
     *  reloads from the BBS assets (picks up an edited texture). */
    public static void reload()
    {
        keyToLayer.clear();
        nextLayer = 0;
        delete();
    }

    public static void delete()
    {
        if (initialized)
        {
            GL11.glDeleteTextures(glTextureId);
            glTextureId = 0;
            initialized = false;
        }
    }
}
