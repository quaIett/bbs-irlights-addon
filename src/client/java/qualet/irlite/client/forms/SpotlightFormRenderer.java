package qualet.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import qualet.irlite.client.light.IRLightPositionResolver;
import qualet.irlite.client.light.cookie.CookieArray;
import org.qualet.irl.light.LightMath;
import org.qualet.irl.light.LightRegistry;
import qualet.irlite.forms.SpotlightForm;

public class SpotlightFormRenderer extends AbstractLightFormRenderer<SpotlightForm>
{
    public SpotlightFormRenderer(SpotlightForm form)
    {
        super(form);
    }

    @Override
    protected Color lightColor()
    {
        return this.form.color.get();
    }

    @Override
    protected Icon icon()
    {
        return Icons.FRUSTUM;
    }

    @Override
    protected void renderGuide(FormRenderingContext context, Color color)
    {
        /* Editor preview and in-world film actors both host draggable handles —
         * capture the guide's local->view matrix wherever the guide is drawn. */
        if (context.modelRenderer || context.type == FormRenderType.ENTITY)
        {
            SpotGuideDrag.captureGuideMatrix(this.form, context.stack);
        }

        LightGuideRenderer.renderSpotlight(context.stack, color, this.form.range.get(), this.form.radius.get(), this.form.innerRadius.get());
    }

    @Override
    protected void renderStencilHandles(FormRenderingContext context)
    {
        float range = this.form.range.get();
        float outer = this.form.radius.get();
        float inner = Math.min(this.form.innerRadius.get(), outer);

        /* Per handle: draw the grab zone with the CURRENT free stencil index
         * encoded as vertex color, then register — addPicking assigns that
         * index and increments. Inner is drawn after outer so it wins the
         * overlap when angles meet; the range disc wins the cap center. */
        LightGuideRenderer.renderSpotlightGrabRing(context.stack, range, outer, context.getPickingIndex());
        context.stencilMap.addPicking(this.form, SpotGuideDrag.HANDLE_RADIUS);

        LightGuideRenderer.renderSpotlightGrabRing(context.stack, range, inner, context.getPickingIndex());
        context.stencilMap.addPicking(this.form, SpotGuideDrag.HANDLE_INNER);

        LightGuideRenderer.renderSpotlightGrabCap(context.stack, range, context.getPickingIndex());
        context.stencilMap.addPicking(this.form, SpotGuideDrag.HANDLE_RANGE);
    }

    @Override
    protected void registerLight(FormRenderingContext context)
    {
        Vector3d p = IRLightPositionResolver.resolve(context);

        // Direction: the spotlight's local +Z in WORLD space. Read it straight from
        // context.world — the same parallel world stack the position comes from — so it
        // can't desync from the render stack the way rebuilding from camera.getRotation()
        // did on 1.21 (the light drifted; see IRLightPositionResolver). Fallback keeps
        // the camera rebuild for renders with no based world stack.
        Vector3f fwd;
        if (context.world != null && context.type == FormRenderType.ENTITY)
        {
            fwd = context.world.peek().getPositionMatrix().transformDirection(new Vector3f(0F, 0F, 1F));
        }
        else
        {
            Matrix4f matrix = new Matrix4f(new org.joml.Matrix3f().rotation(
                net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getRotation()));
            matrix.mul(context.stack.peek().getPositionMatrix());
            Vector4f f = new Vector4f(0F, 0F, 1F, 0F);
            matrix.transform(f);
            fwd = new Vector3f(f.x, f.y, f.z);
        }
        Vector4f forward = new Vector4f();
        LightMath.normalizeDir(fwd.x, fwd.y, fwd.z, 0F, 0F, 1F, forward);
        float dx = forward.x, dy = forward.y, dz = forward.z;

        LightMath.Cone cone = LightMath.cone(this.form.radius.get(), this.form.innerRadius.get());
        float cosOuter = cone.cosOuter();
        float cosInner = cone.cosInner();

        // Resolve the gobo texture (BBS Link) to its texture-array layer, exactly as
        // the scanner path does in LightCollector.emitSpot. Without this the render
        // path called the no-cookie registerSpot overload (cookie forced to layer -1),
        // so a spotlight registered here — a live actor, an in-world film replay, or a
        // light hung off a BodyPart bone (which the scanner always skips and delegates
        // to this path) — never projected its cookie. -1 = no mask, cookie OFF unless a
        // texture is picked. Rotation is stored in degrees on the form -> radians here.
        int cookieLayer = CookieArray.resolve(this.form.cookie.get());
        float cookieRot = (float) Math.toRadians(this.form.cookieRotation.get());
        float cookieFlags = this.form.cookieInvert.get() ? 1F : 0F;

        Color c = this.form.color.get();
        LightRegistry.registerSpot(
            p.x, p.y, p.z,
            dx, dy, dz,
            c.r, c.g, c.b,
            this.form.intensity.get(), this.form.range.get(),
            cosOuter, cosInner,
            this.form.entitiesOnly.get(), this.form.blocksOnly.get(),
            this.form.anisotropy.get(), this.form.vlDensity.get(), this.form.beamStrength.get(),
            this.form.bulbSize.get(), this.form.shadows.get(),
            (float) cookieLayer, cookieRot, this.form.cookieScale.get(), cookieFlags,
            System.identityHashCode(this.form)
        );
    }
}
