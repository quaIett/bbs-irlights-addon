package qualet.irlite.client.light;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Random;

/** Numerical equivalence of allocating traversal math and the production scratch
 *  storage. Runs without a Minecraft/GL context after compileClientJava. */
public final class LightCollectorMathTest
{
    private static int checks;

    public static void main(String[] args)
    {
        var scratch = new LightCollector.TraversalScratch();
        var borrowed = new LightCollector.ScratchFrame[40];
        for (int i = 0; i < borrowed.length; i++) borrowed[i] = scratch.push();
        scratch.rewind(0);
        for (var frame : borrowed) check(scratch.push() == frame, "reuse frames after growing the stack");
        scratch.rewind(0);

        Random random = new Random(0x49524C697465L);
        for (int frame = 0; frame < 256; frame++)
        {
            int mark = scratch.mark();
            try
            {
                var root = scratch.push();
                Matrix4f expected = new Matrix4f().identity();
                root.local.identity();
                if ((frame & 1) == 0)
                {
                    expected.translate(0.5F, 0F, 0.5F);
                    root.local.translate(0.5F, 0F, 0.5F);
                    Matrix4f props = transform(random);
                    expected.mul(new Matrix4f(props));
                    root.transform.identity().mul(props);
                    root.local.mul(root.transform);
                }
                else
                {
                    float yaw = random.nextFloat() * 6.28F;
                    expected.rotateY(yaw);
                    root.local.rotateY(yaw);
                }
                visit(expected, root.local, scratch, random, 1 + frame % 32);
            }
            finally
            {
                scratch.rewind(mark);
            }
            check(scratch.mark() == 0, "balanced frame traversal");
        }

        var outer = scratch.push();
        outer.local.translation(7, 8, 9);
        int mark = scratch.mark();
        try
        {
            scratch.push().local.translation(-1, -2, -3);
            throw new IllegalStateException("simulated callback failure");
        }
        catch (IllegalStateException expected)
        {
            check(expected.getMessage().contains("callback"), "exception propagated through scratch scope");
        }
        finally
        {
            scratch.rewind(mark);
        }
        check(scratch.mark() == 1, "failed nested traversal restores outer depth");
        equal(new Matrix4f().translation(7, 8, 9), outer.local);
        check(scratch.push() == borrowed[1], "failed traversal does not strand storage");
        scratch.rewind(0);
        System.out.println("Light collector math checks passed: " + checks);
    }

    private static void visit(Matrix4f expectedParent, Matrix4f actualParent,
                              LightCollector.TraversalScratch scratch, Random random, int depth)
    {
        int mark = scratch.mark();
        try
        {
            var frame = scratch.push();
            Matrix4f expected = new Matrix4f(expectedParent);
            frame.local.set(actualParent);
            if (random.nextInt(4) != 0)
            {
                Matrix4f form = transform(random);
                expected.mul(new Matrix4f(form));
                frame.transform.identity().mul(form);
                frame.local.mul(frame.transform);
            }
            equal(expected, frame.local);

            Vector4f origin = expected.transform(new Vector4f(0, 0, 0, 1));
            Vector4f forward = expected.transform(new Vector4f(0, 0, 1, 0));
            frame.local.transform(frame.origin.set(0, 0, 0, 1));
            frame.local.transform(frame.forward.set(0, 0, 1, 0));
            equal(origin, frame.origin);
            equal(forward, frame.forward);
            for (double base : new double[] {0, 100_000.125, -29_999_999.75})
            {
                check(Double.doubleToRawLongBits(base + origin.x) == Double.doubleToRawLongBits(base + frame.origin.x),
                    "preserve double world-coordinate addition");
            }

            if (depth == 0) return;
            // One deep child plus a sibling exercises every stack level without
            // making the fixture exponentially large.
            for (int child = 0; child < 2; child++)
            {
                Matrix4f expectedChild = new Matrix4f(expected);
                frame.child.set(frame.local);
                if (random.nextBoolean())
                {
                    Matrix4f part = transform(random);
                    expectedChild.mul(new Matrix4f(part));
                    frame.transform.identity().mul(part);
                    frame.child.mul(frame.transform);
                }
                visit(expectedChild, frame.child, scratch, random, child == 0 ? depth - 1 : 0);
                equal(expected, frame.local);
            }
        }
        finally
        {
            scratch.rewind(mark);
        }
    }

    private static Matrix4f transform(Random random)
    {
        return new Matrix4f().translate(random.nextFloat() * 4 - 2, random.nextFloat() * 4 - 2,
                random.nextFloat() * 4 - 2)
            .rotateXYZ(random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3)
            .rotateZYX(random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3, random.nextFloat() * 6 - 3)
            .scale(scale(random), scale(random), scale(random));
    }

    private static float scale(Random random)
    {
        return random.nextInt(12) == 0 ? 0 : (random.nextBoolean() ? 1 : -1) * (0.25F + random.nextFloat());
    }

    private static void equal(Matrix4f expected, Matrix4f actual)
    {
        for (int column = 0; column < 4; column++)
            for (int row = 0; row < 4; row++)
                equal(expected.get(column, row), actual.get(column, row));
    }

    private static void equal(Vector4f expected, Vector4f actual)
    {
        equal(expected.x, actual.x); equal(expected.y, actual.y);
        equal(expected.z, actual.z); equal(expected.w, actual.w);
    }

    private static void equal(float expected, float actual)
    {
        check(Float.floatToRawIntBits(expected) == Float.floatToRawIntBits(actual), "identical float math");
    }

    private static void check(boolean condition, String message)
    {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
