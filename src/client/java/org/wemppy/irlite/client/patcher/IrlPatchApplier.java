package org.wemppy.irlite.client.patcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Applies an {@link IrlPatch} by copying a clean source shaderpack into a fresh
 * output folder and editing files there. Never touches the source. On any error
 * the partial output is removed so the user is never left with a half-patched pack.
 */
public final class IrlPatchApplier
{
    private IrlPatchApplier()
    {}

    public static PatchResult apply(Path sourcePack, Path outputPack, IrlPatch patch)
    {
        PatchResult result = new PatchResult();

        if (sourcePack == null || !Files.isDirectory(sourcePack))
        {
            result.fail("source pack is not a folder: " + sourcePack);
            return result;
        }

        try
        {
            if (Files.exists(outputPack))
            {
                deleteRecursive(outputPack);
                result.info("Removed existing output: " + outputPack.getFileName());
            }

            copyRecursive(sourcePack, outputPack);
            result.info("Copied pack -> " + outputPack.getFileName());

            for (IrlPatch.Op op : patch.ops)
            {
                applyOp(outputPack, op, result);
            }

            result.summary = "Patched OK -> " + outputPack.getFileName() + " (" + patch.ops.size() + " ops)";
            result.info(result.summary);
            return result;
        }
        catch (PatchException e)
        {
            result.fail(e.getMessage());
            tryCleanup(outputPack, result);
            return result;
        }
        catch (IOException e)
        {
            result.fail("IO error: " + e.getMessage());
            tryCleanup(outputPack, result);
            return result;
        }
    }

    private static void applyOp(Path pack, IrlPatch.Op op, PatchResult result) throws IOException, PatchException
    {
        if (op.kind == IrlPatch.Kind.ADD_FILE)
        {
            Path target = pack.resolve(op.file);
            Files.createDirectories(target.getParent());
            Files.writeString(target, op.body, StandardCharsets.UTF_8);
            result.info("+file " + op.file);
            return;
        }

        Path target = pack.resolve(op.file);
        if (!Files.isRegularFile(target))
        {
            throw new PatchException("target file not found in pack: " + op.file);
        }

        String content = Files.readString(target, StandardCharsets.UTF_8);
        int count = countOccurrences(content, op.anchor);

        if (count == 0)
        {
            throw new PatchException("anchor not found in " + op.file + ": \"" + preview(op.anchor) + "\"");
        }
        if (count > 1)
        {
            throw new PatchException("anchor is ambiguous (" + count + " matches) in " + op.file + ": \"" + preview(op.anchor) + "\"");
        }

        String patched;
        switch (op.kind)
        {
            case AFTER:
                patched = content.replace(op.anchor, op.anchor + "\n" + op.body);
                result.info("after \"" + preview(op.anchor) + "\" in " + op.file);
                break;
            case BEFORE:
                patched = content.replace(op.anchor, op.body + "\n" + op.anchor);
                result.info("before \"" + preview(op.anchor) + "\" in " + op.file);
                break;
            case REPLACE:
            default:
                patched = content.replace(op.anchor, op.body);
                result.info("replace \"" + preview(op.anchor) + "\" in " + op.file);
                break;
        }

        Files.writeString(target, patched, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String haystack, String needle)
    {
        if (needle.isEmpty())
        {
            return 0;
        }

        int count = 0;
        int from = 0;
        int idx;
        while ((idx = haystack.indexOf(needle, from)) >= 0)
        {
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    private static void copyRecursive(Path source, Path dest) throws IOException
    {
        try (Stream<Path> walk = Files.walk(source))
        {
            for (Path src : (Iterable<Path>) walk::iterator)
            {
                Path rel = source.relativize(src);
                Path dst = dest.resolve(rel.toString());
                if (Files.isDirectory(src))
                {
                    Files.createDirectories(dst);
                }
                else
                {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst);
                }
            }
        }
    }

    private static void deleteRecursive(Path path) throws IOException
    {
        try (Stream<Path> walk = Files.walk(path))
        {
            walk.sorted(Comparator.reverseOrder()).forEach(p ->
            {
                try { Files.delete(p); }
                catch (IOException ignored) {}
            });
        }
    }

    private static void tryCleanup(Path outputPack, PatchResult result)
    {
        try
        {
            if (outputPack != null && Files.exists(outputPack))
            {
                deleteRecursive(outputPack);
                result.info("Rolled back partial output.");
            }
        }
        catch (IOException ignored)
        {}
    }

    private static String preview(String s)
    {
        String oneLine = s.replace("\n", "\\n");
        return oneLine.length() > 60 ? oneLine.substring(0, 57) + "..." : oneLine;
    }

    private static final class PatchException extends Exception
    {
        PatchException(String message)
        {
            super(message);
        }
    }
}
