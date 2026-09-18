package qualet.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.forms.BodyPart;
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
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.LightCollector;
import org.qualet.irl.light.shadow.ShadowBakeState;

public abstract class AbstractLightFormRenderer<T extends Form> extends FormRenderer<T>
{
    public AbstractLightFormRenderer(T form)
    {
        super(form);
    }

    protected abstract Color lightColor();

    protected abstract Icon icon();

    protected abstract void renderGuide(FormRenderingContext context, Color color, boolean world);

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

        // A light form has no geometry, so a model-block item that uses one as
        // its inventory form would render as an empty (transparent) icon. Draw
        // the morph-list icon (point light / spotlight) instead so the item
        // reads as a light in the inventory slot.
        if (context.type == FormRenderType.ITEM_INVENTORY && !context.isPicking())
        {
            this.renderItemIcon(context);

            return;
        }

        // World render path: register the light (unless the scanner owns it), draw guide.
        if (!context.isPicking() && !context.ui && !BBSRendering.isIrisShadowPass()
            && (context.type == FormRenderType.MODEL_BLOCK || context.type == FormRenderType.ENTITY))
        {
            // A bone-attached light (its form is parented to a BodyPart with a
            // non-empty bone) is ALWAYS skipped by the scanner walk — the scanner
            // runs in clean world coords and has no rig pose to place the bone, so
            // it delegates bone parts to this render-path. But the scanner also
            // *owns* whole contexts (MODEL_BLOCK, dashboard-roster ENTITY) and we'd
            // normally yield to it; for a bone-attached light that means neither
            // path registers it and it never lights. So force render-path ownership
            // whenever the light hangs off a bone. Dedup by form identity in
            // LightRegistry keeps this from double-registering.
            boolean boneAttached = this.form.getParent() instanceof BodyPart part
                && !part.bone.get().isEmpty();

            if (boneAttached || !LightCollector.isHandledByScanner(context))
            {
                this.registerLight(context);
            }

            /* Guides show with the global setting, or ad-hoc on the replay
             * currently selected in an open film editor (marked from the film
             * stencil pass — the rendered form is a COPY of replay.form, so
             * identity checks against the replay don't work here). */
            if (IrliteConfig.showGuides() || SpotGuideDrag.isFilmSelected(this.form))
            {
                this.renderGuide(context, this.tintedColor(context), true);
            }

            return;
        }

        if (editorPreview && !context.isPicking())
        {
            this.renderGuide(context, this.tintedColor(context), false);

            return;
        }

        if (!context.isPicking())
        {
            return;
        }

        /* Stencil pass: register interactive guide handles BEFORE the pick box
         * so each grabs its own stencil ID (draw with the current objectIndex,
         * then addPicking assigns it and increments; the box below then lands
         * on the next free ID, which the outer FormRenderer.render() maps to
         * the whole form via updateStencilMap). Active in the form-editor
         * preview and in the film viewport for the selected replay — BBS's
         * film picking renders ONLY the selected replay with increment on
         * (per-bone picking), other actors pick as whole entities and are
         * skipped. That same signal marks the form so the world pass shows
         * its guides. */
        boolean entityPick = context.type == FormRenderType.ENTITY;

        /* Film picking — marking the selected replay and registering the grab
         * handles — is confined to the replay editor. In the camera editor or the
         * replay editor's actions timeline the same replay stays selected (BBS
         * keeps rendering it with increment on), but the spotlight guides must
         * neither show nor be grabbable there. The form-editor preview path
         * (context.modelRenderer) is unaffected. */
        boolean filmPick = entityPick && context.stencilMap.increment && SpotGuideDrag.isReplayEditorActive();

        if (filmPick)
        {
            SpotGuideDrag.markFilmSelected(this.form);
        }

        if (filmPick || (context.modelRenderer && context.stencilMap.increment))
        {
            this.renderStencilHandles(context);
        }

        this.renderPickBox(context);
    }

    /** Override to add draggable guide handles to the editor-preview picking pass. */
    protected void renderStencilHandles(FormRenderingContext context)
    {}

    /**
     * Draw the light's morph-list icon as a flat, camera-facing quad in the
     * inventory item slot. The model-block item model is {@code builtin/entity}
     * with no display transform, so the GUI view is straight-on (no isometric
     * tilt) and a quad on the block's local XY plane reads face-on. Uses BBS's
     * own icons atlas (a raw GL texture, not a vanilla Identifier), so we bind
     * it directly and draw with the vanilla position_tex_color program.
     */
    private void renderItemIcon(FormRenderingContext context)
    {
        Icon icon = this.icon();

        if (icon == null || icon.texture == null)
        {
            return;
        }

        Color c = this.lightColor();

        float u1 = icon.x / (float) icon.textureW;
        float v1 = icon.y / (float) icon.textureH;
        float u2 = (icon.x + icon.w) / (float) icon.textureW;
        float v2 = (icon.y + icon.h) / (float) icon.textureH;

        /* Quad centred on the block footprint (origin is block-centre XZ at
         * y=0 after the item renderer's translate(0.5, 0, 0.5)), facing +Z. */
        float x1 = -0.45F, x2 = 0.45F;
        float y1 = 0.1F, y2 = 0.9F;
        float z = 0F;

        context.stack.push();

        /* FormRenderer.render() has already baked the light form's OWN transform
         * (its position/rotation/scale within the model block) onto the stack.
         * Undo it so the inventory icon stays pinned to the block/slot instead of
         * drifting by however far the light was moved inside the block. The form
         * transform is right-multiplied as createTransform().createMatrix(), so
         * multiplying by its inverse cancels it exactly. */
        Matrix4f formMatrix = new Matrix4f(this.createTransform().createMatrix());
        context.stack.peek().getPositionMatrix().mul(formMatrix.invert());

        Matrix4f matrix = context.stack.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        /* icons.png is loaded by BBS as a bare GL texture; bind its id to unit 0. */
        BBSModClient.getTextures().bindTexture(icon.texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

        /* Alpha forced to 1 — a light with a translucent colour must still show
         * a solid icon rather than a faint/invisible one. */
        builder.vertex(matrix, x1, y1, z).texture(u1, v2).color(c.r, c.g, c.b, 1F).next();
        builder.vertex(matrix, x2, y1, z).texture(u2, v2).color(c.r, c.g, c.b, 1F).next();
        builder.vertex(matrix, x2, y2, z).texture(u2, v1).color(c.r, c.g, c.b, 1F).next();
        builder.vertex(matrix, x1, y1, z).texture(u1, v2).color(c.r, c.g, c.b, 1F).next();
        builder.vertex(matrix, x2, y2, z).texture(u2, v1).color(c.r, c.g, c.b, 1F).next();
        builder.vertex(matrix, x1, y2, z).texture(u1, v1).color(c.r, c.g, c.b, 1F).next();

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();

        context.stack.pop();
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
