package qualet.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.function.Consumer;

final class LightGuideRenderer
{
    private static final int CIRCLE_SEGMENTS = 48;
    private static final float WIRE_ALPHA = 0.92F;
    private static final float AXIS_ALPHA = 0.75F;

    private LightGuideRenderer()
    {}

    public static void renderPointLight(MatrixStack stack, Color color, float radius)
    {
        float r = Math.max(radius, 0.05F);
        float t = clamp(r * 0.0025F, 0.002F, 0.009F);

        renderTriangles((builder) ->
        {
            renderCircle(builder, stack, Axis.X, r, t, color, WIRE_ALPHA);
            renderCircle(builder, stack, Axis.Y, r, t, color, WIRE_ALPHA);
            renderCircle(builder, stack, Axis.Z, r, t, color, WIRE_ALPHA);

            line(builder, stack, -r, 0, 0, r, 0, 0, t, color, AXIS_ALPHA);
            line(builder, stack, 0, -r, 0, 0, r, 0, t, color, AXIS_ALPHA);
            line(builder, stack, 0, 0, -r, 0, 0, r, t, color, AXIS_ALPHA);
        });
    }

    public static void renderSpotlight(MatrixStack stack, Color color, float range, float outerAngle, float innerAngle)
    {
        float r = Math.max(range, 0.05F);
        float outer = Math.max(outerAngle, 1F);
        float inner = clamp(innerAngle, 1F, outer);
        float outerR = coneRadius(r, outer);
        float t = clamp(r * 0.0020F, 0.002F, 0.009F);

        renderTriangles((builder) ->
        {
            line(builder, stack, 0, 0, 0, 0, 0, -r, t, color, AXIS_ALPHA);
            coneWire(builder, stack, -r, outerR, t, color, WIRE_ALPHA);
            ringAtZ(builder, stack, r, outerR, t, color, WIRE_ALPHA);

            if (inner < outer)
            {
                float innerR = coneRadius(r, inner);
                ringAtZ(builder, stack, r, innerR, t, color, AXIS_ALPHA * 0.7F);
            }
        });
    }

    private static void renderTriangles(Consumer<BufferBuilder> consumer)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // 1.21: begin() moved to Tessellator and returns the builder.
        BufferBuilder builder = Tessellator.getInstance()
            .begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        consumer.accept(builder);

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void coneWire(BufferBuilder builder, MatrixStack stack, float capZ, float radius, float t, Color color, float alpha)
    {
        line(builder, stack, 0, 0, 0,  radius, 0, capZ, t, color, alpha);
        line(builder, stack, 0, 0, 0, -radius, 0, capZ, t, color, alpha);
        line(builder, stack, 0, 0, 0, 0,  radius, capZ, t, color, alpha);
        line(builder, stack, 0, 0, 0, 0, -radius, capZ, t, color, alpha);
    }

    private static void ringAtZ(BufferBuilder builder, MatrixStack stack, float z, float radius, float t, Color color, float alpha)
    {
        stack.push();
        stack.translate(0, 0, z);
        renderCircle(builder, stack, Axis.Z, radius, t, color, alpha);
        stack.pop();
    }

    private static void renderCircle(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, Color color, float alpha)
    {
        if (radius <= 0.0001F)
        {
            return;
        }

        Matrix4f m = stack.peek().getPositionMatrix();
        float r = color.r, g = color.g, b = color.b;
        float halfT = thickness * 0.5F;
        float rIn = Math.max(radius - halfT, 0F);
        float rOut = radius + halfT;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++)
        {
            double a1 = Math.PI * 2D * i / CIRCLE_SEGMENTS;
            double a2 = Math.PI * 2D * (i + 1) / CIRCLE_SEGMENTS;
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float c2 = (float) Math.cos(a2);
            float s2 = (float) Math.sin(a2);

            float ix1, iy1, iz1, ox1, oy1, oz1;
            float ix2, iy2, iz2, ox2, oy2, oz2;

            if (axis == Axis.X)
            {
                ix1 = 0; iy1 = s1 * rIn;  iz1 = c1 * rIn;
                ox1 = 0; oy1 = s1 * rOut; oz1 = c1 * rOut;
                ix2 = 0; iy2 = s2 * rIn;  iz2 = c2 * rIn;
                ox2 = 0; oy2 = s2 * rOut; oz2 = c2 * rOut;
            }
            else if (axis == Axis.Y)
            {
                ix1 = s1 * rIn;  iy1 = 0; iz1 = c1 * rIn;
                ox1 = s1 * rOut; oy1 = 0; oz1 = c1 * rOut;
                ix2 = s2 * rIn;  iy2 = 0; iz2 = c2 * rIn;
                ox2 = s2 * rOut; oy2 = 0; oz2 = c2 * rOut;
            }
            else
            {
                ix1 = s1 * rIn;  iy1 = c1 * rIn;  iz1 = 0;
                ox1 = s1 * rOut; oy1 = c1 * rOut; oz1 = 0;
                ix2 = s2 * rIn;  iy2 = c2 * rIn;  iz2 = 0;
                ox2 = s2 * rOut; oy2 = c2 * rOut; oz2 = 0;
            }

            vertex(builder, m, ix1, iy1, iz1, r, g, b, alpha);
            vertex(builder, m, ox1, oy1, oz1, r, g, b, alpha);
            vertex(builder, m, ox2, oy2, oz2, r, g, b, alpha);

            vertex(builder, m, ix1, iy1, iz1, r, g, b, alpha);
            vertex(builder, m, ox2, oy2, oz2, r, g, b, alpha);
            vertex(builder, m, ix2, iy2, iz2, r, g, b, alpha);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f m, float x, float y, float z, float r, float g, float b, float a)
    {
        builder.vertex(m, x, y, z).color(r, g, b, a);
    }

    private static void line(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float t, Color color, float alpha)
    {
        Draw.fillBoxTo(builder, stack, x1, y1, z1, x2, y2, z2, t, color.r, color.g, color.b, alpha);
    }

    private static float coneRadius(float range, float angle)
    {
        return (float) (Math.tan(Math.toRadians(angle * 0.5F)) * range);
    }

    private static float clamp(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
