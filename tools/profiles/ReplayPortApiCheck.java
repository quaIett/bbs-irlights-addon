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
        method(keyEditors + "UIKeyframeSheet", "<init>", "(L" + b + "film/replays/tracks/TrackDescriptor;)V");
        method(editor, "getExpandedTracks", "()L" + b + "ui/framework/elements/input/items/FoldState;");
        if (read(keyEditors + "UIKeyframeSheet").fields.stream().noneMatch(f -> f.name.equals("descriptor")
                && f.desc.equals("L" + b + "film/replays/tracks/TrackDescriptor;")
                && (f.access & org.objectweb.asm.Opcodes.ACC_FINAL) != 0))
            throw new AssertionError("UIKeyframeSheet.descriptor anchor missing");
        checks++;
        if (read(editor).fields.stream().noneMatch(f -> f.name.equals("expandedTracksByReplay")
                && f.desc.equals("Ljava/util/Map;") && (f.access & org.objectweb.asm.Opcodes.ACC_FINAL) != 0))
            throw new AssertionError("UIReplaysEditor.expandedTracksByReplay anchor missing");
        checks++;
        /* The timeline's own tabs and sections, which BBS 2.7 turned from an enum and hand-built
         * body-part header rows into registered addon contracts. */
        String categories = b + "api/client/editor/TrackCategories", categoryType = b + "api/client/editor/TrackCategory";
        String predicate = "Ljava/util/function/BiPredicate;";
        method(categoryType, "<init>", "(Ljava/lang/String;L" + b + "ui/utils/icons/Icon;L" + b + "l10n/keys/IKey;L" + b + "l10n/keys/IKey;)V");
        method(categories, "register", "(L" + categoryType + ";" + predicate + ")V");
        method(categories, "categoryOf", "(L" + b + "film/replays/tracks/TrackId;Z)L" + categoryType + ";");
        method(b + "api/client/events/RegisterTrackCategoriesEvent", "register", "(L" + categoryType + ";" + predicate + ")V");
        String section = keyEditors + "UIKeyframeSheet$Section";
        method(section, "<init>", "(Ljava/lang/String;L" + b + "l10n/keys/IKey;L" + b + "ui/utils/icons/Icon;I)V");
        if (read(keyEditors + "UIKeyframeSheet").fields.stream().noneMatch(f -> f.name.equals("section")
                && f.desc.equals("L" + section + ";")
                && (f.access & org.objectweb.asm.Opcodes.ACC_FINAL) == 0))
            throw new AssertionError("UIKeyframeSheet.section anchor missing");
        checks++;
        method(b + "film/replays/tracks/TrackCatalog", "of", "(L" + b + "forms/forms/Form;L" + b + "film/replays/FormProperties;)Ljava/util/List;");
        method(b + "film/replays/tracks/behaviours/PropertyTrack", "apply", "(L" + b + "film/replays/tracks/TrackContext;L" + b + "film/replays/tracks/TrackId;L" + b + "utils/keyframes/KeyframeChannel;FF)V");
        String renderer = b + "forms/renderers/FormRenderer", context = "(L" + b + "forms/renderers/FormRenderingContext;)V";
        method(b + "forms/forms/Form", "getParentForm", "()L" + b + "forms/forms/Form;");
        method(renderer, "getForm", "()L" + b + "forms/forms/Form;");
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
