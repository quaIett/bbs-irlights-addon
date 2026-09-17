package qualet.irlite.client.light;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.mob.MobRenderContext;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import net.irisshaders.iris.Iris;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.VillagerEntity;
import org.qualet.irl.light.shadow.CasterRevision;
import qualet.irlite.mixin.client.bbs.FormStatePlayersAccessor;
import qualet.irlite.mixin.client.bbs.MobFormRendererAccessor;

import java.util.HashSet;
import java.util.WeakHashMap;

/** Villager MobForms, including the baseline scene. Runs the actual vanilla+BBS
 * evaluated renderer into a CPU-only vertex sink. The signature sees final bones,
 * feature geometry, UVs and alpha, not an inferred pose from entity position/age.
 * The whitelist excludes renderers/features with direct GPU work or labels/items. */
public final class BbsMobSilhouette
{
    private final WeakHashMap<Object, Long> identities = new WeakHashMap<>();
    private final BbsMobPoseScratch poseScratch = new BbsMobPoseScratch();
    private long nextIdentity;
    private boolean checked;

    public CasterRevision sample(ModelBlockEntity block, float tickDelta)
    {
        CasterRevision revision = sampleEvaluated(block, tickDelta);
        if (!checked && revision.known() && Boolean.getBoolean("irlite.checkCasterRevisions"))
        {
            checked = true;
            BbsMobSilhouetteChecks.run(this, block, tickDelta);
        }
        return revision;
    }

    private final HashSet<String> reported = new HashSet<>();

    /** @see BbsModelSilhouette#DEBUG */
    private CasterRevision unknown(String reason)
    {
        if (BbsModelSilhouette.DEBUG && reported.add(reason)) System.out.println("[irlite] caster-revision(mob): UNKNOWN because " + reason);
        return CasterRevision.UNKNOWN;
    }

    private CasterRevision sampleEvaluated(ModelBlockEntity block, float tickDelta)
    {
        if (!BbsModelSilhouette.AUDITED) return unknown("BBS version/layout not audited");
        if (block.getProperties() == null) return unknown("model block without properties");
        if (!(block.getProperties().getForm() instanceof MobForm form) || form.getClass() != MobForm.class)
            return unknown("form is not a plain MobForm");
        if (!"minecraft:villager".equals(form.mobID.get())) return unknown("mob is not a villager: " + form.mobID.get());
        if (!form.mobNBT.get().isBlank() || form.texture.get() != null) return unknown("villager with NBT or texture override");
        if (!BbsModelSilhouette.shadowlessChildren(form)) return unknown("body parts other than leaf light forms");
        if (!((FormStatePlayersAccessor) form).irlite$statePlayers().isEmpty()) return unknown("animation state players active");
        if (MobRenderContext.current() != null) return unknown("sampled inside a BBS mob render");
        if (!(FormUtilsClient.getRenderer(form) instanceof MobFormRenderer renderer)
            || renderer.getClass() != MobFormRenderer.class) return unknown("renderer is not the plain MobFormRenderer");
        var access = (MobFormRendererAccessor) renderer;
        access.irlite$ensureEntity();
        if (!(access.irlite$entity() instanceof VillagerEntity entity) || entity.getClass() != VillagerEntity.class)
            return unknown("renderer entity is not a plain VillagerEntity");
        // BBS 2.6: the form pose rides a MobRenderContext bound to the renderer's rig.
        MobRig rig = renderer.getRig();
        if (rig == null) return unknown("no mob rig for the villager renderer");
        if (entity.hasCustomName()) return unknown("villager with custom name");
        for (var slot : net.minecraft.entity.EquipmentSlot.values()) if (!entity.getEquippedStack(slot).isEmpty()) return unknown("villager with equipment");
        if (entity instanceof ISelectorOwnerProvider provider)
        {
            provider.getOwner().check();
            // Dispatcher wraps the vanilla render with BBS's morph renderer.
            // A nested morph could issue direct GL draws instead of using our sink.
            if (provider.getOwner().getForm() != null) return unknown("villager morphed by a selector");
        }
        var dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (dispatcher.shouldRenderHitboxes()) return unknown("hitboxes shown");
        if (!(dispatcher.getRenderer(entity) instanceof VillagerEntityRenderer vanilla)
            || vanilla.getClass() != VillagerEntityRenderer.class
            || vanilla.getModel().getClass() != VillagerResemblingModel.class) return unknown("non-vanilla villager renderer/model");
        Object pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return unknown("no Iris pipeline");

        var transform = new BbsModelSilhouette.Signature().word(block.getPos().asLong())
            .transform(block.getProperties().getTransform()).transform(form.transform.get()).transform(form.transformOverlay.get());
        for (var extra : form.additionalTransforms) transform.transform(extra.get());
        var material = new BbsModelSilhouette.Signature().word(form.visible.get() ? 1 : 0)
            .word(form.shaderShadow.get() ? 1 : 0);
        var stream = new VertexSignature();
        var model = vanilla.getModel();
        BbsMobPoseScratch saved = poseScratch.acquire();
        MobRenderContext context = null;
        boolean child = model.child, riding = model.riding;
        float handSwing = model.handSwingProgress;
        int hurt = entity.hurtTime;
        Throwable evaluationFailure = null;
        try
        {
            model.getPart().traverse().forEachOrdered(saved::capture);
            // MobFormRenderer supplies default overlay (v=10) in the shadow path.
            entity.hurtTime = 0;
            context = MobRenderContext.push(rig, form.pose.get(), form.poseOverlay.get());
            // Use the SAME dispatcher as MobFormRenderer, including its vanilla
            // ground-shadow geometry and camera-dependent alpha. Rendering only
            // vanilla.render would omit that silhouette-relevant output.
            dispatcher.render(entity, 0, 0, 0, 0, tickDelta, new MatrixStack(), layer -> {
                String name = layer.toString();
                material.string(name);
                stream.signature.word(6).string(name);
                return stream;
            }, LightmapTextureManager.pack(15, 15));
        }
        catch (RuntimeException | Error failure)
        {
            evaluationFailure = failure;
            throw failure;
        }
        finally
        {
            try
            {
                // Also restore after an exception before BBS's TAIL pose cleanup
                // or inside an optional cleanup accessor.
                saved.release(context, evaluationFailure);
            }
            finally
            {
                model.child = child;
                model.riding = riding;
                model.handSwingProgress = handSwing;
                entity.hurtTime = hurt;
            }
        }
        long resources = new BbsModelSilhouette.Signature().word(ShadowResourceVersions.reloadVersion())
            .word(identity(pipeline)).value;
        return new CasterRevision(true, transform.value, stream.signature.value,
            identity(form), identity(model), material.value, resources);
    }

    private long identity(Object value) { return identities.computeIfAbsent(value, key -> ++nextIdentity); }

    private static final class VertexSignature implements VertexConsumer
    {
        final BbsModelSilhouette.Signature signature = new BbsModelSilhouette.Signature();
        @Override public VertexConsumer vertex(float x, float y, float z)
        {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite evaluated vertex");
            signature.word(1).word(Double.doubleToLongBits(x)).word(Double.doubleToLongBits(y)).word(Double.doubleToLongBits(z));
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { signature.word(2).word(r).word(g).word(b).word(a); return this; }
        @Override public VertexConsumer texture(float u, float v) { signature.word(3).number(u).number(v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
