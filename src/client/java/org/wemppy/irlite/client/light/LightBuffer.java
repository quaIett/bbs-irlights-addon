package org.wemppy.irlite.client.light;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Owns the GPU light SSBO and packs collected lights into it each frame.
 *
 * std430 contract (the patcher's GLSL must mirror this exactly):
 *
 *   struct Light {
 *       vec4 posRadius;       // xyz = world position, w = radius
 *       vec4 colorIntensity;  // rgb = linear color, a = intensity
 *       vec4 dirType;         // xyz = direction (spot, normalized), w = type (0 point, 1 spot)
 *       vec4 cone;            // x = cos(outerAngle/2), y = cos(innerAngle/2), z = entitiesOnly (0/1), w = pad
 *   };
 *
 *   layout(std430, binding = BINDING) buffer IrliteLights {
 *       uint irlite_lightCount;
 *       uint _pad0, _pad1, _pad2;
 *       Light irlite_lights[];
 *   };
 */
public final class LightBuffer
{
    public static final int BINDING = 7;
    public static final int MAX_LIGHTS = 256;

    private static final int HEADER_BYTES = 16;     // uint count + 12 B pad (std430 vec4 align)
    private static final int LIGHT_BYTES = 64;      // 4 × vec4
    private static final int CAPACITY = HEADER_BYTES + MAX_LIGHTS * LIGHT_BYTES;

    private static int ssbo = 0;
    private static ByteBuffer scratch = null;
    private static boolean initialized = false;
    private static int count = 0;

    private LightBuffer()
    {}

    private static void init()
    {
        if (initialized)
        {
            return;
        }

        ssbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, CAPACITY, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        scratch = MemoryUtil.memAlloc(CAPACITY);

        initialized = true;
    }

    public static void begin()
    {
        if (!initialized)
        {
            init();
        }

        count = 0;
        scratch.clear();
        scratch.position(HEADER_BYTES);
    }

    public static void addPoint(float x, float y, float z, float r, float g, float b, float intensity, float radius, boolean entitiesOnly)
    {
        if (count >= MAX_LIGHTS)
        {
            return;
        }

        scratch.putFloat(x).putFloat(y).putFloat(z).putFloat(radius);
        scratch.putFloat(r).putFloat(g).putFloat(b).putFloat(intensity);
        scratch.putFloat(0F).putFloat(0F).putFloat(0F).putFloat(0F);  // dir=0, type=point
        scratch.putFloat(1F).putFloat(1F).putFloat(entitiesOnly ? 1F : 0F).putFloat(0F);  // cone: full, z=entitiesOnly

        count++;
    }

    public static void addSpot(float x, float y, float z, float dx, float dy, float dz, float r, float g, float b, float intensity, float radius, float cosOuter, float cosInner, boolean entitiesOnly)
    {
        if (count >= MAX_LIGHTS)
        {
            return;
        }

        scratch.putFloat(x).putFloat(y).putFloat(z).putFloat(radius);
        scratch.putFloat(r).putFloat(g).putFloat(b).putFloat(intensity);
        scratch.putFloat(dx).putFloat(dy).putFloat(dz).putFloat(1F);   // type=spot
        scratch.putFloat(cosOuter).putFloat(cosInner).putFloat(entitiesOnly ? 1F : 0F).putFloat(0F);

        count++;
    }

    public static void upload()
    {
        if (!initialized)
        {
            return;
        }

        scratch.putInt(0, count);

        int used = scratch.position();
        scratch.position(0);
        scratch.limit(used);

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, ssbo);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, scratch);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BINDING, ssbo);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        scratch.clear();
    }

    public static int getCount()
    {
        return count;
    }

    public static void delete()
    {
        if (ssbo != 0)
        {
            GL15.glDeleteBuffers(ssbo);
            ssbo = 0;
        }

        if (scratch != null)
        {
            MemoryUtil.memFree(scratch);
            scratch = null;
        }

        initialized = false;
    }
}
