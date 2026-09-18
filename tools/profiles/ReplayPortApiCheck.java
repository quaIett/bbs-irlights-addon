import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Verify the bytecode anchors compilation cannot check, without launching Minecraft. */
public final class ReplayPortApiCheck {
    private static int checks;
    private static ClassNode read(String name) throws Exception {
        try (InputStream in = ReplayPortApiCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing class: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, 0);
            return node;
        }
    }
    private static MethodNode method(String owner, String name, String desc) throws Exception {
        for (MethodNode method : read(owner).methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) { checks++; return method; }
        }
        throw new AssertionError("Missing method: " + owner + "." + name + desc);
    }
    private static void calls(MethodNode method, String owner, String name, String desc, int min) {
        int count = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(desc)) count++;
        }
        if (count < min) throw new AssertionError(method.name + ": missing invocation " + owner + "." + name);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        String b = "mchorse/bbs_mod/", editor = b + "ui/film/replays/UIReplaysEditor";
        String keyEditors = b + "ui/framework/elements/input/keyframes/";
        method(keyEditors + "factories/UIKeyframeFactory", "createPanel", "(L" + b + "utils/keyframes/Keyframe;L" + keyEditors + "UIKeyframes;)L" + keyEditors + "factories/UIKeyframeFactory;");
        String category = editor + "$ReplayCategory";
        method(category, "<init>", "(Ljava/lang/String;IL" + b + "ui/utils/icons/Icon;L" + b + "l10n/keys/IKey;L" + b + "l10n/keys/IKey;)V");
        if (read(category).fields.stream().noneMatch(f -> f.name.equals("$VALUES") && f.desc.equals("[L" + category + ";")))
            throw new AssertionError("ReplayCategory.$VALUES missing");
        checks++;
        method(editor, "categoryOf", "(L" + b + "ui/framework/elements/input/keyframes/UIKeyframeSheet;)L" + category + ";");
        calls(method(editor, "updateChannelsList", "()V"), editor, "updateTab", "(L" + category + ";Ljava/util/List;)V", 2);
        method(b + "film/replays/tracks/TrackCatalog", "of", "(L" + b + "forms/forms/Form;L" + b + "film/replays/FormProperties;)Ljava/util/List;");
        method(b + "film/replays/tracks/behaviours/PropertyTrack", "apply", "(L" + b + "film/replays/tracks/TrackContext;L" + b + "film/replays/tracks/TrackId;L" + b + "utils/keyframes/KeyframeChannel;FF)V");
        String renderer = b + "forms/renderers/FormRenderer", context = "(L" + b + "forms/renderers/FormRenderingContext;)V";
        int wrapped = 0;
        for (MethodNode m : read(b + "forms/FormUtilsClient").methods) {
            if (!m.name.equals("render")) continue;
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(renderer) && call.name.equals("render") && call.desc.equals(context)) wrapped++;
            }
        }
        if (wrapped != 1) throw new AssertionError("Expected one FormRenderer.render redirect anchor, got " + wrapped);
        checks++;
        String iris = "net/irisshaders/iris/";
        method(iris + "uniforms/CommonUniforms", "addDynamicUniforms", "(L" + iris + "gl/uniform/DynamicUniformHolder;L" + iris + "gl/state/FogMode;)V");
        String desc = args[0].startsWith("1.20.") ? "(FJLnet/minecraft/client/util/math/MatrixStack;)V" : "(Lnet/minecraft/client/render/RenderTickCounter;)V";
        MethodNode world = method("net/minecraft/client/render/GameRenderer", "renderWorld", desc);
        if (args[0].equals("1.21.11")) {
            calls(world, "net/minecraft/client/render/GameRenderer", "updateCameraState", "(F)V", 1);
        } else {
            calls(world, "net/minecraft/client/render/Camera", "update", "(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V", 1);
        }
        Files.writeString(Path.of(args[1]), "{\"minecraft\":\"" + args[0] + "\",\"checks\":" + checks + ",\"status\":\"PASS\"}\n");
        System.out.println("Replay port bytecode anchors PASS: " + args[0] + ", " + checks + " checks");
    }
}
