package org.wemppy.irlite.client.patcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed representation of an .irlpatch file. See {@link IrlPatchParser} for the
 * DSL grammar.
 */
public final class IrlPatch
{
    public enum Kind
    {
        ADD_FILE,   // create a new file (body = whole content)
        AFTER,      // insert body right after the anchor
        BEFORE,     // insert body right before the anchor
        REPLACE     // replace the anchor literal with body
    }

    public static final class Op
    {
        public final Kind kind;
        public final String file;
        public final String anchor;  // null for ADD_FILE
        public final String body;

        public Op(Kind kind, String file, String anchor, String body)
        {
            this.kind = kind;
            this.file = file;
            this.anchor = anchor;
            this.body = body;
        }
    }

    public String name = "";
    public String target = "";
    public String marker = "IRLITE";
    public final List<Op> ops = new ArrayList<>();
}
