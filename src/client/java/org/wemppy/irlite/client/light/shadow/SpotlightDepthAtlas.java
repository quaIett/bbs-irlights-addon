package org.wemppy.irlite.client.light.shadow;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * 2D depth atlas of perspective shadow maps, one tile per spotlight.
 * GRID_X x GRID_Y grid of TILE_SIZE^2 tiles; tile i = spot slot i at pixel
 * origin ((i%GRID_X)*TILE_SIZE, (i/GRID_X)*TILE_SIZE).
 *
 * Format DEPTH_COMPONENT32F, NEAREST, manual compare in the shader (no
 * fixed-function compare). Lazy alloc: nothing until the first bake.
 */
public final class SpotlightDepthAtlas
{
    public static int TILE_SIZE = 1024;
    public static final int GRID_X = 4;
    public static final int GRID_Y = 4;
    public static final int MAX_TILES = GRID_X * GRID_Y;

    private static int glTextureId = 0;
    private static int glFboId = 0;
    private static boolean initialized = false;

    private SpotlightDepthAtlas()
    {}

    public static int getAtlasWidth()
    {
        return TILE_SIZE * GRID_X;
    }

    public static int getAtlasHeight()
    {
        return TILE_SIZE * GRID_Y;
    }

    /** Lazy — returns 0 until the first bake allocates (keeps VRAM free when no spot exists). */
    public static int getGlTextureId()
    {
        return glTextureId;
    }

    public static int getFboId()
    {
        if (!initialized)
        {
            init();
        }
        return glFboId;
    }

    public static int tilePixelX(int tile)
    {
        return (tile % GRID_X) * TILE_SIZE;
    }

    public static int tilePixelY(int tile)
    {
        return (tile / GRID_X) * TILE_SIZE;
    }

    private static void init()
    {
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        glTextureId = GlStateManager._genTexture();
        GlStateManager._bindTexture(glTextureId);

        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F,
            getAtlasWidth(), getAtlasHeight(), 0,
            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null
        );

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);

        glFboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glFboId);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glTextureId, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
        {
            throw new IllegalStateException("SpotlightDepthAtlas FBO incomplete: 0x" + Integer.toHexString(status));
        }

        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        GL11.glViewport(0, 0, getAtlasWidth(), getAtlasHeight());
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GlStateManager._bindTexture(prevTex);

        initialized = true;
    }

    public static void delete()
    {
        if (!initialized)
        {
            return;
        }
        GL11.glDeleteTextures(glTextureId);
        GL30.glDeleteFramebuffers(glFboId);
        glTextureId = 0;
        glFboId = 0;
        initialized = false;
    }

    /** Switch tile resolution; frees + re-inits the atlas on next access. */
    public static void setTileSize(int newSize)
    {
        if (newSize == TILE_SIZE)
        {
            return;
        }
        TILE_SIZE = newSize;
        delete();
    }
}
