import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Float32 CPU reference for the VL-only block-geometry hoist. Java 17+ uses
 * strict float evaluation; shader source guards and GPU A/B complement this
 * arithmetic test because a GPU driver can contract expressions differently. */
public final class PointShadowHoistTest
{
    private record V3(float x, float y, float z) {}
    private record Geometry(float x, float y, float faceUv, float halfX, float halfY) {}
    private record Face(int index, float u, float v) {}
    private record Sample(int guard, int face, float u, float v, float minU, float minV,
                          float maxU, float maxV, float depth, float bias) {}
    private static long checks, samples;
    private static final boolean[] facesSeen = new boolean[6];
    private static final boolean[] blocksSeen = new boolean[30];
    private static final boolean[] guardsSeen = new boolean[3];

    private static void check(boolean condition, String label)
    {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private static void same(float a, float b, String label)
    {
        if (Float.floatToIntBits(a) != Float.floatToIntBits(b))
            throw new AssertionError(label + ": " + a + " != " + b);
        checks++;
    }

    private static Face cubeFace(V3 d)
    {
        float ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
        int face;
        float u, v;
        if (ax >= ay && ax >= az)
        {
            face = d.x > 0 ? 0 : 1;
            u = (d.x > 0 ? -d.z : d.z) / ax;
            v = -d.y / ax;
        }
        else if (ay >= ax && ay >= az)
        {
            face = d.y > 0 ? 2 : 3;
            u = d.x / ay;
            v = (d.y > 0 ? d.z : -d.z) / ay;
        }
        else
        {
            face = d.z > 0 ? 4 : 5;
            u = (d.z > 0 ? d.x : -d.x) / az;
            v = -d.y / az;
        }
        return new Face(face, u * 0.5F + 0.5F, v * 0.5F + 0.5F);
    }

    private static float clamp(float x, float lo, float hi)
    {
        return Math.min(Math.max(x, lo), hi);
    }

    // Original irlite_pointAtlasUV: decode the global block anew for this tap,
    // then choose the face, compute tile bounds and clamp the requested UV.
    private static Sample reference(int block, V3 toLight, float dist, float radius,
                                    int atlas, float offset, float worldBias)
    {
        float refDist = dist - 2.0F * offset;
        if (refDist < 0.001F || refDist > radius) return new Sample(1, -1, 0, 0, 0, 0, 0, 0, 0, 0);
        float factor = -refDist / dist;
        V3 dir = new V3(toLight.x * factor, toLight.y * factor, toLight.z * factor);
        float near = 0.05F;
        float zPersp = Math.max(Math.abs(dir.x), Math.max(Math.abs(dir.y), Math.abs(dir.z)));
        float refDepth = (((radius + near) - 2.0F * radius * near / zPersp) / (radius - near)) * 0.5F + 0.5F;
        if (refDepth < 0.0F || refDepth > 1.0F) return new Sample(2, -1, 0, 0, 0, 0, 0, 0, 0, 0);
        float bias = worldBias * radius * near / Math.max(refDist * refDist * (radius - near), 1e-6F);

        int cell, sub, div;
        if (block < 2) { cell = block; sub = 0; div = 1; }
        else if (block < 14) { int j = block - 2; cell = 2 + j / 4; sub = j % 4; div = 2; }
        else { int j = block - 14; cell = 5 + j / 16; sub = j % 16; div = 4; }
        float faceUv = 1.0F / (6.0F * (float) div);
        float blockX = (float) (cell % 2) * 0.5F + (float) (sub % div) * (0.5F / (float) div);
        float blockY = (float) (cell / 2) * (1.0F / 3.0F) + (float) (sub / div) * ((1.0F / 3.0F) / (float) div);
        Face face = cubeFace(dir);
        float originX = blockX + (float) (face.index % 3) * faceUv;
        float originY = blockY + (float) (face.index / 3) * faceUv;
        float halfX = 0.5F / (float) atlas, halfY = 0.5F / (float) atlas;
        float minU = originX + halfX, minV = originY + halfY;
        float maxU = originX + faceUv - halfX, maxV = originY + faceUv - halfY;
        float u = clamp(originX + face.u * faceUv, minU, maxU);
        float v = clamp(originY + face.v * faceUv, minV, maxV);
        return new Sample(0, face.index, u, v, minU, minV, maxU, maxV, refDepth, bias);
    }

    // Prepared once per light. Float operation order mirrors the original
    // declaration expressions; no reciprocal substitution or reassociation.
    private static Geometry prepare(int block, int atlas)
    {
        int cell, sub, div;
        if (block < 2) { cell = block; sub = 0; div = 1; }
        else if (block < 14) { int j = block - 2; cell = 2 + j / 4; sub = j % 4; div = 2; }
        else { int j = block - 14; cell = 5 + j / 16; sub = j % 16; div = 4; }
        float faceUv = 1.0F / (6.0F * (float) div);
        float x = (float) (cell % 2) * 0.5F + (float) (sub % div) * (0.5F / (float) div);
        float y = (float) (cell / 2) * (1.0F / 3.0F) + (float) (sub / div) * ((1.0F / 3.0F) / (float) div);
        return new Geometry(x, y, faceUv, 0.5F / (float) atlas, 0.5F / (float) atlas);
    }

    private static Sample prepared(Geometry p, V3 toLight, float dist, float radius,
                                   float offset, float worldBias)
    {
        float refDist = dist - 2.0F * offset;
        if (refDist < 0.001F || refDist > radius) return new Sample(1, -1, 0, 0, 0, 0, 0, 0, 0, 0);
        float factor = -refDist / dist;
        V3 dir = new V3(toLight.x * factor, toLight.y * factor, toLight.z * factor);
        float near = 0.05F;
        float zPersp = Math.max(Math.abs(dir.x), Math.max(Math.abs(dir.y), Math.abs(dir.z)));
        float refDepth = (((radius + near) - 2.0F * radius * near / zPersp) / (radius - near)) * 0.5F + 0.5F;
        if (refDepth < 0.0F || refDepth > 1.0F) return new Sample(2, -1, 0, 0, 0, 0, 0, 0, 0, 0);
        float bias = worldBias * radius * near / Math.max(refDist * refDist * (radius - near), 1e-6F);

        Face face = cubeFace(dir);
        float originX = p.x + (float) (face.index % 3) * p.faceUv;
        float originY = p.y + (float) (face.index / 3) * p.faceUv;
        float minU = originX + p.halfX, minV = originY + p.halfY;
        float maxU = originX + p.faceUv - p.halfX, maxV = originY + p.faceUv - p.halfY;
        float u = clamp(originX + face.u * p.faceUv, minU, maxU);
        float v = clamp(originY + face.v * p.faceUv, minV, maxV);
        return new Sample(0, face.index, u, v, minU, minV, maxU, maxV, refDepth, bias);
    }

    private static void compare(int block, int atlas, Geometry geometry, V3 v,
                                float radius, float offset, float bias)
    {
        float dist = (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        Sample a = reference(block, v, dist, radius, atlas, offset, bias);
        Sample b = prepared(geometry, v, dist, radius, offset, bias);
        check(a.guard == b.guard, "same near/range/depth guard");
        guardsSeen[a.guard] = true;
        if (a.guard == 0)
        {
            facesSeen[a.face] = true;
            blocksSeen[block] = true;
            check(a.face == b.face, "same face selection and ties");
            same(a.u, b.u, "U"); same(a.v, b.v, "V");
            same(a.minU, b.minU, "min U"); same(a.minV, b.minV, "min V");
            same(a.maxU, b.maxU, "max U"); same(a.maxV, b.maxV, "max V");
            same(a.depth, b.depth, "perspective depth"); same(a.bias, b.bias, "depth bias");
            float cmp = a.depth - a.bias;
            for (float stored : new float[]{0, 1, cmp, Math.nextDown(cmp), Math.nextUp(cmp)})
                check((a.depth - a.bias > stored) == (b.depth - b.bias > stored), "same depth comparison at exact borders");
        }
        samples++;
    }

    public static void main(String[] args)
    {
        List<V3> directions = new ArrayList<>();
        for (float x : new float[]{-1, -0F, 0F, 1})
            for (float y : new float[]{-1, -0F, 0F, 1})
                for (float z : new float[]{-1, -0F, 0F, 1}) directions.add(new V3(x, y, z));
        for (float edge : new float[]{Math.nextDown(1F), 1F, Math.nextUp(1F)})
            for (float sign : new float[]{-1, 1})
            {
                directions.add(new V3(edge * sign, 1, 1));
                directions.add(new V3(1, edge * sign, 1));
                directions.add(new V3(1, 1, edge * sign));
            }
        check(cubeFace(new V3(1, 1, 1)).index == 0, "XYZ tie chooses +X");
        check(cubeFace(new V3(-1, 1, 1)).index == 1, "XYZ tie chooses -X");
        check(cubeFace(new V3(0, 1, 1)).index == 2, "YZ tie chooses +Y");
        check(cubeFace(new V3(0, -1, 1)).index == 3, "YZ tie chooses -Y");
        Random random = new Random(0xB10C);
        for (int block = 0; block < 30; block++)
            for (int tile : new int[]{256, 512, 1024, 2048})
            {
                int atlas = tile * 6;
                Geometry geometry = prepare(block, atlas);
                for (V3 direction : directions)
                    for (float length : new float[]{0, 0.001F, 0.05F, 0.1F, 0.15F, 1F, 32F, 512F})
                        for (float radius : new float[]{0.001F, 0.05F, 0.15F, 1F, 32F, 512F})
                            for (float offset : new float[]{0, 0.05F})
                                compare(block, atlas, geometry,
                                    new V3(direction.x * length, direction.y * length, direction.z * length),
                                    radius, offset, 0.05F);
                for (int i = 0; i < 500; i++)
                {
                    V3 v = new V3(random.nextFloat() * 300 - 150, random.nextFloat() * 300 - 150,
                        random.nextFloat() * 300 - 150);
                    compare(block, atlas, geometry, v, 300F, random.nextFloat() * 0.2F, random.nextFloat() * 0.1F);
                }
            }
        for (boolean seen : facesSeen) check(seen, "all six sampled faces covered");
        for (boolean seen : blocksSeen) check(seen, "all thirty blocks sampled");
        for (boolean seen : guardsSeen) check(seen, "both early returns and real taps covered");
        System.out.println("Point shadow hoist passed: " + checks + " bit-exact checks, " + samples
            + " samples, 30 blocks, 6 faces, 4 atlas sizes, face ties/signed zero/near/depth/clamp borders.");
    }
}
