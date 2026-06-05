package org.wemppy.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import org.wemppy.irlite.IrliteConfig;
import org.wemppy.irlite.client.light.LightCollector;
import org.wemppy.irlite.client.light.shadow.ShadowBakeState;

public abstract class AbstractLightFormRenderer<T extends Form> extends FormRenderer<T>
{
    public AbstractLightFormRenderer(T form)
    {
        super(form);
    }

    protected abstract Color lightColor();

    protected abstract Icon icon();

    protected abstract void renderGuide(FormRenderingContext context, Color color);

    /** Register this light into the per-frame registry (render-path: live actors / replays). */
    protected abstract void registerLight(FormRenderingContext context);

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean editorPreview = context.type == FormRenderType.PREVIEW || context.modelRenderer || context.ui;

        // Skip entirely while baking shadows — a light form inside a caster's
        // form-tree would otherwise re-register every face/tile pass.
        if (ShadowBakeState.isBaking())
        {
            return;
        }

        // World render path: register the light (unless the scanner owns it), draw guide.
        if (!context.isPicking() && !context.ui && !BBSRendering.isIrisShadowPass()
            && (context.type == FormRenderType.MODEL_BLOCK || context.type == FormRenderType.ENTITY))
        {
            if (!LightCollector.isHandledByScanner(context))
            {
                this.registerLight(context);
            }

            if (IrliteConfig.showGuides())
            {
                this.renderGuide(context, this.tintedColor(context));
            }

            return;
        }

        if (editorPreview && !context.isPicking())
        {
            this.renderGuide(context, this.tintedColor(context));

            return;
        }

        if (!context.isPicking())
        {
            return;
        }

        this.renderPickBox(context);
    }

    private Color tintedColor(FormRenderingContext context)
    {
        Color c = this.lightColor().copy();
        c.mul(context.color);

        return c;
    }

    private void renderPickBox(FormRenderingContext context)
    {
        Color c = this.tintedColor(context);

        context.stack.push();
        context.stack.translate(-0.25, 0, -0.25);

        CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
        {
            this.setupTarget(context, BBSShaders.getPickerModelsProgram());
            RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
        });

        Draw.renderBox(context.stack, 0, 0, 0, 0.5, 0.5, 0.5, c.r, c.g, c.b);

        CustomVertexConsumerProvider.clearRunnables();
        RenderSystem.enableDepthTest();

        context.stack.pop();
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        int tint = this.lightColor().getARGBColor();
        int cw = x2 - x1;
        int ch = y2 - y1;
        int pad = 6;
        int size = Math.min(cw, ch) - pad * 2;

        if (size < 12)
        {
            size = Math.min(cw, ch);
        }

        float ix = x1 + (cw - size) / 2F;
        float iy = y1 + (ch - size) / 2F;

        context.batcher.box(ix, iy, ix + size, iy + size, Colors.A50 | 0x1a1a1e);

        Icon icon = this.icon();
        Texture atlas = BBSModClient.getTextures().getTexture(icon.texture);
        context.batcher.texturedBox(
            atlas, tint, ix, iy, size, size,
            icon.x, icon.y, icon.x + icon.w, icon.y + icon.h,
            icon.textureW, icon.textureH
        );
    }
}
