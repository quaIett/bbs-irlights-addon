package qualet.irlite.client.light;

import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/** Call-scoped storage, never a cache of a model's pose or group order. */
final class BbsModelPoseScratch
{
    private BbsModelPoseScratch nested;
    private boolean active;
    private State[] states = new State[0];
    private int captured;

    BbsModelPoseScratch acquire(int capacity)
    {
        if (active)
        {
            if (nested == null) nested = new BbsModelPoseScratch();
            return nested.acquire(capacity);
        }
        // Finish all growth before any shared group is changed. Assign the new
        // array only after construction succeeds, so a failed growth is reusable.
        if (states.length < capacity)
        {
            State[] grown = Arrays.copyOf(states, Math.max(capacity, states.length * 2 + 8));
            for (int i = states.length; i < grown.length; i++) grown[i] = new State();
            states = grown;
        }
        active = true;
        return this;
    }

    void capture(List<ModelGroup> groups, int count)
    {
        for (int i = 0; i < count; i++)
        {
            ModelGroup group = groups.get(i);
            // Only this exact audited reset() fully initializes our isolated
            // Transform/Color. Geometry already rejects other group classes.
            if (group.getClass() != ModelGroup.class) throw new IllegalArgumentException("Unsupported model group");
            State state = states[i];
            state.group = group;
            state.current = group.current;
            state.color = group.color;
            state.overlay = group.overlay;
            state.poseVisible = group.poseVisible;
            state.lighting = group.lighting;
            state.orient = group.orient;
            // BBS 2.5.2 IK stretch shift; reset() nulls it, so it is restored like orient.
            state.offset = BbsSilhouetteBridge.offset(group);
            captured = i + 1;
            group.current = state.isolatedTransform;
            group.color = state.isolatedColor;
            // BBS 2.6: reset()/applyPose write the pose overlay and pose visibility too.
            group.overlay = state.isolatedOverlay;
        }
    }

    void release()
    {
        // Use captured identities even if getPose/applyPose changed the live
        // list. Reverse restoration also unwinds any duplicate identities.
        while (captured > 0)
        {
            State state = states[--captured];
            state.group.current = state.current;
            state.group.color = state.color;
            state.group.overlay = state.overlay;
            state.group.poseVisible = state.poseVisible;
            state.group.lighting = state.lighting;
            state.group.orient = state.orient;
            BbsSilhouetteBridge.setOffset(state.group, state.offset);
            state.group = null;
            state.current = null;
            state.color = null;
            state.overlay = null;
            state.orient = null;
            state.offset = null;
        }
        active = false;
    }

    private static final class State
    {
        final Transform isolatedTransform = new Transform();
        final Color isolatedColor = new Color();
        final Color isolatedOverlay = new Color();
        ModelGroup group;
        Transform current;
        Color color;
        Color overlay;
        boolean poseVisible;
        float lighting;
        Quaternionf orient;
        Vector3f offset;
    }
}
