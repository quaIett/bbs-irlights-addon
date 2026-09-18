import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Keep real BBS BodyPart/StubEntity code; only avoid bootstrapping item registries.
 * These ownership tests never read equipment, so null empty slots are sufficient. */
public final class BodyPartEntityFixture
{
    public static void main(String[] args) throws Exception
    {
        String name = "mchorse/bbs_mod/forms/entities/StubEntity.class";
        ClassNode node = new ClassNode();
        try (var input = BodyPartEntityFixture.class.getClassLoader().getResourceAsStream(name))
        {
            new ClassReader(input).accept(node, 0);
        }
        int replaced = 0;
        for (MethodNode method : node.methods)
        {
            if (!method.name.equals("<init>")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray())
            {
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals("net/minecraft/item/ItemStack") && field.name.equals("EMPTY"))
                {
                    method.instructions.set(field, new InsnNode(Opcodes.ACONST_NULL));
                    replaced++;
                }
            }
        }
        if (replaced != 1) throw new AssertionError("StubEntity registry boundary changed: " + replaced);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        Path target = Path.of(args[0]).resolve(name);
        Files.createDirectories(target.getParent());
        Files.write(target, writer.toByteArray());
    }
}
