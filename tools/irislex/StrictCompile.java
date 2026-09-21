import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL46C.*;

/**
 * Compiles shader sources exactly as Iris hands them to the driver, on whichever GPU this JVM lands on.
 * NVIDIA accepts GLSL that AMD and Intel reject (readonly/restrict below GLSL 4.20 broke three packs for months),
 * so a pack is only checked once it compiles on a strict driver too.
 *
 * Getting the sources: set enableDebugOptions=true in run/config/iris.properties and join a world with the pack;
 * Iris writes every patched program to run/patched_shaders. Picking the GPU: Windows graphics settings pin the
 * JDK 21 java.exe to NVIDIA on the dev laptop while JDK 17 has no preference and lands on the Intel UHD, so run
 * this with JDK 17 for the strict check (the "GPU" line printed first says which driver was used).
 *
 *   info
 *   compile <dumpDir> <outDir>
 *   nvasm   <dumpDirA> <dumpDirB> <outDir>   NVIDIA only, fragment programs: compare the generated assembly
 */
public class StrictCompile {
    static final Map<String, Integer> TYPES = Map.of(
        ".vsh", GL_VERTEX_SHADER, ".fsh", GL_FRAGMENT_SHADER, ".gsh", GL_GEOMETRY_SHADER,
        ".csh", GL_COMPUTE_SHADER, ".tcs", GL_TESS_CONTROL_SHADER, ".tes", GL_TESS_EVALUATION_SHADER);

    public static void main(String[] args) throws Exception {
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        long window = glfwCreateWindow(32, 32, "IRLights strict compile", 0, 0);
        if (window == 0) throw new IllegalStateException("No GL 4.3 core context");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        String gpu = glGetString(GL_VENDOR) + " | " + glGetString(GL_RENDERER) + " | " + glGetString(GL_VERSION);
        System.out.println("GPU " + gpu);
        try {
            switch (args[0]) {
                case "info" -> { }
                case "compile" -> compile(Path.of(args[1]), Path.of(args[2]), gpu);
                case "nvasm" -> nvasm(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
                default -> throw new IllegalArgumentException(args[0]);
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    static List<Path> sources(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> TYPES.containsKey(ext(p))).sorted().collect(Collectors.toList());
        }
    }

    static String ext(Path p) {
        String n = p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i);
    }

    static void compile(Path dir, Path out, String gpu) throws Exception {
        Files.createDirectories(out);
        List<String> summary = new ArrayList<>();
        int passed = 0, failed = 0;
        for (Path file : sources(dir)) {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            int shader = glCreateShader(TYPES.get(ext(file)));
            glShaderSource(shader, src);
            glCompileShader(shader);
            boolean ok = glGetShaderi(shader, GL_COMPILE_STATUS) != 0;
            String log = glGetShaderInfoLog(shader, 65536).strip();
            glDeleteShader(shader);
            String name = file.getFileName().toString();
            if (ok) passed++; else failed++;
            summary.add((ok ? "PASS " : "FAIL ") + name);
            if (!ok) Files.writeString(out.resolve(name + ".log"), log, StandardCharsets.UTF_8);
            else if (!log.isEmpty()) Files.writeString(out.resolve(name + ".warn.log"), log, StandardCharsets.UTF_8);
        }
        summary.add(0, "GPU " + gpu);
        summary.add(1, "compiled=" + passed + " failed=" + failed);
        Files.write(out.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        System.out.println("compiled=" + passed + " failed=" + failed + " report=" + out);
    }

    /** NVIDIA program binaries carry the generated assembly as text; pull out every "!!NV...END" block. */
    static String assembly(String src) {
        int program = glCreateShaderProgramv(GL_FRAGMENT_SHADER, src);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) {
            String log = glGetProgramInfoLog(program, 65536);
            glDeleteProgram(program);
            return "LINK FAILED\n" + log;
        }
        int length = glGetProgrami(program, GL_PROGRAM_BINARY_LENGTH);
        ByteBuffer binary = org.lwjgl.BufferUtils.createByteBuffer(length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer written = stack.mallocInt(1), format = stack.mallocInt(1);
            glGetProgramBinary(program, written, format, binary);
            binary.limit(written.get(0));
        }
        glDeleteProgram(program);
        byte[] bytes = new byte[binary.remaining()];
        binary.get(bytes);
        // The assembly is one printable run inside the blob; "\nEND" alone would stop at the first ENDIF/ENDREP.
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        StringBuilder asm = new StringBuilder();
        int from = 0;
        while ((from = text.indexOf("!!NV", from)) >= 0) {
            int end = from;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c != '\n' && c != '\t' && c != '\r' && (c < 0x20 || c > 0x7e)) break;
                end++;
            }
            asm.append(text, from, end).append('\n');
            from = end;
        }
        return asm.length() == 0 ? "NO ASSEMBLY (" + bytes.length + " bytes)" : asm.toString();
    }

    static void nvasm(Path a, Path b, Path out) throws Exception {
        Files.createDirectories(out);
        List<String> summary = new ArrayList<>();
        int same = 0, differ = 0, missing = 0;
        for (Path fa : sources(a)) {
            if (!ext(fa).equals(".fsh")) continue;
            Path fb = b.resolve(fa.getFileName().toString());
            String name = fa.getFileName().toString();
            if (!Files.exists(fb)) { summary.add("MISSING " + name); missing++; continue; }
            String sa = Files.readString(fa, StandardCharsets.UTF_8), sb = Files.readString(fb, StandardCharsets.UTF_8);
            if (sa.equals(sb)) { summary.add("SRC-SAME " + name); continue; }
            String asmA = assembly(sa), asmB = assembly(sb);
            boolean eq = asmA.equals(asmB) && !asmA.startsWith("NO ASSEMBLY") && !asmA.startsWith("LINK FAILED");
            if (eq) same++; else differ++;
            summary.add((eq ? "ASM-SAME " : "ASM-DIFF ") + name + " (" + asmA.length() + " / " + asmB.length() + " chars)");
            if (!eq) {
                Files.writeString(out.resolve(name + ".a.asm"), asmA, StandardCharsets.UTF_8);
                Files.writeString(out.resolve(name + ".b.asm"), asmB, StandardCharsets.UTF_8);
            }
        }
        summary.add(0, "asm-same=" + same + " asm-diff=" + differ + " missing=" + missing);
        Files.write(out.resolve("nvasm-summary.txt"), summary, StandardCharsets.UTF_8);
        System.out.println("asm-same=" + same + " asm-diff=" + differ + " missing=" + missing + " report=" + out);
    }
}
