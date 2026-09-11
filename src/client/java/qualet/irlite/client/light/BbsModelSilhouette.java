package qualet.irlite.client.light;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.CasterRevision;
import qualet.irlite.mixin.client.bbs.FormStatePlayersAccessor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

/** Conservative BBS 2.3.1 adapter. Evaluates the supported cubic pose before the
 * bake, even when the model is off screen. Runtime animation/IK/physics/constraints,
 * state players, equipment, attachments and other renderers remain UNKNOWN.
 * In this supported subset every step AFTER applyPose is proven silhouette-neutral.
 * Never infer a known state merely from a stationary block or serialized Form. */
public final class BbsModelSilhouette
{
    static final boolean AUDITED = FabricLoader.getInstance().getModContainer("bbs")
        .map(mod -> "2.3.1-1.20.4".equals(mod.getMetadata().getVersion().getFriendlyString())).orElse(false);
    private final WeakHashMap<Object, Long> identities = new WeakHashMap<>();
    private final IdentityHashMap<ModelInstance, Long> geometryThisFrame = new IdentityHashMap<>();
    private final BbsModelPoseScratch poseScratch = new BbsModelPoseScratch();
    private long nextIdentity;

    public void beginFrame() { geometryThisFrame.clear(); }

    public CasterRevision sample(ModelBlockEntity block, float tickDelta)
    {
        if (!AUDITED || block.getProperties() == null || ModelIKDebug.enabled || ModelPhysicsDebug.enabled)
            return CasterRevision.UNKNOWN;
        if (!(block.getProperties().getForm() instanceof ModelForm form) || form.getClass() != ModelForm.class
            || !shadowlessChildren(form)
            || !((FormStatePlayersAccessor) form).irlite$statePlayers().isEmpty()
            || !empty(form.ik.get()) || !empty(form.physics.get()) || !empty(form.constraints.get())
            || !form.ikTargetOverrides.isEmpty() || !form.poleTargetOverrides.isEmpty()
            || !form.ikTargetWeights.isEmpty() || !form.poleTargetWeights.isEmpty()
            || !form.ikControlOverrides.isEmpty()
            || !form.physicsTargetOverrides.isEmpty() || !form.physicsTargetWeights.isEmpty()
            || !form.physicsControlOverrides.isEmpty() || form.windControlOverride != null)
            return CasterRevision.UNKNOWN;

        if (!(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer)
            || renderer.getClass() != ModelFormRenderer.class)
            return CasterRevision.UNKNOWN;
        // A cache read only: do not cause an off-screen model/texture to load.
        ModelInstance instance = BBSModClient.getModels().models.get(form.model.get());
        if (instance == null || instance.getClass() != ModelInstance.class
            || !(instance.model instanceof Model model) || model.getClass() != Model.class
            || instance.procedural || !instance.itemsMain.isEmpty() || !instance.itemsOff.isEmpty()
            || !instance.armorSlots.isEmpty() || instance.view != null)
            return CasterRevision.UNKNOWN;

        renderer.ensureAnimator(tickDelta);
        if (!(renderer.getAnimator() instanceof Animator animator) || animator.getClass() != Animator.class
            || animator.active != null || animator.lastActive != null || animator.basePre != null
            || animator.basePost != null || !animator.actions.isEmpty())
            return CasterRevision.UNKNOWN;

        Signature transform = new Signature().word(block.getPos().asLong()).transform(block.getProperties().getTransform())
            .transform(form.transform.get()).transform(form.transformOverlay.get());
        for (ValueTransform extra : form.additionalTransforms) transform.transform(extra.get());
        Signature morph = new Signature().word(identity(form)).vec(instance.scale);
        for (var entry : new TreeMap<>(form.shapeKeys.get().shapeKeys).entrySet())
            morph.string(entry.getKey()).number(entry.getValue());

        // Probe the ACTUAL cubic evaluator, preserving the shared model's old
        // mutable pose for other renderers. No animator tick or physics step runs.
        Signature pose = new Signature();
        var groups = model.getOrderedGroups();
        int groupCount = groups.size();
        BbsModelPoseScratch saved = poseScratch.acquire(groupCount);
        try
        {
            saved.capture(groups, groupCount);
            model.resetPose();
            model.applyPose(renderer.getPose());
            for (ModelGroup group : groups)
            {
                pose.string(group.id).transform(group.current).color(group.color).word(group.visible ? 1 : 0);
                if (group.orient == null) pose.word(0);
                else pose.word(1).number(group.orient.x).number(group.orient.y).number(group.orient.z).number(group.orient.w);
            }
        }
        finally
        {
            saved.release();
        }

        Signature material = new Signature().color(form.color.get()).word(form.visible.get() ? 1 : 0)
            .word(form.shaderShadow.get() ? 1 : 0).word(form.additiveColor.get() ? 1 : 0)
            .word(instance.culling ? 1 : 0);
        Link defaultTexture = form.texture.get() == null ? instance.texture : form.texture.get();
        if (!texture(material, defaultTexture)) return CasterRevision.UNKNOWN;
        // Iterate the actual VAO material keys, not just the editor's material list.
        if (instance.isVAORendered())
        {
            boolean singleMaterial = instance.materials.size() <= 1;
            Link fallback = singleMaterial ? defaultTexture : instance.texture;
            for (ModelGroup group : groups)
            {
                Map<String, ModelVAO> vaos = instance.getVaos().get(group);
                if (vaos == null) continue;
                for (String name : new TreeMap<>(vaos).keySet())
                {
                    Link link = singleMaterial ? defaultTexture : form.materialTextureOverrides.get(name);
                    if (!singleMaterial && link == null) link = form.materialTextures.getLink(name);
                    if (!singleMaterial && link == null) link = instance.getMaterialTexture(name, fallback);
                    // Null leaves the prior draw's texture bound: view/order dependent.
                    if (!texture(material.string(name), link)) return CasterRevision.UNKNOWN;
                }
            }
        }
        Long geometry = geometryThisFrame.get(instance);
        if (geometry == null)
        {
            Signature shape = new Signature().word(identity(instance)).word(identity(model))
                .word(model.textureWidth).word(model.textureHeight).word(instance.isVAORendered() ? 1 : 0);
            for (ModelGroup group : model.topGroups) geometry(shape, instance, group, 0);
            geometry = shape.value;
            geometryThisFrame.put(instance, geometry);
        }
        Object pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline == null) return CasterRevision.UNKNOWN;
        long resources = new Signature().word(ShadowResourceVersions.reloadVersion()).word(identity(pipeline)).value;
        return new CasterRevision(true, transform.value, pose.value, morph.value, geometry, material.value, resources);
    }

    private boolean texture(Signature sig, Link link)
    {
        var manager = BBSModClient.getTextures();
        if (link == null || manager.animatedTextures.containsKey(link)) return false;
        Texture texture = manager.textures.get(link);
        if (texture == null || texture.getClass() != Texture.class || !texture.isValid()
            || texture.getParent() != null || !(texture instanceof ShadowResourceRevision revision)
            || revision.irlite$shadowRevision() == 0) return false;
        sig.word(revision.irlite$shadowRevision()).word(texture.id).word(texture.width).word(texture.height);
        return true;
    }

    private void geometry(Signature sig, ModelInstance instance, ModelGroup group, int depth)
    {
        if (depth > 64 || group.getClass() != ModelGroup.class) throw new IllegalArgumentException("Unsupported model tree");
        sig.word(identity(group)).string(group.id).transform(group.initial).word(group.children.size());
        if (instance.isVAORendered())
        {
            var vaos = instance.getVaos().get(group);
            sig.word(vaos == null ? -1 : vaos.size());
            if (vaos != null) for (var entry : new TreeMap<>(vaos).entrySet())
            {
                if (entry.getValue().getClass() != ModelVAO.class
                    || !(entry.getValue() instanceof ShadowResourceRevision revision)
                    || revision.irlite$shadowRevision() == 0) throw new IllegalArgumentException("Unversioned VAO");
                sig.string(entry.getKey()).word(revision.irlite$shadowRevision());
            }
        }
        else
        {
            sig.word(group.cubes.size()).word(group.meshes.size());
            for (ModelCube cube : group.cubes)
            {
                sig.vec(cube.pivot).vec(cube.rotate).word(cube.quads.size());
                for (ModelQuad quad : cube.quads)
                {
                    sig.vec(quad.normal).word(quad.vertices.size());
                    for (ModelVertex vertex : quad.vertices) sig.vec(vertex.vertex).vec(vertex.uv);
                }
            }
            for (ModelMesh mesh : group.meshes)
            {
                sig.vec(mesh.origin).vec(mesh.rotate).string(mesh.material);
                data(sig, mesh.baseData);
                sig.word(mesh.data.size());
                for (var entry : new TreeMap<>(mesh.data).entrySet())
                {
                    sig.string(entry.getKey());
                    data(sig, entry.getValue());
                }
            }
        }
        for (ModelGroup child : group.children) geometry(sig, instance, child, depth + 1);
    }

    private static void data(Signature sig, ModelData data)
    {
        sig.word(data.vertices.size()).word(data.normals.size()).word(data.uvs.size());
        for (Vector3f v : data.vertices) sig.vec(v);
        for (Vector3f v : data.normals) sig.vec(v);
        for (Vector2f v : data.uvs) sig.vec(v);
    }

    private long identity(Object value) { return identities.computeIfAbsent(value, key -> ++nextIdentity); }
    private static boolean empty(BaseType value) { return value == null || value instanceof MapType map && map.isEmpty(); }

    static boolean shadowlessChildren(mchorse.bbs_mod.forms.forms.Form form)
    {
        for (var part : form.parts.getAllTyped())
        {
            var child = part.getForm();
            if (child == null) continue;
            if ((child.getClass() != qualet.irlite.forms.SpotlightForm.class
                && child.getClass() != qualet.irlite.forms.PointLightForm.class)
                || !child.parts.getAllTyped().isEmpty()
                || !((FormStatePlayersAccessor) child).irlite$statePlayers().isEmpty()) return false;
        }
        return true;
    }

    static final class Signature
    {
        long value = 0x9E3779B97F4A7C15L;
        Signature word(long word)
        {
            long z = value ^ word;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            value = z ^ (z >>> 31);
            return this;
        }
        Signature number(float v)
        {
            if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite silhouette");
            return word(Float.floatToIntBits(v));
        }
        Signature vec(Vector3f v) { return number(v.x).number(v.y).number(v.z); }
        Signature vec(Vector2f v) { return number(v.x).number(v.y); }
        Signature color(Color v) { return number(v.r).number(v.g).number(v.b).number(v.a); }
        Signature transform(Transform t) { return t == null ? word(0) : word(1).vec(t.translate).vec(t.rotate).vec(t.rotate2).vec(t.scale); }
        Signature string(String s)
        {
            word(s.length());
            for (int i = 0; i < s.length(); i++) word(s.charAt(i));
            return this;
        }
    }
}
