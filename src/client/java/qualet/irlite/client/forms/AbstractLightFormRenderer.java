package qualet.irlite.client.forms;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
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
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
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

    protected abstract void renderGuide(FormRenderingContext context, Color color);

    /** Register this light into the per-frame registry (render-path: live actors / replays). */
    protected abstract void registerLight(FormRenderingContext context);

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean editorPreview = context.type == FormRenderType.PREVIEW || context.modelRenderer || context.ui;

        // Skip entirely while baking shadows — a light form inside a caster's
        // form-tree would otherwise re-register every face/tile pass (and, on
        // 1.21.11, its guide would land in the captured occluder geometry).
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
     * Draw the light's morph-list icon as a flat quad in the inventory item slot.
     * The model-block item model has no display transform, so the GUI view is
     * straight-on and a quad on the block's local XY plane reads face-on. 1.21.11:
     * item forms are recorded through BBS's FormRenderCapture and replayed later, so
     * the quad goes through BBS's billboard layer — resolved right after binding the
     * icons atlas, the layer carries that texture in its own Sampler0 — instead of the
     * removed global position_tex_color program. Both faces are emitted so the pick
     * of winding/culling can't hide it.
     */
    private void renderItemIcon(FormRenderingContext context)
    {
        Icon icon = this.icon();

        if (icon == null || icon.texture == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(icon.texture);

        if (texture == null)
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

        BBSModClient.getTextures().bindTexture(texture);
        RenderLayer layer = BBSShaders.getBoundBillboardLayer();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

        /* Alpha forced to 1 — a light with a translucent colour must still show
         * a solid icon rather than a faint/invisible one. Front, then back. */
        builder.vertex(matrix, x1, y1, z).texture(u1, v2).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x2, y1, z).texture(u2, v2).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x2, y2, z).texture(u2, v1).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x1, y1, z).texture(u1, v2).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x2, y2, z).texture(u2, v1).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x1, y2, z).texture(u1, v1).color(c.r, c.g, c.b, 1F);

        builder.vertex(matrix, x2, y2, z).texture(u2, v1).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x2, y1, z).texture(u2, v2).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x1, y1, z).texture(u1, v2).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x1, y2, z).texture(u1, v1).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x2, y2, z).texture(u2, v1).color(c.r, c.g, c.b, 1F);
        builder.vertex(matrix, x1, y1, z).texture(u1, v2).color(c.r, c.g, c.b, 1F);

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            layer.draw(built);
        }

        context.stack.pop();
    }

    private Color tintedColor(FormRenderingContext context)
    {
        Color c = this.lightColor().copy();
        c.mul(context.color);

        return c;
    }

    /**
     * The whole-form pick zone: a small wire box in this form's picking id. 1.21.11
     * has no global picker program to hijack the box's draw into, so the id rides the
     * vertex colour through the same colour-id pass as the guide handles.
     */
    private void renderPickBox(FormRenderingContext context)
    {
        Color id = LightGuideRenderer.stencilColor(context.getPickingIndex());

        context.stack.push();
        context.stack.translate(-0.25, 0, -0.25);

        BufferBuilder builder = LightGuideRenderer.beginTriangles();

        Draw.renderBox(builder, context.stack, 0, 0, 0, 0.5, 0.5, 0.5, id.r, id.g, id.b, 1F);
        LightGuideRenderer.flushStencil(builder);

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
