package org.wemppy.irlite.client.light.shadow;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;

/**
 * Cube-map-array of depth shadow maps, one cubemap (6 faces) per shadowed point
 * light. Face f of shadow slot i lives at array layer i*6 + f.
 *
 * Each face is a 90-degree perspective depth render from the light position
 * (near 0.05, far = radius). Shader test: sample with the world-space direction
 * lightPos->receiver + the slot index, compare against the dominant-axis
 * perspective depth (NOT Euclidean length — that gives a 6-pointed star).
 *
 * GL_TEXTURE_CUBE_MAP_ARRAY is not in Iris's TextureType enum, so the sampler
 * bind is fixed up by {@link org.wemppy.irlite.mixin.client.iris.SamplerBindingCubeArrayMixin}.
 */
public final class PointShadowArray
{
    /** Max point lights that get a cube shadow (cube-array is expensive). */
    public static final int MAX_SHADOWS = 16;
    public static int FACE_SIZE = 512;
    public static final int LAYER_COUNT = 6 * MAX_SHADOWS;

    private static final int GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F;

    private static int glTextureId = 0;
    private static int glFboId = 0;
    private static boolean initialized = false;

    private PointShadowArray()
    {}

    public static int getGlTextureId()
    {
        return glTextureId;
    }

    public static void bindFaceForRender(int slot, int face)
    {
        if (!initialized)
        {
            init();
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glFboId);
        int layer = slot * 6 + face;
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, glTextureId, 0, layer);
    }

    private static void init()
    {
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevCubeArray = GL11.glGetInteger(GL40.GL_TEXTURE_BINDING_CUBE_MAP_ARRAY);

        glTextureId = GlStateManager._genTexture();
        GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, glTextureId);

        GL12.glTexImage3D(
            GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0, GL30.GL_DEPTH_COMPONENT32F,
            FACE_SIZE, FACE_SIZE, LAYER_COUNT, 0,
            GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null
        );

        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);

        GL11.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);

        glFboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, glFboId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, glTextureId, 0, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
        {
            throw new IllegalStateException("PointShadowArray FBO incomplete: 0x" + Integer.toHexString(status));
        }

        for (int layer = 0; layer < LAYER_COUNT; layer++)
        {
            GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, glTextureId, 0, layer);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, glTextureId, 0, 0);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, prevCubeArray);
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
}
