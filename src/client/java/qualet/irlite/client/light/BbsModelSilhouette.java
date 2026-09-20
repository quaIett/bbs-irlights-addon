package qualet.irlite.client.light;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormBone;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormMaterialLevels;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import net.irisshaders.iris.Iris;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.CasterRevision;
import qualet.irlite.mixin.client.bbs.FormStatePlayersAccessor;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

/** Conservative BBS adapter, audited on 2.6-1.20.1 and 2.6-1.20.4 (the drifted
 * members go through {@link BbsSilhouetteBridge}). Evaluates the supported cubic pose
 * before the bake, even when the model is off screen. Runtime animation/IK/physics/
 * constraints, state players, equipment, attachments, look-at, welds, the hybrid
 * VAO+CPU path and other renderers remain UNKNOWN. In this supported subset every step
 * AFTER applyPose is proven silhouette-neutral. Never infer a known state merely from a
 * stationary block or serialized Form. */
public final class BbsModelSilhouette
{
    static final boolean AUDITED = BbsSilhouetteBridge.AUDITED;
    private final WeakHashMap<Object, Long> identities = new WeakHashMap<>();
    private final IdentityHashMap<ModelInstance, Long> geometryThisFrame = new IdentityHashMap<>();
    private final BbsModelPoseScratch poseScratch = new BbsModelPoseScratch();
    private long nextIdentity;
    private boolean checked;

    public void beginFrame() { geometryThisFrame.clear(); }

    public CasterRevision sample(ModelBlockEntity block, float tickDelta)
    {
        CasterRevision revision = sampleEvaluated(block, tickDelta);
        if (!checked && revision.known() && Boolean.getBoolean("irlite.checkCasterRevisions"))
        {
            checked = true;
            BbsModelSilhouetteChecks.run(this, block, tickDelta);
        }
        return revision;
    }

    /** Opt-in trace of why a caster stays UNKNOWN ({@code -Dirlite.debugCasterRevisions=true}):
     * each distinct reason prints once per sampler, so "no reuse" is never silent. */
    public static final boolean DEBUG = Boolean.getBoolean("irlite.debugCasterRevisions");
    private final HashSet<String> reported = new HashSet<>();

    private CasterRevision unknown(String reason)
    {
        if (DEBUG && reported.add(reason)) System.out.println("[irlite] caster-revision(cubic): UNKNOWN because " + reason);
        return CasterRevision.UNKNOWN;
    }

    private CasterRevision sampleEvaluated(ModelBlockEntity block, float tickDelta)
    {
        if (!AUDITED) return unknown("BBS version/layout not audited");
        // BBS 2.7: an addon on a pose event may move the form, its bones or its anchor from state
        // of its own, which no signature of ours sees.
        int poseListeners = BbsSilhouetteBridge.poseListeners();
        if (poseListeners != 0) return unknown("addon pose listeners registered: " + poseListeners);
        if (block.getProperties() == null) return unknown("model block without properties");
        if (BbsSilhouetteBridge.debugOverlays()) return unknown("IK/physics debug overlay enabled");
        if (!(block.getProperties().getForm() instanceof ModelForm form) || form.getClass() != ModelForm.class)
            return unknown("form is not a plain ModelForm");
        if (!shadowlessChildren(form)) return unknown("body parts other than leaf light forms");
        if (!((FormStatePlayersAccessor) form).irlite$statePlayers().isEmpty()) return unknown("animation state players active");
        if (configuredBones(form)) return unknown("per-bone IK/physics/constraints configured");
        if (!form.ikTargetOverrides.isEmpty() || !form.poleTargetOverrides.isEmpty()
            || !form.ikTargetWeights.isEmpty() || !form.poleTargetWeights.isEmpty()
            || !form.physicsTargetOverrides.isEmpty() || !form.physicsTargetWeights.isEmpty())
            return unknown("runtime IK/physics target overrides");

        if (!(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer)
            || renderer.getClass() != ModelFormRenderer.class)
            return unknown("renderer is not the plain ModelFormRenderer");
        // A cache read only: do not cause an off-screen model/texture to load.
        ModelInstance instance = BBSModClient.getModels().models.get(form.model.get());
        if (instance == null) return unknown("model not loaded: " + form.model.get());
        if (instance.getClass() != ModelInstance.class) return unknown("subclassed ModelInstance");
        if (!(instance.model instanceof Model model) || model.getClass() != Model.class)
            return unknown("non-cubic model (BOBJ/other): " + form.model.get());
        // A .jem model's CEM program drives visibility and pose outside the audited applyPose.
        if (instance.cemAnimation != null) return unknown("CEM animated model: " + form.model.get());
        if (BbsSilhouetteBridge.procedural(instance)) return unknown("procedural model: " + form.model.get());
        if (!BbsSilhouetteBridge.itemsMain(instance).isEmpty() || !BbsSilhouetteBridge.itemsOff(instance).isEmpty()
            || !BbsSilhouetteBridge.armorSlots(instance).isEmpty())
            return unknown("item/armor slots configured: " + form.model.get());
        if (BbsSilhouetteBridge.view(instance) != null) return unknown("look-at configured: " + form.model.get());
        // BBS 2.5.2: welded seams deform cubes per pose, and the hybrid path mixes GPU VAO
        // draws (per-material textures) with CPU draws of the same model. A partially baked
        // model (shape-keyed meshes: VAOs present yet not VAO-rendered) is that hybrid path.
        if (!BbsSilhouetteBridge.weldBindings(instance).isEmpty()) return unknown("welds configured: " + form.model.get());
        if (!instance.getVaos().isEmpty() && !instance.isVAORendered()) return unknown("hybrid VAO+CPU render path: " + form.model.get());
        Vector3f configScale = BbsSilhouetteBridge.scale(instance);
        if (configScale == null) return unknown("config scale unavailable");

        renderer.ensureAnimator(tickDelta);
        if (!(renderer.getAnimator() instanceof Animator animator) || animator.getClass() != Animator.class)
            return unknown("animator is not the plain Animator");
        if (animator.active != null || animator.lastActive != null || animator.basePre != null
            || animator.basePost != null || !animator.actions.isEmpty())
            return unknown("animator actions active");

        Signature transform = new Signature().word(block.getPos().asLong()).transform(block.getProperties().getTransform())
            .transform(form.transform.get()).transform(form.transformOverlay.get());
        for (ValueTransform extra : form.additionalTransforms) transform.transform(extra.get());
        Signature morph = new Signature().word(identity(form)).vec(configScale);
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
            // BBS 2.6 channels phase: the model config's default pose sits under the form's.
            model.applyPose(instance.getDefaultPose());
            model.applyPose(renderer.getPose());
            for (ModelGroup group : groups)
            {
                pose.string(group.id).transform(group.current).color(group.color).color(group.overlay)
                    .word(group.visible ? 1 : 0).word(group.poseVisible ? 1 : 0);
                if (group.orient == null) pose.word(0);
                else pose.word(1).number(group.orient.x).number(group.orient.y).number(group.orient.z).number(group.orient.w);
                // 2.5.2 IK stretch shift (render matrix input ahead of the bone's own translate).
                Vector3f offset = BbsSilhouetteBridge.offset(group);
                if (offset == null) pose.word(0);
                else pose.word(1).vec(offset);
            }
        }
        finally
        {
            saved.release();
        }

        Signature material = new Signature().color(form.color.get()).word(form.visible.get() ? 1 : 0)
            .word(form.shaderShadow.get() ? 1 : 0).word(form.additiveColor.get() ? 1 : 0)
            .word(BbsSilhouetteBridge.culling(instance) ? 1 : 0).word(form.renderLayer.get());
        // BBS 2.6 material levels: visibility, face culling and the multiply colour reach every pass.
        for (String name : new java.util.TreeSet<String>(instance.materials))
        {
            material.string(name).word(FormMaterialLevels.materialVisible(form, name) ? 1 : 0)
                .word(FormMaterialLevels.materialCulling(form, name));
            Color tint = FormMaterialLevels.materialColor(form, name);
            if (tint == null) material.word(0);
            else material.word(1).color(tint);
        }
        Link modelTexture = BbsSilhouetteBridge.texture(instance);
        Link defaultTexture = form.texture.get() == null ? modelTexture : form.texture.get();
        if (!texture(material, defaultTexture)) return unknown("default texture not loaded/versioned: " + defaultTexture);
        // Iterate the actual VAO material keys, not just the editor's material list.
        if (instance.isVAORendered())
        {
            boolean singleMaterial = instance.materials.size() <= 1;
            Link fallback = singleMaterial ? defaultTexture : modelTexture;
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
                    if (!texture(material.string(name), link)) return unknown("material texture not loaded/versioned: " + name + " -> " + link);
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
        if (pipeline == null) return unknown("no Iris pipeline");
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

    /** BBS 2.6 keeps IK/physics chains, joint limits and constraints per bone; the runtimes only
     * compile non-default bones, so an all-default set proves no constraint stage runs. */
    private static boolean configuredBones(ModelForm form)
    {
        for (var value : form.bones.getAll())
            if (value instanceof FormBone bone && !bone.isDefault()) return true;
        return false;
    }

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
        /** Rotation storage drifts by BBS version: rotate + rotate2 euler triples (2.3.1) or
         * a rotation mode with the quaternion (2.5.2); both representations are hashed whole. */
        Signature transform(Transform t)
        {
            if (t == null) return word(0);
            word(1).vec(t.translate).vec(t.rotate).vec(t.scale);
            Vector3f rotate2 = BbsSilhouetteBridge.rotate2(t);
            if (rotate2 != null) return vec(rotate2);
            word(BbsSilhouetteBridge.rotationMode(t));
            Quaternionf quat = BbsSilhouetteBridge.quat(t);
            return quat == null ? this : number(quat.x).number(quat.y).number(quat.z).number(quat.w);
        }
        Signature string(String s)
        {
            word(s.length());
            for (int i = 0; i < s.length(); i++) word(s.charAt(i));
            return this;
        }
    }
}
