package qualet.irlite.client.light;

import com.sun.management.ThreadMXBean;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.*;

/** Real production scratch + BBS 2.3.1 evaluator + vanilla ModelPart, no stubs.
 * Dispatcher/Mixin integration remains covered by the opt-in in-client fixture. */
public final class BbsPoseScratchTest
{
    private static long assertions;
    private static volatile long sink;

    public static void main(String[] args) throws Exception
    {
        modelParity();
        modelFailures();
        modelNestedAndMutableLists();
        mobRestoration();
        mobCleanupFailures();
        allocations();
        System.out.println("BBS pose scratch PASS: " + assertions + " assertions; real BBS/JOML/ModelPart, no stubs");
    }

    private static void modelParity() throws Exception
    {
        BbsModelPoseScratch root = new BbsModelPoseScratch();
        Random random = new Random(0xBB5231);
        for (int count : new int[] {0, 1, 24, 96, 2, 96, 1, 128})
        {
            Model model = model(count);
            for (int frame = 0; frame < 24; frame++)
            {
                Collections.shuffle(model.getOrderedGroups(), random);
                Pose pose = new Pose();
                for (ModelGroup group : model.getOrderedGroups())
                {
                    randomize(group.initial, random);
                    randomize(group.current, random);
                    group.color.set(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat());
                    group.lighting = random.nextFloat();
                    group.orient = new Quaternionf(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat());
                    var transform = pose.get(group.id);
                    randomize(transform, random);
                    transform.color.set(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat());
                    transform.lighting = random.nextFloat();
                    transform.fix = random.nextFloat();
                }
                GroupSnapshot[] snapshots = snapshots(model.getOrderedGroups());
                long expected = oldPose(model, pose);
                checkGroups(snapshots);
                require(expected == newPose(root, model, pose), "evaluated pose signature parity");
                checkGroups(snapshots);
                require(expected == newPose(root, model, pose), "repeat always evaluates live pose");
                checkGroups(snapshots);
                assertModelReleased(root);
            }
        }
        // Same cardinality with entirely different group identities.
        Model model = model(24);
        newPose(root, model, new Pose());
        for (int i = 0; i < 24; i++) model.topGroups.set(i, new ModelGroup("replacement_" + i));
        model.initialize();
        GroupSnapshot[] snapshots = snapshots(model.getOrderedGroups());
        require(oldPose(model, new Pose()) == newPose(root, model, new Pose()), "replacement identities");
        checkGroups(snapshots);
        assertModelReleased(root);
    }

    private static void modelFailures() throws Exception
    {
        BbsModelPoseScratch root = new BbsModelPoseScratch();
        Model model = model(8);
        for (ModelGroup group : model.getOrderedGroups())
        {
            // Raw bits on the saved state must survive even exceptional samples.
            group.current.rotate2.x = Float.intBitsToFloat(0x7fc01234);
            group.current.scale.z = -0.0f;
            group.color.a = -0.0f;
            group.lighting = Float.intBitsToFloat(0xffc02345);
            group.orient = new Quaternionf(2, 3, 4, 5);
        }
        GroupSnapshot[] snapshots = snapshots(model.getOrderedGroups());
        List<ModelGroup> broken = new AbstractList<>() {
            public int size() { return model.getOrderedGroups().size(); }
            public ModelGroup get(int index) {
                if (index == 4) throw new IllegalStateException("setup");
                return model.getOrderedGroups().get(index);
            }
        };
        BbsModelPoseScratch scope = root.acquire(broken.size());
        try { expect(IllegalStateException.class, () -> scope.capture(broken, broken.size())); }
        finally { scope.release(); }
        checkGroups(snapshots);
        assertModelReleased(root);

        for (int failure = 0; failure < 4; failure++)
        {
            BbsModelPoseScratch saved = root.acquire(model.getOrderedGroups().size());
            try
            {
                saved.capture(model.getOrderedGroups(), model.getOrderedGroups().size());
                model.resetPose();
                switch (failure)
                {
                    case 0 -> expect(IllegalStateException.class, () -> { throw new IllegalStateException("getPose"); });
                    case 1 -> expect(NullPointerException.class, () -> model.applyPose(null));
                    case 2 -> {
                        model.getOrderedGroups().get(3).current.rotate2.z = Float.NaN;
                        expect(IllegalArgumentException.class, () -> signature(model));
                    }
                    case 3 -> {
                        Pose pose = new Pose();
                        pose.transforms.put(model.getOrderedGroups().get(3).id, null);
                        expect(NullPointerException.class, () -> model.applyPose(pose));
                    }
                    default -> throw new AssertionError();
                }
            }
            finally { saved.release(); }
            checkGroups(snapshots);
            assertModelReleased(root);
        }
        // Unsupported subclasses cannot consume reset-based reusable storage.
        List<ModelGroup> unsupported = new ArrayList<>(model.getOrderedGroups());
        unsupported.set(4, new ModelGroup("unsupported") {});
        BbsModelPoseScratch saved = root.acquire(unsupported.size());
        try { expect(IllegalArgumentException.class, () -> saved.capture(unsupported, unsupported.size())); }
        finally { saved.release(); }
        checkGroups(snapshots);
        assertModelReleased(root);
    }

    private static void modelNestedAndMutableLists() throws Exception
    {
        BbsModelPoseScratch root = new BbsModelPoseScratch();
        Model model = model(17);
        List<ModelGroup> groups = model.getOrderedGroups();
        GroupSnapshot[] original = snapshots(groups);
        BbsModelPoseScratch outer = root.acquire(groups.size());
        try
        {
            outer.capture(groups, groups.size());
            model.resetPose();
            model.getOrderedGroups().get(0).current.rotate2.set(3, 4, 5);
            GroupSnapshot[] evaluated = snapshots(groups);
            BbsModelPoseScratch inner = root.acquire(groups.size());
            require(inner != outer, "nested scope isolation");
            try { inner.capture(groups, groups.size()); model.resetPose(); }
            finally { inner.release(); }
            checkGroups(evaluated);
            // Restoration must not re-read this mutable list.
            groups.clear();
            groups.add(new ModelGroup("new_world_group"));
        }
        finally { outer.release(); }
        checkGroups(original);
        assertModelReleased(root);
        require(newPose(root, model(3), new Pose()) != 0, "reuse after world/model replacement");
        assertModelReleased(root);
    }

    private static void mobRestoration() throws Exception
    {
        BbsMobPoseScratch root = new BbsMobPoseScratch();
        Random random = new Random(0x12345678);
        for (int count : new int[] {0, 1, 24, 100, 2, 100, 24})
        {
            List<ModelPart> parts = parts(count);
            for (int frame = 0; frame < 16; frame++)
            {
                Collections.shuffle(parts, random);
                for (ModelPart part : parts) randomize(part, random);
                PartSnapshot[] original = partSnapshots(parts);
                BbsMobPoseScratch saved = root.acquire();
                try
                {
                    parts.stream().forEachOrdered(saved::capture);
                    for (ModelPart part : parts) randomize(part, random);
                    PartSnapshot[] evaluated = partSnapshots(parts);
                    BbsMobPoseScratch inner = root.acquire();
                    require(inner != saved, "nested mob scope isolation");
                    try { parts.stream().forEachOrdered(inner::capture); for (ModelPart part : parts) randomize(part, random); }
                    finally { inner.release(); }
                    checkParts(evaluated);
                    expect(IllegalStateException.class, () -> { throw new IllegalStateException("dispatcher/vertex sink"); });
                }
                finally { saved.release(); }
                checkParts(original);
                assertMobReleased(root);
            }
        }
        // Actual ModelPart traversal, including a replaced child at equal size.
        Map<String, ModelPart> children = new LinkedHashMap<>();
        children.put("a", parts(1).get(0)); children.put("b", parts(1).get(0));
        ModelPart parent = new ModelPart(List.of(), children);
        for (int frame = 0; frame < 3; frame++)
        {
            children.put("a", parts(1).get(0));
            List<ModelPart> live = parent.traverse().toList();
            for (ModelPart part : live) randomize(part, random);
            PartSnapshot[] original = partSnapshots(live);
            BbsMobPoseScratch saved = root.acquire();
            try { parent.traverse().forEachOrdered(saved::capture); for (ModelPart part : live) randomize(part, random); children.clear(); }
            finally { saved.release(); }
            checkParts(original);
            assertMobReleased(root);
        }
        BbsMobPoseScratch saved = root.acquire();
        ModelPart part = parts(1).get(0);
        PartSnapshot original = new PartSnapshot(part);
        try { saved.capture(part); expect(NullPointerException.class, () -> saved.capture(null)); part.pitch = 555; }
        finally { saved.release(); }
        original.check();
        assertMobReleased(root);
    }

    private static void mobCleanupFailures() throws Exception
    {
        BbsMobPoseScratch root = new BbsMobPoseScratch();
        ModelPart part = parts(1).get(0);
        for (int mask = 0; mask < 8; mask++) for (boolean rendererFailed : new boolean[] {false, true})
        {
            int failures = mask;
            PartSnapshot original = new PartSnapshot(part);
            BbsMobPoseScratch saved = root.acquire();
            saved.capture(part);
            part.pitch = 987.5f;
            List<String> trace = new ArrayList<>();
            Throwable primary = rendererFailed ? new IllegalArgumentException("renderer") : null;
            Throwable[] errors = {new IllegalStateException("pose"), new NoSuchMethodError("overlay"), new IllegalStateException("cache")};
            BbsMobPoseScratch.Cleanup cleanup = new BbsMobPoseScratch.Cleanup()
            {
                public void clearPose() { step(0, "pose"); }
                public void clearOverlay() { step(1, "overlay"); }
                public void clearCache()
                {
                    original.check(); // Parts must restore BEFORE cache cleanup.
                    step(2, "cache");
                }
                private void step(int index, String name)
                {
                    trace.add(name);
                    if ((failures & (1 << index)) != 0)
                    {
                        if (errors[index] instanceof RuntimeException error) throw error;
                        throw (Error) errors[index];
                    }
                }
            };
            Throwable thrown = null;
            try { saved.release(cleanup, primary); }
            catch (RuntimeException | Error error) { thrown = error; }
            require(trace.equals(List.of("pose", "overlay", "cache")), "all cleanup steps keep order after failure");
            List<Throwable> expected = new ArrayList<>();
            if (primary != null) expected.add(primary);
            for (int i = 0; i < 3; i++) if ((failures & (1 << i)) != 0) expected.add(errors[i]);
            if (rendererFailed) require(thrown == null, "cleanup preserves in-flight renderer exception");
            else require(thrown == (expected.isEmpty() ? null : expected.get(0)), "first cleanup error is propagated");
            if (!expected.isEmpty())
                require(Arrays.equals(expected.get(0).getSuppressed(), expected.subList(1, expected.size()).toArray(Throwable[]::new)), "all later failures suppressed in order");
            original.check();
            assertMobReleased(root);
            require(root.acquire() == root, "scope reusable after cleanup failure");
            root.release();
        }
    }

    private static long newPose(BbsModelPoseScratch root, Model model, Pose pose)
    {
        var groups = model.getOrderedGroups();
        BbsModelPoseScratch saved = root.acquire(groups.size());
        try { saved.capture(groups, groups.size()); model.resetPose(); model.applyPose(pose); return signature(model); }
        finally { saved.release(); }
    }

    /** The baseline snapshot/evaluation algorithm, retained only as the oracle. */
    private static long oldPose(Model model, Pose pose)
    {
        var groups = model.getOrderedGroups();
        Transform[] previous = new Transform[groups.size()];
        Color[] colors = new Color[groups.size()];
        float[] lighting = new float[groups.size()];
        Quaternionf[] orientations = new Quaternionf[groups.size()];
        for (int i = 0; i < groups.size(); i++)
        {
            ModelGroup group = groups.get(i);
            previous[i] = group.current; colors[i] = group.color;
            lighting[i] = group.lighting; orientations[i] = group.orient;
            group.current = new Transform(); group.color = new Color();
        }
        try { model.resetPose(); model.applyPose(pose); return signature(model); }
        finally
        {
            for (int i = 0; i < groups.size(); i++)
            {
                ModelGroup group = groups.get(i);
                group.current = previous[i]; group.color = colors[i];
                group.lighting = lighting[i]; group.orient = orientations[i];
            }
        }
    }

    private static long signature(Model model)
    {
        var pose = new BbsModelSilhouette.Signature();
        for (ModelGroup group : model.getOrderedGroups())
        {
            pose.string(group.id).transform(group.current).color(group.color).word(group.visible ? 1 : 0);
            if (group.orient == null) pose.word(0);
            else pose.word(1).number(group.orient.x).number(group.orient.y).number(group.orient.z).number(group.orient.w);
        }
        return pose.value;
    }

    private static void allocations()
    {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation measurement unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        Model model = model(24);
        Pose pose = new Pose();
        BbsModelPoseScratch modelScratch = new BbsModelPoseScratch();
        List<ModelPart> parts = parts(24);
        BbsMobPoseScratch mobScratch = new BbsMobPoseScratch();
        Runnable oldModel = () -> sink = oldPose(model, pose);
        Runnable newModel = () -> sink = newPose(modelScratch, model, pose);
        Runnable oldMob = () -> {
            List<PartSnapshot> saved = parts.stream().map(PartSnapshot::new).toList();
            parts.get(0).pivotX += 1;
            for (PartSnapshot state : saved) state.restore();
        };
        Runnable newMob = () -> {
            BbsMobPoseScratch saved = mobScratch.acquire();
            try { parts.stream().forEachOrdered(saved::capture); parts.get(0).pivotX += 1; }
            finally { saved.release(); }
        };
        for (int i = 0; i < 30_000; i++) { oldModel.run(); newModel.run(); oldMob.run(); newMob.run(); }
        double oldModelBytes = allocated(bean, oldModel), newModelBytes = allocated(bean, newModel);
        double oldMobBytes = allocated(bean, oldMob), newMobBytes = allocated(bean, newMob);
        require(newModelBytes < oldModelBytes, "measured model allocation reduction");
        require(newMobBytes < oldMobBytes, "measured mob allocation reduction");
        System.out.printf(Locale.ROOT, "Allocation bytes/sample, 24 groups/parts, 30k warmup + 20k measured: model %.1f -> %.1f; mob %.1f -> %.1f%n",
            oldModelBytes, newModelBytes, oldMobBytes, newMobBytes);
    }

    private static double allocated(ThreadMXBean bean, Runnable work)
    {
        long thread = Thread.currentThread().getId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20_000; i++) work.run();
        return (bean.getThreadAllocatedBytes(thread) - before) / 20_000.0;
    }

    private static Model model(int count)
    {
        Model model = new Model(null);
        for (int i = 0; i < count; i++) model.topGroups.add(new ModelGroup("group_" + i));
        model.initialize();
        return model;
    }

    private static List<ModelPart> parts(int count)
    {
        List<ModelPart> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new ModelPart(List.of(), Map.of()));
        return result;
    }

    private static void randomize(Transform value, Random random)
    {
        value.translate.set(random.nextFloat(), random.nextFloat(), random.nextFloat());
        value.rotate.set(random.nextFloat() * 270, random.nextFloat() * 270, random.nextFloat() * 270);
        value.rotate2.set(random.nextFloat() * 270, random.nextFloat() * 270, random.nextFloat() * 270);
        value.scale.set(random.nextFloat() * 3, random.nextFloat() * 3, random.nextFloat() * 3);
    }

    private static void randomize(ModelPart part, Random random)
    {
        part.pivotX = Float.intBitsToFloat(random.nextInt()); part.pivotY = -0.0f; part.pivotZ = random.nextFloat();
        part.pitch = random.nextFloat(); part.yaw = Float.intBitsToFloat(0x7fc01234); part.roll = random.nextFloat();
        part.xScale = random.nextFloat(); part.yScale = Float.NEGATIVE_INFINITY; part.zScale = random.nextFloat();
        part.visible = random.nextBoolean(); part.hidden = random.nextBoolean();
    }

    private static GroupSnapshot[] snapshots(List<ModelGroup> groups) { return groups.stream().map(GroupSnapshot::new).toArray(GroupSnapshot[]::new); }
    private static PartSnapshot[] partSnapshots(List<ModelPart> parts) { return parts.stream().map(PartSnapshot::new).toArray(PartSnapshot[]::new); }
    private static void checkGroups(GroupSnapshot[] snapshots) { for (GroupSnapshot state : snapshots) state.check(); }
    private static void checkParts(PartSnapshot[] snapshots) { for (PartSnapshot state : snapshots) state.check(); }

    private record GroupSnapshot(ModelGroup group, Transform transform, Color color, Quaternionf orient, int[] bits)
    {
        GroupSnapshot(ModelGroup group) { this(group, group.current, group.color, group.orient, groupBits(group)); }
        void check()
        {
            require(group.current == transform && group.color == color && group.orient == orient, "original group refs");
            require(Arrays.equals(bits, groupBits(group)), "all group float fields bit-exact");
        }
    }

    private static int[] groupBits(ModelGroup group)
    {
        Transform t = group.current; Color c = group.color; Quaternionf o = group.orient;
        return bits(t.translate.x, t.translate.y, t.translate.z, t.rotate.x, t.rotate.y, t.rotate.z,
            t.rotate2.x, t.rotate2.y, t.rotate2.z, t.scale.x, t.scale.y, t.scale.z, c.r, c.g, c.b, c.a, group.lighting,
            o == null ? 0 : o.x, o == null ? 0 : o.y, o == null ? 0 : o.z, o == null ? 0 : o.w);
    }

    /** Same eleven saved fields as the baseline Mob PartState (no int[] per snapshot). */
    private record PartSnapshot(ModelPart part, float x, float y, float z, float pitch, float yaw, float roll,
                                float sx, float sy, float sz, boolean visible, boolean hidden)
    {
        PartSnapshot(ModelPart part) { this(part, part.pivotX, part.pivotY, part.pivotZ, part.pitch, part.yaw, part.roll,
            part.xScale, part.yScale, part.zScale, part.visible, part.hidden); }
        void check()
        {
            require(Arrays.equals(bits(x, y, z, pitch, yaw, roll, sx, sy, sz),
                bits(part.pivotX, part.pivotY, part.pivotZ, part.pitch, part.yaw, part.roll, part.xScale, part.yScale, part.zScale)), "nine part floats bit-exact");
            require(part.visible == visible && part.hidden == hidden, "two part booleans");
        }
        void restore()
        {
            part.pivotX = x; part.pivotY = y; part.pivotZ = z;
            part.pitch = pitch; part.yaw = yaw; part.roll = roll;
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
            part.visible = visible; part.hidden = hidden;
        }
    }

    private static int[] bits(float... values)
    {
        int[] bits = new int[values.length];
        for (int i = 0; i < values.length; i++) bits[i] = Float.floatToRawIntBits(values[i]);
        return bits;
    }

    private static void assertModelReleased(BbsModelPoseScratch scope) throws Exception
    {
        require(!(boolean) field(scope, "active") && (int) field(scope, "captured") == 0, "model scope released");
        for (Object state : (Object[]) field(scope, "states"))
            for (String field : new String[] {"group", "current", "color", "orient"}) require(field(state, field) == null, "no retained model references");
        if (field(scope, "nested") instanceof BbsModelPoseScratch nested) assertModelReleased(nested);
    }

    private static void assertMobReleased(BbsMobPoseScratch scope) throws Exception
    {
        require(!(boolean) field(scope, "active") && (int) field(scope, "captured") == 0, "mob scope released");
        for (Object state : (Object[]) field(scope, "states")) require(field(state, "part") == null, "no retained part references");
        if (field(scope, "nested") instanceof BbsMobPoseScratch nested) assertMobReleased(nested);
    }

    private static Object field(Object owner, String name) throws Exception
    {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static void expect(Class<? extends Throwable> type, Runnable work)
    {
        try { work.run(); } catch (Throwable failure) { require(type.isInstance(failure), "expected " + type + ", got " + failure); return; }
        throw new AssertionError("Expected " + type);
    }

    private static void require(boolean value, String description)
    {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
}
