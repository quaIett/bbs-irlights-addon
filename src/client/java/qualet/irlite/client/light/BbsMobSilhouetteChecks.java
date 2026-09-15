package qualet.irlite.client.light;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.mob.MobRenderContext;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.VillagerEntityRenderer;
import net.minecraft.entity.passive.VillagerEntity;
import org.joml.Vector3f;
import qualet.irlite.mixin.client.bbs.MobFormRendererAccessor;

import java.util.List;

/** Opt-in in-client regression fixture. Every mutation is restored synchronously
 * before the actual shadow/world draw or a save. No GPU readback is performed. */
final class BbsMobSilhouetteChecks
{
    static void run(BbsMobSilhouette sampler, ModelBlockEntity block, float td)
    {
        MobForm form = (MobForm) block.getProperties().getForm();
        var baseline = sampler.sample(block, td);
        require(baseline.equals(sampler.sample(block, td)), "repeat evaluated state");
        var transform = form.transform.get();
        float x = transform.translate.x;
        try { transform.translate.x += 0.25f; require(!baseline.equals(sampler.sample(block, td)), "translation"); }
        finally { transform.translate.x = x; }
        Vector3f rotate2 = BbsSilhouetteBridge.rotate2(transform);
        if (rotate2 != null)
        {
            x = rotate2.x;
            try { rotate2.x += 0.25f; require(!baseline.equals(sampler.sample(block, td)), "second rotation"); }
            finally { rotate2.x = x; }
        }
        else
        {
            // BBS 2.5.2 replaced rotate2 with quaternion rotation storage.
            Transform saved = transform.copy();
            try
            {
                Transform.class.getMethod("setModeQuaternion").invoke(transform);
                BbsSilhouetteBridge.quat(transform).rotateY(0.3f);
                require(!baseline.equals(sampler.sample(block, td)), "quaternion rotation");
            }
            catch (ReflectiveOperationException failure)
            {
                throw new AssertionError("Caster revision regression: quaternion rotation probe", failure);
            }
            finally { transform.copy(saved); }
        }
        x = transform.scale.x;
        try { transform.scale.x *= 1.25f; require(!baseline.equals(sampler.sample(block, td)), "scale"); }
        finally { transform.scale.x = x; }
        var renderer = (MobFormRendererAccessor) FormUtilsClient.getRenderer(form);
        var entity = (VillagerEntity) renderer.irlite$entity();
        float yaw = entity.headYaw, prev = entity.prevHeadYaw;
        try
        {
            entity.headYaw += 20; entity.prevHeadYaw += 20;
            require(baseline.pose() != sampler.sample(block, td).pose(), "evaluated head vertices");
        }
        finally { entity.headYaw = yaw; entity.prevHeadYaw = prev; }
        var vanilla = (VillagerEntityRenderer) MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        var body = vanilla.getModel().getPart().getChild("body");
        boolean hidden = body.hidden;
        try { body.hidden = !hidden; require(baseline.pose() != sampler.sample(block, td).pose(), "actual geometry"); }
        finally { body.hidden = hidden; }
        checkPartRestoration(sampler, block, td, vanilla, entity);
        Link oldTexture = form.texture.get();
        try { form.texture.set(Link.assets("textures/unknown-revision-test.png")); require(!sampler.sample(block, td).known(), "unsupported texture"); }
        finally { form.texture.set(oldTexture); }
        require(baseline.equals(sampler.sample(block, td)), "all fixture state restored");
        ShadowResourceVersions.reloaded();
        require(baseline.resources() != sampler.sample(block, td).resources(), "resource reload");
        require(MobRenderContext.current() == null, "BBS pose cleanup");
        System.out.println("[irlite] caster-revision-checks: PASS 11 (evaluated Villager MobForm, all part fields restored)");
    }

    private static void checkPartRestoration(BbsMobSilhouette sampler, ModelBlockEntity block, float td,
                                             VillagerEntityRenderer vanilla, VillagerEntity entity)
    {
        var model = vanilla.getModel();
        List<PartSnapshot> original = model.getPart().traverse().map(PartSnapshot::new).toList();
        boolean child = model.child, riding = model.riding;
        float handSwing = model.handSwingProgress;
        int hurt = entity.hurtTime;
        try
        {
            int index = 0;
            for (PartSnapshot state : original)
            {
                ModelPart part = state.part;
                float value = ++index * 0.03125f;
                part.pivotX = -0.0f; part.pivotY = value; part.pivotZ = -value;
                part.pitch = value; part.yaw = -value; part.roll = value * 2;
                part.xScale = 1 + value; part.yScale = 1 - value * 0.25f; part.zScale = 1 + value * 0.5f;
                part.visible = true; part.hidden = false;
            }
            model.child = !child;
            model.riding = !riding;
            model.handSwingProgress = -0.0f;
            entity.hurtTime = hurt + 3;
            List<PartSnapshot> expected = model.getPart().traverse().map(PartSnapshot::new).toList();
            require(sampler.sample(block, td).known(), "supported all-parts restore fixture");
            for (PartSnapshot state : expected) state.check();
            require(model.child == !child && model.riding == !riding
                && same(model.handSwingProgress, -0.0f) && entity.hurtTime == hurt + 3, "model flags/entity restored");
        }
        finally
        {
            for (PartSnapshot state : original) state.restore();
            model.child = child; model.riding = riding; model.handSwingProgress = handSwing;
            entity.hurtTime = hurt;
        }
    }

    private record PartSnapshot(ModelPart part, float x, float y, float z, float pitch, float yaw, float roll,
                                float sx, float sy, float sz, boolean visible, boolean hidden)
    {
        PartSnapshot(ModelPart part) { this(part, part.pivotX, part.pivotY, part.pivotZ, part.pitch, part.yaw, part.roll,
            part.xScale, part.yScale, part.zScale, part.visible, part.hidden); }
        void check()
        {
            require(same(part.pivotX, x) && same(part.pivotY, y) && same(part.pivotZ, z)
                && same(part.pitch, pitch) && same(part.yaw, yaw) && same(part.roll, roll)
                && same(part.xScale, sx) && same(part.yScale, sy) && same(part.zScale, sz)
                && part.visible == visible && part.hidden == hidden, "all nine floats/two booleans restored");
        }
        void restore()
        {
            part.pivotX = x; part.pivotY = y; part.pivotZ = z;
            part.pitch = pitch; part.yaw = yaw; part.roll = roll;
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
            part.visible = visible; part.hidden = hidden;
        }
    }

    private static boolean same(float a, float b) { return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b); }

    private static void require(boolean condition, String name)
    {
        if (!condition) throw new AssertionError("Caster revision regression: " + name);
    }
}
