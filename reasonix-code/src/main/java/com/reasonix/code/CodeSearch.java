package com.reasonix.code;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CodeSearch {

    private static final Logger log = LoggerFactory.getLogger(CodeSearch.class);

    public enum CodeMatchKind { call, definition, reference }

    public record CodeMatch(int line, int column, CodeMatchKind kind, String snippet) {}

    public record FindOptions(CodeMatchKind kind) {
        public static final FindOptions ANY = new FindOptions(null);
    }

    private static final Set<String> DEFINITION_RULES = Set.of(
            "classDeclaration", "interfaceDeclaration", "enumDeclaration",
            "methodDeclaration", "constructorDeclaration", "fieldDeclaration",
            "variableDeclarator", "typeDeclaration", "recordDeclaration",
            "annotationConstantRest", "packageDeclaration", "importDeclaration"
    );

    private static final Set<String> CALL_RULES = Set.of(
            "methodInvocation", "superMethodInvocation", "explicitConstructorInvocation",
            "fieldAccess", "arrayAccess", "arrayCreation"
    );

    public List<CodeMatch> findInCode(Path filePath, String source, String name, FindOptions opts) {
        List<CodeMatch> matches = new ArrayList<>();
        String ext = getExtension(filePath);
        AntlrLanguageSupport support = AntlrLanguageSupport.forExtension(ext);
        if (support == null) return matches;

        String[] sourceLines = source.split("\r?\n");

        try {
            Lexer lexer = support.createLexer(CharStreams.fromString(source));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            Parser parser = support.createParser(tokens);
            ParserRuleContext tree = support.parse(parser);

            new ParseTreeWalker().walk(new ParseTreeListener() {
                @Override
                public void enterEveryRule(ParserRuleContext ctx) {
                    checkNode(ctx, sourceLines, name, opts, matches);
                }

                @Override
                public void exitEveryRule(ParserRuleContext ctx) {
                }

                @Override
                public void visitTerminal(TerminalNode node) {
                }

                @Override
                public void visitErrorNode(ErrorNode node) {
                }
            }, tree);

        } catch (Exception e) {
            log.debug("Failed to parse code for file {}: {}", filePath, e.getMessage());
        }

        return matches;
    }

    private void checkNode(ParserRuleContext ctx, String[] sourceLines, String name, FindOptions opts, List<CodeMatch> matches) {
        if (ctx.getChildCount() != 1) return;
        if (!(ctx.getChild(0) instanceof Token token)) return;
        if (!token.getText().equals(name)) return;

        CodeMatchKind kind = classify(ctx);
        if (opts.kind() != null && opts.kind() != kind) return;

        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine() + 1;
        String snippet = line > 0 && line <= sourceLines.length ? sourceLines[line - 1] : "";

        matches.add(new CodeMatch(line, column, kind, snippet));
    }

    private CodeMatchKind classify(ParserRuleContext ctx) {
        ParserRuleContext parent = ctx.getParent();
        if (parent == null) return CodeMatchKind.reference;

        String parentRuleName = getRuleName(parent);
        if (DEFINITION_RULES.contains(parentRuleName)) {
            return CodeMatchKind.definition;
        }
        if (CALL_RULES.contains(parentRuleName)) {
            return CodeMatchKind.call;
        }
        return CodeMatchKind.reference;
    }

    private String getRuleName(ParserRuleContext ctx) {
        return ctx.getClass().getSimpleName()
                .replace("Context", "")
                .replaceFirst("^([A-Z])", "_$1")
                .toLowerCase()
                .replaceFirst("^_", "");
    }

    private String getExtension(Path filePath) {
        String name = filePath.toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot) : "";
    }
}