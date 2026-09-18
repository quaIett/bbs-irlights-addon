package qualet.irlite.client.forms;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** No game/GL context: exercise real pose snapshots, and audit the real MC/BBS
 * bytecode anchors. Only the BBS shader-enabled flag is a fixture. */
public final class GuideOverlayTest
{
    private static int checks;

    private static void check(boolean value, String description)
    {
        if (!value) throw new AssertionError(description);
        checks++;
    }

    private static Object value(Object guide, String name) throws Exception
    {
        Method method = guide.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(guide);
    }

    private static ClassNode read(String owner) throws Exception
    {
        ClassNode result = new ClassNode();
        try (var in = GuideOverlayTest.class.getClassLoader().getResourceAsStream(owner + ".class"))
        {
            if (in == null) throw new AssertionError(owner);
            new ClassReader(in).accept(result, 0);
        }
        return result;
    }

    private static MethodNode method(String owner, String name, String descriptor) throws Exception
    {
        MethodNode result = read(owner).methods.stream()
            .filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow();
        checks++;
        return result;
    }

    private static Object annotation(AnnotationNode node, String key)
    {
        for (int i = 0; i < node.values.size(); i += 2)
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        throw new AssertionError("Missing annotation value: " + key);
    }

    public static void main(String[] args) throws Exception
    {
        String mc = args[0];
        boolean legacy = mc.startsWith("1.20.");
        String game = "net/minecraft/client/render/GameRenderer";
        String counter = "Lnet/minecraft/client/render/RenderTickCounter;";
        String renderDesc = legacy ? "(FJZ)V" : "(" + counter + "Z)V";
        String worldDesc = legacy ? "(FJLnet/minecraft/client/util/math/MatrixStack;)V" : "(" + counter + ")V";
        MethodNode render = method(game, "render", renderDesc);
        method(game, "renderWorld", worldDesc);
        int count = 0;
        for (AbstractInsnNode insn : render.instructions)
            if (insn instanceof MethodInsnNode call && call.owner.equals(game)
                && call.name.equals("renderWorld") && call.desc.equals(worldDesc)) count++;
        check(count == 1, "Exactly one world-render call in the real Minecraft class");

        MethodNode hook = method("qualet/irlite/mixin/client/GameRendererLightMixin", "irlite$drawLightGuides",
            renderDesc.substring(0, renderDesc.length() - 2) + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        AnnotationNode inject = hook.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        check(annotation(inject, "require").equals(1), "Injection must not silently fail");
        AnnotationNode at = (AnnotationNode) ((List<?>) annotation(inject, "at")).get(0);
        check(annotation(at, "target").equals("L" + game + ";renderWorld" + worldDesc), "Exact injection target");
        check(((String[]) annotation(at, "shift"))[1].equals("AFTER"), "After the complete world/shader pass");

        // Check the actual host dependency, skipping our one-flag fixture on the test classpath.
        var hosts = GuideOverlayTest.class.getClassLoader().getResources("mchorse/bbs_mod/client/BBSRendering.class");
        boolean gateFound = false;
        while (hosts.hasMoreElements())
        {
            var resource = hosts.nextElement();
            if (!resource.getProtocol().equals("jar")) continue;
            try (var in = resource.openStream())
            {
                ClassNode host = new ClassNode();
                new ClassReader(in).accept(host, 0);
                String gate = mc.equals("1.21.11") ? "isIrisWorldForms" : "isIrisWorldShadersEnabled";
                gateFound |= host.methods.stream().anyMatch(m -> m.name.equals(gate) && m.desc.equals("()Z"));
            }
        }
        check(gateFound, "World/offscreen shader gate exists in real BBS JAR");

        RenderSystem.initRenderThread();
        Field pendingField = WorldLightGuideOverlay.class.getDeclaredField("pending");
        pendingField.setAccessible(true);
        List<?> pending = (List<?>) pendingField.get(null);
        WorldLightGuideOverlay.beginFrame();
        WorldLightGuideOverlay.flush(); // Empty frames must not touch GL or the client singleton.
        MatrixStack stack = new MatrixStack();
        stack.translate(4, 5, -6);
        Matrix4f pose = new Matrix4f(stack.peek().getPositionMatrix());
        Matrix4f view = new Matrix4f().rotationXYZ(0.2F, 0.4F, 0.1F);
        RenderSystem.getModelViewMatrix().set(view);
        Color color = new Color(0.2F, 0.4F, 0.8F);
        BBSRendering.active = false;
        check(!WorldLightGuideOverlay.defer(stack, color, 10F, 40F, 20F, true), "Offscreen/no-shader path remains immediate");
        check(pending.isEmpty(), "No offscreen command leaks into the world");
        BBSRendering.active = true;
        check(WorldLightGuideOverlay.defer(stack, color, 10F, 40F, 20F, true), "World spotlight queued");
        check(WorldLightGuideOverlay.defer(stack, color, 5F, 0F, 0F, false), "World point light queued");
        check(pending.size() == 2, "Both guides retained");
        Object spot = pending.get(0), point = pending.get(1);
        stack.translate(100, 200, 300);
        RenderSystem.getModelViewMatrix().identity();
        color.r = 1F;
        check(((Matrix4f) value(spot, "pose")).equals(pose), "Pose survives caller stack reuse");
        check(((Matrix4f) value(spot, "modelView")).equals(view), "Camera survives later hand/UI matrices");
        check(((Color) value(spot, "color")).r == 0.2F, "Tint survives reused BBS colour");
        check(value(spot, "range").equals(10F) && value(spot, "outer").equals(40F)
            && value(spot, "inner").equals(20F) && value(spot, "spot").equals(true), "Spotlight dimensions retained");
        check(value(point, "range").equals(5F) && value(point, "spot").equals(false), "Point radius retained");
        if (!mc.equals("1.21.11"))
        {
            Matrix4f projection = new Matrix4f((Matrix4f) value(spot, "projection"));
            ((Matrix4f) RenderSystem.class.getMethod("getProjectionMatrix").invoke(null)).scale(2F);
            check(((Matrix4f) value(spot, "projection")).equals(projection), "Projection is not a mutable global reference");
        }
        WorldLightGuideOverlay.beginFrame();
        check(pending.isEmpty(), "Aborted/old frame cannot leak guides into the next frame");
        WorldLightGuideOverlay.flush();
        System.out.println("Guide overlay " + mc + ": PASS " + checks + " snapshot/API checks (no GPU draw)");
    }
}
