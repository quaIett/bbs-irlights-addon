import java.nio.file.*;
import net.irisshaders.iris.shaderpack.parsing.ConstDirectiveParser;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;

/** Uses Iris's real directive parser, then checks replay ID/depth storage on the GPU. */
public class ReplayAttachmentCheck {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of(args[0]));
        String key = args[1] + "Format";
        String format = "RGBA"; // Iris's default when the directive is absent/unrecognized.
        for (var directive : ConstDirectiveParser.findDirectives(source)) {
            if (directive.getKey().equals(key)) format = directive.getValue();
        }
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        long window = glfwCreateWindow(16, 16, "Replay attachment check", 0, 0);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        try {
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, format.equals("RG32F") ? GL_RG32F : GL_RGBA8,
                    1, 1, 0, GL_RG, GL_FLOAT, (java.nio.ByteBuffer) null);
            int framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("Incomplete framebuffer");
            for (int id : new int[] {0, 1, 2, 255, 256, 16777215}) {
                float depth = 0.91337f;
                glClearBufferfv(GL_COLOR, 0, new float[] {id, depth, 0, 0});
                float[] read = new float[2];
                glReadPixels(0, 0, 1, 1, GL_RG, GL_FLOAT, read);
                if (read[0] != id || Math.abs(read[1] - depth) >= 0.000002f)
                    throw new AssertionError(key + "=" + format + ": ID=" + id + ", read=" +
                            read[0] + ", depth=" + read[1] + " (expected " + depth + ")");
            }
            if (!format.equals("RG32F")) throw new AssertionError("Expected RG32F, got " + format);
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(texture);
            System.out.println("PASS " + key + "=" + format + ": six replay IDs and exact depth");
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
