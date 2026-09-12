package qualet.irlite.client.light;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.model.View;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-time reflective bridge over the BBS members the silhouette samplers read and
 * that drifted between the audited BBS releases. BBS 2.3.1 exposes the ModelInstance
 * config as public fields and Transform carries a second euler triple (rotate2);
 * BBS 2.5.2 moved the config behind getters, replaced rotate2 with a rotation mode
 * plus quaternion, added the IK stretch offset on ModelGroup and welds/hybrid rendering
 * on ModelInstance. One addon jar spans both (the UITrackpad#limit precedent), so each
 * access resolves ONCE here to a MethodHandle, or to null when the member is absent.
 *
 * <p>{@link #AUDITED} is true only for a listed BBS version whose member layout matches
 * what that version's silhouette audit saw; a same-version build with a different layout
 * stays UNKNOWN (the IrliteBbsCompat precedent). A missing member is never defaulted to a
 * value on the sampling path.</p>
 */
public final class BbsSilhouetteBridge
{
    public static final String BBS_VERSION = FabricLoader.getInstance().getModContainer("bbs")
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    /* ModelInstance config: 2.3.1 public field -> 2.5.2 getter. */
    static final MethodHandle PROCEDURAL = accessor(ModelInstance.class, "procedural", "isProcedural", boolean.class);
    static final MethodHandle CULLING = accessor(ModelInstance.class, "culling", "isCulling", boolean.class);
    static final MethodHandle SCALE = accessor(ModelInstance.class, "scale", "getScale", Vector3f.class);
    static final MethodHandle TEXTURE = accessor(ModelInstance.class, "texture", "getTexture", Link.class);
    static final MethodHandle VIEW = accessor(ModelInstance.class, "view", "getView", View.class);
    static final MethodHandle ITEMS_MAIN = accessor(ModelInstance.class, "itemsMain", "getItemsMain", List.class);
    static final MethodHandle ITEMS_OFF = accessor(ModelInstance.class, "itemsOff", "getItemsOff", List.class);
    static final MethodHandle ARMOR_SLOTS = accessor(ModelInstance.class, "armorSlots", "getArmorSlots", Map.class);
    /* 2.5.2 only: resolved welds (hybrid VAO + CPU seam deformation). */
    static final MethodHandle WELD_BINDINGS = method(ModelInstance.class, "getWeldBindings", List.class);

    /* Transform rotation storage: 2.3.1 rotate + rotate2 euler triples; 2.5.2 mode + quaternion. */
    static final MethodHandle ROTATE2 = field(Transform.class, "rotate2", Vector3f.class);
    static final MethodHandle ROTATION_MODE = field(Transform.class, "rotationMode", Object.class);
    static final MethodHandle QUAT = field(Transform.class, "quat", Quaternionf.class);

    /* 2.5.2 only: transient IK stretch shift, applied in the render matrix before the bone's translate. */
    static final MethodHandle OFFSET = field(ModelGroup.class, "offset", Vector3f.class);
    static final MethodHandle OFFSET_SETTER = setter(ModelGroup.class, "offset", Vector3f.class);

    /* 2.3.1 only: global debug overlays drawn by the model renderer. 2.5.2 gates its overlays on
     * a compiled IK/physics config, which the sampler already requires to be empty. */
    static final MethodHandle IK_DEBUG = staticFlag(ModelIKDebug.class, "enabled");
    static final MethodHandle PHYSICS_DEBUG = staticFlag(ModelPhysicsDebug.class, "enabled");

    /** True only for an audited BBS version whose drifted members resolved exactly as audited. */
    public static final boolean AUDITED = audit();

    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean();

    private BbsSilhouetteBridge() {}

    private static boolean audit()
    {
        List<String> missing = new ArrayList<>();
        require(missing, "ModelInstance.procedural", PROCEDURAL);
        require(missing, "ModelInstance.culling", CULLING);
        require(missing, "ModelInstance.scale", SCALE);
        require(missing, "ModelInstance.texture", TEXTURE);
        require(missing, "ModelInstance.view", VIEW);
        require(missing, "ModelInstance.itemsMain", ITEMS_MAIN);
        require(missing, "ModelInstance.itemsOff", ITEMS_OFF);
        require(missing, "ModelInstance.armorSlots", ARMOR_SLOTS);
        boolean legacyLayout = ROTATE2 != null && ROTATION_MODE == null && QUAT == null && OFFSET == null && WELD_BINDINGS == null;
        boolean modernLayout = ROTATE2 == null && ROTATION_MODE != null && QUAT != null
            && OFFSET != null && OFFSET_SETTER != null && WELD_BINDINGS != null;
        String layout = switch (BBS_VERSION)
        {
            case "2.3.1-1.20.4" -> legacyLayout ? null : "2.3.1 layout (Transform.rotate2, no ModelGroup.offset/welds)";
            case "2.5.2-1.20.4" -> modernLayout ? null : "2.5.2 layout (Transform.rotationMode/quat, ModelGroup.offset, ModelInstance.getWeldBindings)";
            default -> "unaudited BBS version";
        };
        if (layout != null) missing.add(layout);
        boolean audited = missing.isEmpty();
        System.out.println("[irlite] caster-revision bridge: bbs " + BBS_VERSION
            + (audited ? " audited" : " NOT audited (revisions stay UNKNOWN): " + String.join(", ", missing)));
        return audited;
    }

    private static void require(List<String> missing, String name, MethodHandle handle)
    {
        if (handle == null) missing.add(name);
    }

    /* ---- resolution (once, at class init) ---- */

    private static MethodHandle accessor(Class<?> owner, String fieldName, String getterName, Class<?> type)
    {
        MethodHandle handle = field(owner, fieldName, type);
        return handle != null ? handle : method(owner, getterName, type);
    }

    private static MethodHandle field(Class<?> owner, String name, Class<?> type)
    {
        try
        {
            Field field = owner.getField(name);
            if (!type.isAssignableFrom(field.getType())) return null;
            return LOOKUP.unreflectGetter(field).asType(MethodType.methodType(type, owner));
        }
        catch (Throwable absent)
        {
            return null;
        }
    }

    private static MethodHandle setter(Class<?> owner, String name, Class<?> type)
    {
        try
        {
            Field field = owner.getField(name);
            if (field.getType() != type) return null;
            return LOOKUP.unreflectSetter(field).asType(MethodType.methodType(void.class, owner, type));
        }
        catch (Throwable absent)
        {
            return null;
        }
    }

    private static MethodHandle method(Class<?> owner, String name, Class<?> type)
    {
        try
        {
            return LOOKUP.findVirtual(owner, name, MethodType.methodType(type)).asType(MethodType.methodType(type, owner));
        }
        catch (Throwable absent)
        {
            return null;
        }
    }

    private static MethodHandle staticFlag(Class<?> owner, String name)
    {
        try
        {
            return LOOKUP.findStaticGetter(owner, name, boolean.class);
        }
        catch (Throwable absent)
        {
            return null;
        }
    }

    /* ---- invocation ---- */

    private static Object get(MethodHandle handle, Object receiver)
    {
        try
        {
            return handle.invoke(receiver);
        }
        catch (RuntimeException | Error failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new IllegalStateException("BBS bridge read failed: " + handle, failure);
        }
    }

    public static boolean procedural(ModelInstance instance) { return (Boolean) get(PROCEDURAL, instance); }
    public static boolean culling(ModelInstance instance) { return (Boolean) get(CULLING, instance); }
    /** Null when no scale accessor resolved (unaudited host): callers fold the identity. */
    public static Vector3f scale(ModelInstance instance) { return SCALE == null ? null : (Vector3f) get(SCALE, instance); }
    public static Link texture(ModelInstance instance) { return (Link) get(TEXTURE, instance); }
    public static View view(ModelInstance instance) { return (View) get(VIEW, instance); }
    public static List<?> itemsMain(ModelInstance instance) { return (List<?>) get(ITEMS_MAIN, instance); }
    public static List<?> itemsOff(ModelInstance instance) { return (List<?>) get(ITEMS_OFF, instance); }
    public static Map<?, ?> armorSlots(ModelInstance instance) { return (Map<?, ?>) get(ARMOR_SLOTS, instance); }
    /** Resolved welds; empty on BBS 2.3.1, which has no welds. */
    public static List<?> weldBindings(ModelInstance instance)
    {
        return WELD_BINDINGS == null ? List.of() : (List<?>) get(WELD_BINDINGS, instance);
    }

    /** The second euler triple of BBS 2.3.1, null on 2.5.2. */
    public static Vector3f rotate2(Transform transform) { return ROTATE2 == null ? null : (Vector3f) get(ROTATE2, transform); }
    /** Rotation mode ordinal of BBS 2.5.2 (EULER=0, QUATERNION=1), -1 on 2.3.1. */
    public static int rotationMode(Transform transform)
    {
        return ROTATION_MODE == null ? -1 : ((Enum<?>) get(ROTATION_MODE, transform)).ordinal();
    }
    /** The quaternion rotation of BBS 2.5.2, null on 2.3.1. */
    public static Quaternionf quat(Transform transform) { return QUAT == null ? null : (Quaternionf) get(QUAT, transform); }

    /** The transient IK stretch offset of BBS 2.5.2; null when absent or not shifted. */
    public static Vector3f offset(ModelGroup group) { return OFFSET == null ? null : (Vector3f) get(OFFSET, group); }
    public static void setOffset(ModelGroup group, Vector3f offset)
    {
        if (OFFSET_SETTER == null) return;
        try
        {
            OFFSET_SETTER.invoke(group, offset);
        }
        catch (RuntimeException | Error failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new IllegalStateException("BBS bridge write failed: " + OFFSET_SETTER, failure);
        }
    }

    /** BBS 2.3.1 global IK/physics debug overlays (extra silhouette drawn by the renderer). */
    public static boolean debugOverlays()
    {
        return staticFlag(IK_DEBUG) || staticFlag(PHYSICS_DEBUG);
    }

    private static boolean staticFlag(MethodHandle handle)
    {
        if (handle == null) return false;
        try
        {
            return (boolean) handle.invokeExact();
        }
        catch (RuntimeException | Error failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new IllegalStateException("BBS bridge flag failed: " + handle, failure);
        }
    }

    /** A revision probe failure costs reuse, never correctness; surface the first one so
     * BBS drift never degrades silently into "no reuse". */
    public static void reportFailure(Throwable failure)
    {
        if (FAILURE_REPORTED.compareAndSet(false, true))
        {
            System.err.println("[irlite] caster-revision probe failed on bbs " + BBS_VERSION
                + "; revisions stay UNKNOWN for that caster");
            failure.printStackTrace();
        }
    }
}
