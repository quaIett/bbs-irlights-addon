import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;

/** Standalone driver test of the entire production VL library. No Minecraft,
 *  Iris, game settings, world files or visible window are used. */
public final class VlGpuTest
{
    private static final double ABS_TOL = 2e-5, REL_TOL = 5e-4;
    private static final int LIGHTS = 16, ATLAS = 384, COOKIE = 32, DRAWS_PER_QUERY = 4;
    private static final String[] SWITCHES = {"IRLITE_VL_COOKIE_HOIST", "IRLITE_VL_ZERO_KNOBS", "IRLITE_VL_POINT_HOIST"};
    private static final String VERTEX = """
        #version 430 core
        noperspective out vec2 texCoord;
        void main() {
            vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            texCoord = p;
            gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
        }
        """;
    private static final String HEADER = """
        #version 430 core
        #define FRAGMENT_SHADER
        #define IRLITE_VL_PASS
        #define texture2DLod textureLod
        #define texture2D texture
        noperspective in vec2 texCoord;
        uniform float viewWidth, viewHeight, frameTimeCounter;
        uniform sampler2D noisetex;
        layout(location=0) out vec4 resultColor;
        """;
    private static final String MAIN = """
        void main() {
            vec3 start = vec3(0.0);
            vec3 end = vec3((texCoord - 0.5) * vec2(20.0, 12.0), 24.0);
            // Same deterministic start offset in both programs and every timing
            // pair. The real production Noise3D and its texture remain active.
            float dither = fract(gl_FragCoord.x * 0.754877666 + gl_FragCoord.y * 0.569840296);
            resultColor = vec4(irlite_volumetric(start, end, normalize(end - start), dither), 1.0);
        }
        """;

    private record Program(String name, int id, String sourceHash, String driverHash) {}
    private record Scene(String type, String shadow, int stride, int noise, boolean cookie,
                         boolean zeroKnobs, boolean hiz)
    {
        String name() { return type + "-" + shadow + "-s" + stride + "-noise" + noise
            + (cookie ? "-cookie" : "") + (zeroKnobs ? "-zero" : "") + (hiz ? "-hiz" : ""); }
    }
    private record Difference(double maxAbs, double maxRel, double rms, int failures, double energy) {}
    private final List<Integer> textures = new ArrayList<>();
    private final List<Program> programs = new ArrayList<>();
    private final List<String> comparisons = new ArrayList<>(), timings = new ArrayList<>();
    private final Map<String, Double> referenceEnergy = new LinkedHashMap<>();
    private final Path out;
    private final int width, height, warmup, pairs;
    private int lightsBuffer, globalsBuffer, clustersBuffer, framebuffer, vao;
    private int spotDepth, pointDepth, spotPyramid;
    private long window;

    private VlGpuTest(Path out, int width, int height, int warmup, int pairs)
    {
        this.out = out; this.width = width; this.height = height; this.warmup = warmup; this.pairs = pairs;
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length < 8) throw new IllegalArgumentException("baseline current commonFunctions output width height warmup pairs [isolate]");
        Path baseline = Path.of(args[0]), current = Path.of(args[1]), common = Path.of(args[2]), out = Path.of(args[3]);
        var test = new VlGpuTest(out, Integer.parseInt(args[4]), Integer.parseInt(args[5]),
            Integer.parseInt(args[6]), Integer.parseInt(args[7]));
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.json"), "{\"schema\":1,\"passed\":false,\"phase\":\"initializing\"}");
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        callback.set();
        try
        {
            test.run(baseline, current, common, args.length > 8 && args[8].equals("isolate"));
        }
        finally
        {
            test.close();
            glfwSetErrorCallback(null);
            callback.free();
        }
    }

    private void run(Path baseline, Path current, Path common, boolean isolate) throws Exception
    {
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(width, height, "IRLite synthetic GPU verification (hidden)", 0L, 0L);
        if (window == 0) throw new IllegalStateException("Cannot create hidden OpenGL 4.3 context");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        GL.createCapabilities();
        if (!GL.getCapabilities().OpenGL43) throw new IllegalStateException("OpenGL 4.3 unavailable");
        String driver = glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION);
        System.out.println("GPU: " + driver + "; hidden=" + (glfwGetWindowAttrib(window, GLFW_VISIBLE) == GLFW_FALSE));

        String oldSource = Files.readString(baseline), newSource = Files.readString(current);
        String noise = extractFunction(Files.readString(common), "float Noise3D(vec3 p)");
        programs.add(program("baseline", HEADER + noise + "\n" + oldSource + MAIN));
        programs.add(program("current", HEADER + noise + "\n" + newSource + MAIN));
        StringBuilder disabled = new StringBuilder();
        for (String name : SWITCHES) disabled.append("#define ").append(name).append(" 0\n");
        programs.add(program("current-all-disabled", HEADER + disabled + noise + "\n" + newSource + MAIN));
        if (isolate)
            for (String name : SWITCHES)
                programs.add(program("without-" + name, HEADER + "#define " + name + " 0\n" + noise + "\n" + newSource + MAIN));
        // Match Iris's boolean shader-option preprocessing. All library code
        // is retained; only the IRLITE_VL_SHADOWS option is commented out.
        programs.add(program("baseline-compile-shadows-off", HEADER + noise + "\n" + withoutVlShadows(oldSource) + MAIN));
        programs.add(program("current-compile-shadows-off", HEADER + noise + "\n" + withoutVlShadows(newSource) + MAIN));
        setup();
        List<Scene> scenes = new ArrayList<>();
        for (String type : List.of("point", "spot"))
            for (String shadow : List.of("lit", "occluded", "edge"))
                for (int stride : new int[] {1, 2, 3, 8})
                    for (int noiseMode = 0; noiseMode < 3; noiseMode++)
                    {
                        scenes.add(new Scene(type, shadow, stride, noiseMode, false, false, false));
                        if (type.equals("spot")) scenes.add(new Scene(type, shadow, stride, noiseMode, true, false, false));
                    }
        scenes.add(new Scene("spot", "lit", 2, 2, true, true, false));
        scenes.add(new Scene("point", "occluded", 3, 2, false, true, false));
        scenes.add(new Scene("spot", "lit", 2, 1, true, false, true));
        scenes.add(new Scene("spot", "occluded", 2, 1, true, false, true));
        scenes.add(new Scene("spot", "edge", 3, 2, true, false, true));
        for (int stride : new int[] {1, 2, 3, 8})
        {
            scenes.add(new Scene("mixed", "edge", stride, 2, true, false, false));
            scenes.add(new Scene("mixed", "edge", stride, 2, true, false, true));
            scenes.add(new Scene("spot-axis", "edge", stride, 2, true, false, false));
            scenes.add(new Scene("spot-narrow", "lit", stride, 2, true, false, false));
            for (int noiseMode = 0; noiseMode < 3; noiseMode++)
            {
                scenes.add(new Scene("spot", "disabled", stride, noiseMode, true, false, false));
                scenes.add(new Scene("spot", "unmapped", stride, noiseMode, true, false, false));
            }
        }
        int failed = 0;
        for (Scene scene : scenes)
        {
            fixture(scene);
            float[] expected = read(programs.get(0));
            double energy = energy(expected);
            referenceEnergy.put(scene.name(), energy);
            if (scene.shadow.equals("lit") && energy < 1e-6)
                throw new AssertionError("Vacuous lit fixture: " + scene.name());
            for (int i = 1; i < programs.size(); i++)
            {
                Program program = programs.get(i);
                if (program.name.endsWith("-compile-shadows-off") && !scene.shadow.equals("disabled")) continue;
                float[] actual = read(program);
                Difference d = compare(expected, actual);
                if (d.failures != 0) failed++;
                comparisons.add("{\"scene\":" + quote(scene.name()) + ",\"variant\":" + quote(program.name)
                    + ",\"maxAbs\":" + d.maxAbs + ",\"maxRel\":" + d.maxRel + ",\"rms\":" + d.rms
                    + ",\"failedComponents\":" + d.failures + ",\"baselineEnergy\":" + energy + ",\"currentEnergy\":" + d.energy + "}");
                if (scene.name().equals("spot-edge-s2-noise2-cookie") && i == 1)
                {
                    picture(expected, out.resolve("baseline.png"), 1);
                    picture(actual, out.resolve("current.png"), 1);
                    float[] diff = new float[actual.length];
                    for (int p = 0; p < diff.length; p++) diff[p] = Math.abs(actual[p] - expected[p]);
                    picture(diff, out.resolve("difference-x1000.png"), 1000);
                }
            }
        }
        verifyFixtures();
        System.out.println("Framebuffer comparisons: " + comparisons.size() + ", failed: " + failed);
        if (failed == 0)
        {
            for (Scene scene : List.of(
                new Scene("point", "edge", 2, 2, false, false, false),
                new Scene("spot", "lit", 2, 2, true, false, false),
                new Scene("spot", "occluded", 2, 2, true, false, false),
                new Scene("spot", "edge", 2, 2, true, true, false),
                new Scene("mixed", "edge", 2, 2, true, false, true),
                new Scene("spot", "disabled", 2, 2, true, false, false)))
            {
                fixture(scene);
                time(scene, programs.get(0), programs.get(1));
                if (isolate)
                    for (Program p : programs)
                        if (p.name.startsWith("without-")) time(scene, p, programs.get(1));
            }
        }
        String programJson = String.join(",", programs.stream().map(p -> "{\"name\":" + quote(p.name)
            + ",\"sourceSha256\":" + quote(p.sourceHash) + ",\"driverSourceSha256\":" + quote(p.driverHash) + "}").toList());
        Files.writeString(out.resolve("report.json"), "{\"schema\":1,\"synthetic\":true,\"passed\":" + (failed == 0)
            + ",\"driver\":" + quote(driver) + ",\"java\":" + quote(System.getProperty("java.version"))
            + ",\"width\":" + width + ",\"height\":" + height + ",\"lightCount\":" + LIGHTS
            + ",\"drawsPerQuery\":" + DRAWS_PER_QUERY + ",\"noiseTextureSeed\":\"0x49524C697465\",\"atlasSize\":" + ATLAS
            + ",\"warmupPairs\":" + warmup + ",\"timingPairs\":" + pairs
            + ",\"absoluteTolerance\":" + ABS_TOL + ",\"relativeTolerance\":" + REL_TOL
            + ",\"baselineSha256\":" + quote(hash(oldSource)) + ",\"currentSha256\":" + quote(hash(newSource))
            + ",\"noiseSha256\":" + quote(hash(noise)) + ",\"programs\":[" + programJson
            + "],\"comparisons\":[" + String.join(",", comparisons) + "],\"timings\":[" + String.join(",", timings) + "]}");
        if (failed != 0) throw new AssertionError(failed + " framebuffer comparisons failed; see report.json");
        System.out.println("VL GPU verification PASS; synthetic timings are not in-game FPS: " + out.resolve("report.json"));
    }

    private Program program(String name, String source) throws Exception
    {
        Files.writeString(out.resolve(name + ".frag"), source);
        int vs = compile(GL_VERTEX_SHADER, VERTEX, name + ".vert");
        int fs = compile(GL_FRAGMENT_SHADER, source, name + ".frag");
        String driverSource = glGetShaderSource(fs);
        Files.writeString(out.resolve(name + ".driver.frag"), driverSource);
        if (!source.equals(driverSource)) throw new AssertionError("Driver source differs: " + name);
        int p = glCreateProgram();
        try
        {
            glAttachShader(p, vs); glAttachShader(p, fs); glLinkProgram(p);
            String log = glGetProgramInfoLog(p);
            Files.writeString(out.resolve(name + ".link.log"), log);
            if (glGetProgrami(p, GL_LINK_STATUS) != GL_TRUE) throw new AssertionError(name + " link failed: " + log);
            if (glGetUniformBlockIndex(p, "IrliteVlGlobals") == GL_INVALID_INDEX
                || glGetProgramResourceIndex(p, GL_SHADER_STORAGE_BLOCK, "IrliteLights") == GL_INVALID_INDEX)
                throw new AssertionError("Production light/globals interface missing: " + name);
            glUseProgram(p);
            uniform(p, "noisetex", 0); uniform(p, "irl_cookieArray", 1);
            uniform(p, "irl_spotShadowAtlas", 2); uniform(p, "irl_pointShadowAtlas", 3);
            uniform(p, "irl_spotShadowPyramid", 4);
            glUniform1f(glGetUniformLocation(p, "viewWidth"), width);
            glUniform1f(glGetUniformLocation(p, "viewHeight"), height);
            glUniform1f(glGetUniformLocation(p, "frameTimeCounter"), 137.375F);
            return new Program(name, p, hash(source), hash(driverSource));
        }
        finally
        {
            glDeleteShader(vs); glDeleteShader(fs);
        }
    }

    private int compile(int type, String source, String label) throws Exception
    {
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        String log = glGetShaderInfoLog(shader);
        Files.writeString(out.resolve(label + ".compile.log"), log);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) throw new AssertionError(label + " compile failed: " + log);
        return shader;
    }

    private void setup()
    {
        vao = glGenVertexArrays(); glBindVertexArray(vao);
        framebuffer = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        int color = texture(9, GL_TEXTURE_2D);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, width, height, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0); glReadBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete float framebuffer");
        glViewport(0, 0, width, height);
        glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_DITHER);
        lightsBuffer = glGenBuffers(); globalsBuffer = glGenBuffers(); clustersBuffer = glGenBuffers();
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, lightsBuffer);
        glBindBufferBase(GL_UNIFORM_BUFFER, 7, globalsBuffer);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, clustersBuffer);
        ByteBuffer cluster = MemoryUtil.memCalloc(16 + 576 * 8 + 4);
        try
        {
            cluster.putInt(0, 1).putInt(4, 1).putInt(8, 1).putInt(12, 1);
            cluster.putInt(16, 0xffff).putInt(16 + 576 * 8, 0xffff);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, clustersBuffer); glBufferData(GL_SHADER_STORAGE_BUFFER, cluster, GL_STATIC_DRAW);
        }
        finally { MemoryUtil.memFree(cluster); }
        texture(0, GL_TEXTURE_2D);
        FloatBuffer noise = MemoryUtil.memAllocFloat(128 * 128);
        try
        {
            Random random = new Random(0x49524C697465L);
            while (noise.hasRemaining()) noise.put(random.nextFloat());
            noise.flip(); glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 128, 128, 0, GL_RED, GL_FLOAT, noise);
        }
        finally { MemoryUtil.memFree(noise); }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        texture(1, GL_TEXTURE_2D_ARRAY);
        FloatBuffer cookie = MemoryUtil.memAllocFloat(COOKIE * COOKIE);
        try
        {
            for (int y = 0; y < COOKIE; y++) for (int x = 0; x < COOKIE; x++)
                cookie.put(((x / 4 + y / 4) & 1) == 0 ? 0.15F : 1F);
            cookie.flip(); glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_R32F, COOKIE, COOKIE, 1, 0, GL_RED, GL_FLOAT, cookie);
        }
        finally { MemoryUtil.memFree(cookie); }
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        spotDepth = texture(2, GL_TEXTURE_2D); pointDepth = texture(3, GL_TEXTURE_2D); spotPyramid = texture(4, GL_TEXTURE_2D);
        checkError("setup");
    }

    private int texture(int unit, int target)
    {
        int id = glGenTextures(); textures.add(id);
        glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(target, id);
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return id;
    }

    private void fixture(Scene scene)
    {
        ByteBuffer lights = MemoryUtil.memCalloc(16 + LIGHTS * 96), globals = MemoryUtil.memCalloc(96);
        try
        {
            lights.putInt(LIGHTS).putFloat(1).putInt(3).putInt(0);
            for (int i = 0; i < LIGHTS; i++)
            {
                boolean mixed = scene.type.equals("mixed");
                boolean spot = scene.type.startsWith("spot") || mixed && (i & 1) == 1;
                float x = ((i % 4) - 1.5F) * 0.9F, y = ((i / 4) - 1.5F) * 0.6F;
                float z = spot ? 2F : 10F, range = spot ? 22F : 14F;
                float ax = mixed && spot ? x * -0.07F : 0, ay = mixed && spot ? y * -0.1F : 0, az = 1;
                float cosOuter = 0.80F, cosInner = 0.92F;
                if (scene.type.equals("spot-axis"))
                {
                    ay = (i % 2 == 0 ? 1 : -1) * (i % 4 < 2 ? 0.9899F : 0.9901F);
                    az = (float) Math.sqrt(1F - ay * ay);
                    y = -ay * 10F; z = 10F - az * 10F;
                }
                if (scene.type.equals("spot-narrow"))
                {
                    x *= 0.025F; y *= 0.025F; z = 0.025F;
                    cosOuter = (float) Math.cos(Math.toRadians(0.5));
                    cosInner = (float) Math.cos(Math.toRadians(0.25));
                }
                if (mixed) { range += i % 3; z += (i % 3) * 0.5F; }
                vec4(lights, x, y, z, range);
                vec4(lights, 0.5F + (i % 3) * 0.2F, 0.6F, 0.9F - (i % 3) * 0.15F, 0.75F);
                vec4(lights, ax, ay, az, spot ? 1 : 0);
                vec4(lights, cosOuter, cosInner, 0, 0);
                int[] pointBlocks = {0, 1, 2, 13, 14, 29};
                int tile = mixed ? (spot ? new int[] {0, 5, 25}[i % 3] : pointBlocks[(i / 2) % pointBlocks.length]) : 0;
                vec4(lights, mixed ? (i % 4 - 1.5F) * 0.35F : 0.45F, mixed ? 0.035F + (i % 4) * 0.04F : 0.085F,
                    mixed ? 0.4F + (i % 5) * 0.2F : 1, scene.shadow.equals("unmapped") || mixed && i % 7 == 0 ? -1 : tile);
                vec4(lights, scene.cookie && (!mixed || i % 3 != 0) ? 0 : -1, 0.37F + (mixed ? i * 0.1F : 0),
                    0.85F, mixed && i % 5 == 0 ? 1 : 0);
            }
            lights.flip(); glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightsBuffer); glBufferData(GL_SHADER_STORAGE_BUFFER, lights, GL_DYNAMIC_DRAW);
            vec4(globals, 1, 96, scene.zeroKnobs ? 0 : 1.5F, 1.5F);
            vec4(globals, scene.zeroKnobs ? 0 : 0.6F, 2, 0.25F, 37);
            int flags = 128 | 16 | (scene.shadow.equals("disabled") ? 0 : 1) | (scene.noise > 0 ? 2 : 0) | (scene.hiz ? 32 : 0);
            globals.putInt(48).putInt(scene.stride).putInt(scene.stride).putInt(flags);
            vec4(globals, scene.noise == 2 ? 0.75F : 0, 0, 0, 0);
            vec4(globals, 0, 2.2F, 1, 0); vec4(globals, 0, 6, 0.1F, 0);
            globals.flip(); glBindBuffer(GL_UNIFORM_BUFFER, globalsBuffer); glBufferData(GL_UNIFORM_BUFFER, globals, GL_DYNAMIC_DRAW);
        }
        finally { MemoryUtil.memFree(lights); MemoryUtil.memFree(globals); }
        float[] depth = new float[ATLAS * ATLAS];
        for (int y = 0; y < ATLAS; y++) for (int x = 0; x < ATLAS; x++)
            depth[y * ATLAS + x] = List.of("lit", "disabled", "unmapped").contains(scene.shadow) ? 1F : scene.shadow.equals("occluded") ? 0.1F
                : ((x / 5 + y / 7) & 1) == 0 ? 0.994F : 0.96F;
        for (int unit = 2; unit <= 3; unit++)
        {
            glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, unit == 2 ? spotDepth : pointDepth);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, ATLAS, ATLAS, 0, GL_RED, GL_FLOAT, depth);
        }
        glActiveTexture(GL_TEXTURE4); glBindTexture(GL_TEXTURE_2D, spotPyramid);
        int size = ATLAS / 2, level = 0;
        float[] previous = depth;
        int previousSize = ATLAS, components = 1;
        while (size > 0)
        {
            float[] minMax = new float[size * size * 2];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            {
                float lo = 1, hi = 0;
                for (int oy = 0; oy < 2; oy++) for (int ox = 0; ox < 2; ox++)
                {
                    int p = ((y * 2 + oy) * previousSize + x * 2 + ox) * components;
                    lo = Math.min(lo, previous[p]); hi = Math.max(hi, previous[p + components - 1]);
                }
                minMax[(y * size + x) * 2] = lo; minMax[(y * size + x) * 2 + 1] = hi;
            }
            glTexImage2D(GL_TEXTURE_2D, level++, GL_RG32F, size, size, 0, GL_RG, GL_FLOAT, minMax);
            previous = minMax; previousSize = size; components = 2; size /= 2;
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, level - 1);
        checkError("fixture " + scene.name());
    }

    private void draw(Program program)
    {
        glUseProgram(program.id); glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private float[] read(Program program)
    {
        draw(program);
        float[] rgba = new float[width * height * 4];
        glReadPixels(0, 0, width, height, GL_RGBA, GL_FLOAT, rgba);
        checkError("read " + program.name);
        return rgba;
    }

    private void time(Scene scene, Program a, Program b)
    {
        for (int i = 0; i < warmup; i++)
            for (int d = 0; d < DRAWS_PER_QUERY; d++) { draw(a); draw(b); }
        glFinish();
        int[] qa = new int[pairs], qb = new int[pairs];
        long[] ta = new long[pairs], tb = new long[pairs];
        int accepted = 0;
        List<String> raw = new ArrayList<>(); raw.add("pair,order,aBatchNs,bBatchNs");
        try
        {
            for (int i = 0; i < pairs; i++)
            {
                qa[i] = glGenQueries(); qb[i] = glGenQueries();
                if ((i & 1) == 0) { query(a, qa[i]); query(b, qb[i]); }
                else { query(b, qb[i]); query(a, qa[i]); }
            }
            // Blocking readback is deliberately outside every timed bracket.
            // Only rendering is inside each query; fixture uploads are excluded.
            glFinish();
            for (int i = 0; i < pairs; i++)
            {
                long aNs = glGetQueryObjecti64(qa[i], GL_QUERY_RESULT);
                long bNs = glGetQueryObjecti64(qb[i], GL_QUERY_RESULT);
                raw.add(i + "," + ((i & 1) == 0 ? "AB" : "BA") + "," + aNs + "," + bNs);
                // Some NVIDIA timer queries return zero even for this nonempty
                // draw. Retain raw values and reject the WHOLE A/B pair, never
                // turn an unavailable duration into a claimed speedup.
                if (aNs > 0 && bNs > 0) { ta[accepted] = aNs; tb[accepted++] = bNs; }
            }
            Files.write(out.resolve("timing-" + scene.name() + "-" + a.name + ".csv"), raw);
        }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
        finally { for (int q : qa) if (q != 0) glDeleteQueries(q); for (int q : qb) if (q != 0) glDeleteQueries(q); }
        if (accepted < Math.ceil(pairs * 0.9)) throw new AssertionError("Too many invalid GPU timer pairs: " + (pairs - accepted) + "/" + pairs);
        double am = median(Arrays.copyOf(ta, accepted)) / (1e6 * DRAWS_PER_QUERY);
        double bm = median(Arrays.copyOf(tb, accepted)) / (1e6 * DRAWS_PER_QUERY);
        timings.add("{\"scene\":" + quote(scene.name()) + ",\"a\":" + quote(a.name) + ",\"b\":" + quote(b.name)
            + ",\"acceptedPairs\":" + accepted + ",\"rejectedPairs\":" + (pairs - accepted)
            + ",\"aMedianMs\":" + am + ",\"bMedianMs\":" + bm + ",\"ratioAOverB\":" + am / bm + "}");
        System.out.printf(Locale.ROOT, "%s: %s %.4f ms; %s %.4f ms; synthetic ratio %.3fx (%d/%d pairs)%n", scene.name(), a.name, am, b.name, bm, am / bm, accepted, pairs);
        checkError("timing");
    }

    private static void query(Program p, int query)
    {
        glUseProgram(p.id);
        glBeginQuery(GL_TIME_ELAPSED, query);
        for (int draw = 0; draw < DRAWS_PER_QUERY; draw++) glDrawArrays(GL_TRIANGLES, 0, 3);
        glEndQuery(GL_TIME_ELAPSED);
    }

    private void verifyFixtures()
    {
        for (String type : List.of("point", "spot"))
        {
            double lit = referenceEnergy.get(type + "-lit-s2-noise0");
            double occluded = referenceEnergy.get(type + "-occluded-s2-noise0");
            if (!(occluded < lit * 0.9)) throw new AssertionError(type + " shadow fixture did not reduce VL energy");
            double noise = referenceEnergy.get(type + "-lit-s2-noise1");
            double morph = referenceEnergy.get(type + "-lit-s2-noise2");
            if (Math.abs(noise - lit) < 1e-7 || Math.abs(noise - morph) < 1e-7)
                throw new AssertionError(type + " noise/morph fixture is inert");
        }
        if (!(referenceEnergy.get("spot-lit-s2-noise2-cookie") < referenceEnergy.get("spot-lit-s2-noise2") * 0.95))
            throw new AssertionError("Cookie fixture is inert");
    }

    private static Difference compare(float[] a, float[] b)
    {
        double maxAbs = 0, maxRel = 0, squares = 0;
        int failures = 0;
        for (int i = 0; i < a.length; i++)
        {
            if (!Float.isFinite(a[i]) || !Float.isFinite(b[i])) { failures++; continue; }
            double delta = Math.abs((double) a[i] - b[i]), magnitude = Math.max(Math.abs(a[i]), Math.abs(b[i]));
            maxAbs = Math.max(maxAbs, delta); maxRel = Math.max(maxRel, delta / Math.max(magnitude, 1e-6));
            squares += delta * delta;
            if (delta > ABS_TOL + REL_TOL * magnitude) failures++;
        }
        return new Difference(maxAbs, maxRel, Math.sqrt(squares / a.length), failures, energy(b));
    }

    private static double energy(float[] rgba)
    {
        double sum = 0; for (int i = 0; i < rgba.length; i++) if ((i & 3) != 3) sum += Math.abs(rgba[i]);
        return sum / (rgba.length / 4 * 3);
    }

    private void picture(float[] rgba, Path file, double exposure) throws Exception
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
        {
            int p = (y * width + x) * 4, color = 0;
            for (int c = 0; c < 3; c++)
            {
                double linear = Math.max(0, rgba[p + c] * exposure);
                int value = (int) Math.round(Math.pow(linear / (1 + linear), 1 / 2.2) * 255);
                color = (color << 8) | value;
            }
            image.setRGB(x, height - 1 - y, color);
        }
        ImageIO.write(image, "png", file.toFile());
    }

    private void close()
    {
        if (window != 0)
        {
            for (Program p : programs) glDeleteProgram(p.id);
            for (int t : textures) glDeleteTextures(t);
            if (lightsBuffer != 0) glDeleteBuffers(lightsBuffer);
            if (globalsBuffer != 0) glDeleteBuffers(globalsBuffer);
            if (clustersBuffer != 0) glDeleteBuffers(clustersBuffer);
            if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
            if (vao != 0) glDeleteVertexArrays(vao);
            glfwDestroyWindow(window); window = 0;
        }
        glfwTerminate();
    }

    private static String extractFunction(String source, String signature)
    {
        int start = source.indexOf(signature), brace = source.indexOf('{', start);
        if (start < 0 || brace < 0) throw new IllegalArgumentException("Missing production " + signature);
        int depth = 1, end = brace + 1;
        while (end < source.length() && depth > 0)
        {
            char c = source.charAt(end++); if (c == '{') depth++; if (c == '}') depth--;
        }
        if (depth != 0) throw new IllegalArgumentException("Unbalanced production function");
        return source.substring(start, end) + "\n";
    }
    private static String withoutVlShadows(String source)
    {
        String result = source.replaceFirst("(?m)^#define IRLITE_VL_SHADOWS[^\\r\\n]*", "// IRLITE_VL_SHADOWS disabled by the test fixture");
        if (result.equals(source)) throw new IllegalArgumentException("Missing production IRLITE_VL_SHADOWS option");
        return result;
    }
    private static void vec4(ByteBuffer b, float x, float y, float z, float w) { b.putFloat(x).putFloat(y).putFloat(z).putFloat(w); }
    private static void uniform(int p, String name, int unit) { glUniform1i(glGetUniformLocation(p, name), unit); }
    private static void checkError(String step) { int error = glGetError(); if (error != GL_NO_ERROR) throw new AssertionError(step + ": GL error 0x" + Integer.toHexString(error)); }
    private static double median(long[] values) { long[] sorted = values.clone(); Arrays.sort(sorted); int n = sorted.length; return n % 2 == 1 ? sorted[n / 2] : sorted[n / 2 - 1] / 2D + sorted[n / 2] / 2D; }
    private static String hash(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
}
