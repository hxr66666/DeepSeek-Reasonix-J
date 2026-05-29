package com.reasonix.code;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AntlrSymbolExtractor {

    private static final Logger log = LoggerFactory.getLogger(AntlrSymbolExtractor.class);

    public List<SymbolExtractor.Symbol> extractSymbols(Path filePath, String content) {
        String fileName = filePath.getFileName().toString();
        return extractSymbols(content, fileName);
    }

    public List<SymbolExtractor.Symbol> extractSymbols(String content, String fileName) {
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";

        AntlrLanguageSupport support = AntlrLanguageSupport.forExtension(ext);
        if (support == null) {
            return List.of();
        }

        return doExtract(support, content);
    }

    private List<SymbolExtractor.Symbol> doExtract(AntlrLanguageSupport support, String content) {
        CharStream input = CharStreams.fromString(content);
        Lexer lexer = support.createLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        Parser parser = support.createParser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine,
                                    String msg, RecognitionException e) {
                log.debug("ANTLR parse error at line {} col {}: {}", line, charPositionInLine, msg);
            }
        });

        ParserRuleContext tree = null;
        try {
            tree = support.parse(parser);
        } catch (Exception e) {
            log.debug("ANTLR parsing failed: {}", e.getMessage());
        }

        if (tree == null) {
            return List.of();
        }

        String[] ruleNames = parser.getRuleNames();

        List<SymbolExtractor.Symbol> result;
        try {
            result = support.extractSymbols(tree, ruleNames);
        } finally {
            releaseTree(tree);
            parser.setTokenStream(null);
        }

        return result;
    }

    private void releaseTree(ParserRuleContext tree) {
        if (tree == null) return;
        try {
            releaseChildren(tree);
            tree.children = null;
            tree.parent = null;
            tree.start = null;
            tree.stop = null;
            tree.exception = null;
        } catch (Exception e) {
            log.debug("Error releasing parse tree: {}", e.getMessage());
        }
    }

    private void releaseChildren(ParserRuleContext ctx) {
        if (ctx.children == null) return;
        for (var child : ctx.children) {
            if (child instanceof ParserRuleContext childCtx) {
                releaseChildren(childCtx);
                childCtx.children = null;
                childCtx.parent = null;
                childCtx.start = null;
                childCtx.stop = null;
                childCtx.exception = null;
            } else if (child instanceof TerminalNode term) {
                term.setParent(null);
            }
        }
    }

    public static void extractByRuleNames(ParserRuleContext tree, String[] ruleNames,
                                          List<SymbolExtractor.Symbol> symbols,
                                          Set<String> classRules, Set<String> functionRules,
                                          Set<String> interfaceRules, Set<String> enumRules) {
        extractByRuleNames(tree, ruleNames, symbols, classRules, functionRules, interfaceRules, enumRules, null);
    }

    public static void extractByRuleNames(ParserRuleContext tree, String[] ruleNames,
                                          List<SymbolExtractor.Symbol> symbols,
                                          Set<String> classRules, Set<String> functionRules,
                                          Set<String> interfaceRules, Set<String> enumRules,
                                          Set<String> fieldRules) {
        if (tree == null) return;
        walkTree(tree, ruleNames, symbols, classRules, functionRules, interfaceRules, enumRules, fieldRules);
    }

    private static void walkTree(ParseTree ctx, String[] ruleNames,
                                 List<SymbolExtractor.Symbol> symbols,
                                 Set<String> classRules, Set<String> functionRules,
                                 Set<String> interfaceRules, Set<String> enumRules,
                                 Set<String> fieldRules) {
        if (ctx instanceof ParserRuleContext ruleCtx) {
            int idx = ruleCtx.getRuleIndex();
            String ruleName = idx >= 0 && idx < ruleNames.length ? ruleNames[idx] : "";

            if (classRules.contains(ruleName)) {
                String name = extractIdentifier(ruleCtx);
                if (name != null) {
                    symbols.add(new SymbolExtractor.Symbol(name, SymbolExtractor.SymbolType.CLASS, ruleCtx.getStart().getLine()));
                }
            } else if (functionRules.contains(ruleName)) {
                String name = extractIdentifier(ruleCtx);
                if (name != null) {
                    symbols.add(new SymbolExtractor.Symbol(name, SymbolExtractor.SymbolType.FUNCTION, ruleCtx.getStart().getLine()));
                }
            } else if (interfaceRules.contains(ruleName)) {
                String name = extractIdentifier(ruleCtx);
                if (name != null) {
                    symbols.add(new SymbolExtractor.Symbol(name, SymbolExtractor.SymbolType.INTERFACE, ruleCtx.getStart().getLine()));
                }
            } else if (enumRules.contains(ruleName)) {
                String name = extractIdentifier(ruleCtx);
                if (name != null) {
                    symbols.add(new SymbolExtractor.Symbol(name, SymbolExtractor.SymbolType.ENUM, ruleCtx.getStart().getLine()));
                }
            } else if (fieldRules != null && fieldRules.contains(ruleName)) {
                String name = extractIdentifier(ruleCtx);
                if (name != null) {
                    symbols.add(new SymbolExtractor.Symbol(name, SymbolExtractor.SymbolType.FIELD, ruleCtx.getStart().getLine()));
                }
            }
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            walkTree(ctx.getChild(i), ruleNames, symbols, classRules, functionRules, interfaceRules, enumRules, fieldRules);
        }
    }

    private static String extractIdentifier(ParserRuleContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            var child = ctx.getChild(i);
            if (child instanceof TerminalNode term) {
                if (term.getSymbol().getType() > 0) {
                    String text = term.getText();
                    if (text != null && !text.isEmpty() && Character.isJavaIdentifierStart(text.charAt(0))) {
                        return text;
                    }
                }
            } else if (child instanceof ParserRuleContext childCtx) {
                String name = extractIdentifier(childCtx);
                if (name != null) return name;
            }
        }
        return null;
    }
}
