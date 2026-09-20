package qualet.irlite.client.light;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.qualet.irl.light.shadow.CasterRevision;

import java.util.List;
import java.util.Map;

/** Opt-in in-client regression fixture for the cubic ModelForm sampler, the twin of
 * {@link BbsMobSilhouetteChecks} ({@code -Dirlite.checkCasterRevisions=true}). Every
 * mutation is restored synchronously before the actual shadow/world draw or a save. */
final class BbsModelSilhouetteChecks
{
    private static final String PROBE_KEY = "irlite$revision-probe";

    static void run(BbsModelSilhouette sampler, ModelBlockEntity block, float td)
    {
        ModelForm form = (ModelForm) block.getProperties().getForm();
        ModelInstance instance = BBSModClient.getModels().models.get(form.model.get());
        Model model = (Model) instance.model;
        List<ModelGroup> groups = model.getOrderedGroups();
        require(!groups.isEmpty(), "model has groups");
        int checks = 0;

        CasterRevision baseline = sampler.sample(block, td);
        require(baseline.known() && baseline.equals(sampler.sample(block, td)), "repeat evaluated state");
        checks++;

        Transform blockTransform = block.getProperties().getTransform();
        if (blockTransform != null)
        {
            float x = blockTransform.translate.x;
            try
            {
                blockTransform.translate.x += 0.25f;
                require(baseline.transform() != sampler.sample(block, td).transform(), "block translation");
                checks++;
            }
            finally { blockTransform.translate.x = x; }
        }
        Transform formTransform = form.transform.get();
        float yaw = formTransform.rotate.y;
        try
        {
            formTransform.rotate.y += 10f;
            require(baseline.transform() != sampler.sample(block, td).transform(), "form rotation");
            checks++;
        }
        finally { formTransform.rotate.y = yaw; }

        // The evaluated pose: mutate the form pose of the first bone and read it back through
        // the real getPose/resetPose/applyPose path (both rotation representations on 2.5.2).
        Pose pose = form.pose.get();
        String bone = groups.get(0).id;
        boolean existed = pose.transforms.containsKey(bone);
        PoseTransform poseTransform = pose.get(bone);
        Transform saved = poseTransform.copy();
        try
        {
            poseTransform.rotate.x += 0.5f;
            require(baseline.pose() != sampler.sample(block, td).pose(), "evaluated bone rotation");
            checks++;
            poseTransform.copy(saved);
            poseTransform.translate.y += 0.25f;
            require(baseline.pose() != sampler.sample(block, td).pose(), "evaluated bone translation");
            checks++;
            poseTransform.copy(saved);
            if (BbsSilhouetteBridge.QUAT != null)
            {
                Transform.class.getMethod("setModeQuaternion").invoke(poseTransform);
                CasterRevision quaternion = sampler.sample(block, td);
                require(quaternion.known(), "quaternion pose supported");
                BbsSilhouetteBridge.quat(poseTransform).rotateY(0.3f);
                require(quaternion.pose() != sampler.sample(block, td).pose(), "quaternion bone rotation");
                checks++;
                poseTransform.copy(saved);
            }
        }
        catch (ReflectiveOperationException failure)
        {
            throw new AssertionError("Caster revision regression: quaternion pose probe", failure);
        }
        finally
        {
            poseTransform.copy(saved);
            if (!existed) pose.transforms.remove(bone);
        }

        // BBS 2.7: the recording settings grow a form extra pose overlay tracks, and the
        // renderer folds them into the pose it hands us. A change in one has to move the
        // signature, or an animated overlay would draw against a frozen shadow.
        int poseOverlays = mchorse.bbs_mod.BBSSettings.recordingPoseOverlays.get();
        try
        {
            mchorse.bbs_mod.BBSSettings.recordingPoseOverlays.set(Math.max(1, poseOverlays));
            form.syncOverlayTracks();
            require(!form.additionalOverlays.isEmpty(), "pose overlay track created");
            checks++;
            form.additionalOverlays.get(0).get().getOrCreate(bone).translate.y += 0.35f;
            require(baseline.pose() != sampler.sample(block, td).pose(), "pose overlay track");
            checks++;
        }
        finally
        {
            mchorse.bbs_mod.BBSSettings.recordingPoseOverlays.set(0);
            form.syncOverlayTracks();
            mchorse.bbs_mod.BBSSettings.recordingPoseOverlays.set(poseOverlays);
            form.syncOverlayTracks();
        }

        require(BbsSilhouetteBridge.poseListeners() == 0, "pose event listeners readable and empty");
        checks++;

        // The probe must leave the shared model's live pose untouched, including a live
        // 2.5.2 IK stretch offset, which the probe's own reset must neither leak nor keep.
        ModelGroup first = groups.get(0);
        Vector3f liveOffset = BbsSilhouetteBridge.OFFSET != null ? new Vector3f(0.125f, 0f, 0f) : null;
        if (liveOffset != null) BbsSilhouetteBridge.setOffset(first, liveOffset);
        try
        {
            List<GroupSnapshot> live = groups.stream().map(GroupSnapshot::of).toList();
            CasterRevision sampled = sampler.sample(block, td);
            require(sampled.known(), "supported restore fixture");
            for (GroupSnapshot snapshot : live) snapshot.check();
            checks++;
            if (liveOffset != null)
            {
                require(sampled.equals(baseline), "live offset isolated from the evaluated pose");
                checks++;
            }
        }
        finally
        {
            if (liveOffset != null) BbsSilhouetteBridge.setOffset(first, null);
        }

        Map<String, Float> shapeKeys = form.shapeKeys.get().shapeKeys;
        require(!shapeKeys.containsKey(PROBE_KEY), "probe shape key unused");
        try
        {
            shapeKeys.put(PROBE_KEY, 0.5f);
            require(baseline.morph() != sampler.sample(block, td).morph(), "shape key");
            checks++;
        }
        finally { shapeKeys.remove(PROBE_KEY); }

        Color color = form.color.get();
        float alpha = color.a;
        try
        {
            color.a = alpha > 0.5f ? 0.25f : 0.75f;
            require(baseline.material() != sampler.sample(block, td).material(), "form color");
            checks++;
        }
        finally { color.a = alpha; }

        boolean visible = form.visible.get();
        try
        {
            form.visible.set(!visible);
            require(baseline.material() != sampler.sample(block, td).material(), "visibility");
            checks++;
        }
        finally { form.visible.set(visible); }

        try
        {
            form.ikTargetOverrides.put(PROBE_KEY, new org.joml.Vector3f());
            require(!sampler.sample(block, td).known(), "unsupported runtime IK target");
            checks++;
        }
        finally { form.ikTargetOverrides.remove(PROBE_KEY); }

        int layer = form.renderLayer.get();
        try
        {
            form.renderLayer.set(layer == mchorse.bbs_mod.forms.forms.Form.LAYER_CUTOUT
                ? mchorse.bbs_mod.forms.forms.Form.LAYER_SOLID : mchorse.bbs_mod.forms.forms.Form.LAYER_CUTOUT);
            require(baseline.material() != sampler.sample(block, td).material(), "render layer");
            checks++;
        }
        finally { form.renderLayer.set(layer); }

        require(baseline.equals(sampler.sample(block, td)), "all fixture state restored");
        checks++;
        ShadowResourceVersions.reloaded();
        require(baseline.resources() != sampler.sample(block, td).resources(), "resource reload");
        checks++;
        System.out.println("[irlite] caster-revision-checks(cubic): PASS " + checks
            + " (evaluated cubic ModelForm, live pose restored, bbs " + BbsSilhouetteBridge.BBS_VERSION + ")");
    }

    private record GroupSnapshot(ModelGroup group, Transform current, Transform values, Color color,
                                 float r, float g, float b, float a, float lighting, Quaternionf orient, Vector3f offset)
    {
        static GroupSnapshot of(ModelGroup group)
        {
            return new GroupSnapshot(group, group.current, group.current.copy(), group.color,
                group.color.r, group.color.g, group.color.b, group.color.a, group.lighting, group.orient,
                BbsSilhouetteBridge.offset(group));
        }

        void check()
        {
            require(group.current == current && group.color == color, "live pose objects restored");
            require(group.current.equals(values), "live transform values untouched");
            require(same(group.color.r, r) && same(group.color.g, g) && same(group.color.b, b) && same(group.color.a, a),
                "live color untouched");
            require(same(group.lighting, lighting) && group.orient == orient && BbsSilhouetteBridge.offset(group) == offset,
                "live orient/offset/lighting untouched");
        }
    }

    private static boolean same(float a, float b) { return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b); }

    private static void require(boolean condition, String name)
    {
        if (!condition) throw new AssertionError("Caster revision regression (cubic): " + name);
    }
}
