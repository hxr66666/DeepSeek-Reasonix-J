package com.reasonix.code.antlr;

import org.antlr.v4.runtime.*;

public abstract class JavaParserBase extends Parser {

    public JavaParserBase(TokenStream input) {
        super(input);
    }

    public boolean DoLastRecordComponent() {
        ParserRuleContext ctx = this.getContext();
        if (!(ctx instanceof JavaParser.RecordComponentListContext)) {
            return true;
        }

        JavaParser.RecordComponentListContext tctx = (JavaParser.RecordComponentListContext) ctx;
        var rcs = tctx.recordComponent();
        if (rcs.isEmpty()) return true;

        int count = rcs.size();
        for (int c = 0; c < count; ++c) {
            JavaParser.RecordComponentContext rc = rcs.get(c);
            if (rc.ELLIPSIS() != null && c + 1 < count)
                return false;
        }
        return true;
    }

    public boolean IsNotIdentifierAssign() {
        int la = this._input.LA(1);
        switch (la) {
            case JavaParser.IDENTIFIER:
            case JavaParser.MODULE:
            case JavaParser.OPEN:
            case JavaParser.REQUIRES:
            case JavaParser.EXPORTS:
            case JavaParser.OPENS:
            case JavaParser.TO:
            case JavaParser.USES:
            case JavaParser.PROVIDES:
            case JavaParser.WHEN:
            case JavaParser.WITH:
            case JavaParser.TRANSITIVE:
            case JavaParser.YIELD:
            case JavaParser.SEALED:
            case JavaParser.PERMITS:
            case JavaParser.RECORD:
            case JavaParser.VAR:
                break;
            default:
                return true;
        }
        int la2 = this._input.LA(2);
        if (la2 != JavaParser.ASSIGN) return true;
        return false;
    }
}
