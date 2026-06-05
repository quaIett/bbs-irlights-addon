package org.wemppy.irlite.client.patcher;

/**
 * Parser for the .irlpatch DSL.
 *
 * <pre>
 * # comment (only outside a &lt;&lt;&lt; ... &gt;&gt;&gt; body)
 * @name    Human readable name
 * @target  ShaderpackName        # informational
 * @marker  IRLITE
 *
 * +file path/relative/to/pack/new.glsl
 * &lt;&lt;&lt;
 * ...whole file content...
 * &gt;&gt;&gt;
 *
 * @file path/relative/to/pack/existing.glsl
 * after "literal anchor"
 * &lt;&lt;&lt;
 * ...inserted text...
 * &gt;&gt;&gt;
 * before "literal anchor"
 * &lt;&lt;&lt;
 * ...inserted text...
 * &gt;&gt;&gt;
 * replace "literal to replace"
 * &lt;&lt;&lt;
 * ...replacement...
 * &gt;&gt;&gt;
 * </pre>
 *
 * Anchors are literal substrings (with \\n, \\t, \\", \\\\ escapes). A body is
 * the raw text between a line that is exactly {@code <<<} and a line that is
 * exactly {@code >>>}.
 */
public final class IrlPatchParser
{
    public static final class ParseException extends Exception
    {
        public ParseException(String message)
        {
            super(message);
        }
    }

    private final String[] lines;
    private int i;
    private String currentFile;

    private IrlPatchParser(String text)
    {
        this.lines = text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
    }

    public static IrlPatch parse(String text) throws ParseException
    {
        return new IrlPatchParser(text).run();
    }

    private IrlPatch run() throws ParseException
    {
        IrlPatch patch = new IrlPatch();

        while (this.i < this.lines.length)
        {
            String raw = this.lines[this.i];
            String line = raw.trim();
            this.i++;

            if (line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            if (line.startsWith("@name "))
            {
                patch.name = line.substring(6).trim();
            }
            else if (line.startsWith("@target "))
            {
                patch.target = line.substring(8).trim();
            }
            else if (line.startsWith("@marker "))
            {
                patch.marker = line.substring(8).trim();
            }
            else if (line.startsWith("+file "))
            {
                String path = normalize(line.substring(6).trim());
                String body = readBody();
                patch.ops.add(new IrlPatch.Op(IrlPatch.Kind.ADD_FILE, path, null, body));
            }
            else if (line.startsWith("@file "))
            {
                this.currentFile = normalize(line.substring(6).trim());
            }
            else if (line.startsWith("after ") || line.startsWith("before ") || line.startsWith("replace "))
            {
                IrlPatch.Kind kind = line.startsWith("after ") ? IrlPatch.Kind.AFTER
                    : line.startsWith("before ") ? IrlPatch.Kind.BEFORE
                    : IrlPatch.Kind.REPLACE;

                if (this.currentFile == null)
                {
                    throw new ParseException("line " + this.i + ": '" + word(line) + "' before any @file");
                }

                String anchor = unquote(line.substring(line.indexOf(' ') + 1).trim());
                String body = readBody();
                patch.ops.add(new IrlPatch.Op(kind, this.currentFile, anchor, body));
            }
            else
            {
                throw new ParseException("line " + this.i + ": unrecognized directive: " + line);
            }
        }

        if (patch.ops.isEmpty())
        {
            throw new ParseException("patch has no operations");
        }

        return patch;
    }

    /** Reads a {@code <<<} ... {@code >>>} block; returns the inner text. */
    private String readBody() throws ParseException
    {
        // Skip blank / comment lines until the opening fence.
        while (this.i < this.lines.length)
        {
            String t = this.lines[this.i].trim();
            if (t.isEmpty() || t.startsWith("#"))
            {
                this.i++;
                continue;
            }
            break;
        }

        if (this.i >= this.lines.length || !this.lines[this.i].trim().equals("<<<"))
        {
            throw new ParseException("line " + (this.i + 1) + ": expected '<<<' body opener");
        }
        this.i++;

        StringBuilder body = new StringBuilder();
        boolean first = true;

        while (this.i < this.lines.length)
        {
            String raw = this.lines[this.i];
            if (raw.trim().equals(">>>"))
            {
                this.i++;
                return body.toString();
            }

            if (!first)
            {
                body.append('\n');
            }
            body.append(raw);
            first = false;
            this.i++;
        }

        throw new ParseException("unterminated body (missing '>>>')");
    }

    private static String normalize(String path)
    {
        return path.replace('\\', '/').replaceAll("^/+", "");
    }

    private static String word(String line)
    {
        int sp = line.indexOf(' ');
        return sp < 0 ? line : line.substring(0, sp);
    }

    private static String unquote(String s) throws ParseException
    {
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"')
        {
            throw new ParseException("anchor must be in double quotes: " + s);
        }

        String inner = s.substring(1, s.length() - 1);
        StringBuilder out = new StringBuilder(inner.length());

        for (int k = 0; k < inner.length(); k++)
        {
            char c = inner.charAt(k);
            if (c == '\\' && k + 1 < inner.length())
            {
                char n = inner.charAt(++k);
                switch (n)
                {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    default: out.append(n); break;
                }
            }
            else
            {
                out.append(c);
            }
        }

        return out.toString();
    }
}
