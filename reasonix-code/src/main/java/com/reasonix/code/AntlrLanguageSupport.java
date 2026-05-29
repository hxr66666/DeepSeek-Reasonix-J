package com.reasonix.code;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AntlrLanguageSupport {

    Lexer createLexer(CharStream input);

    Parser createParser(CommonTokenStream tokens);

    ParserRuleContext parse(Parser parser);

    default List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        return List.of();
    }

    default List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree, String[] ruleNames) {
        return extractSymbols(tree);
    }

    Set<String> supportedExtensions();

    static final Map<String, AntlrLanguageSupport> REGISTRY = new HashMap<>();

    static void register(AntlrLanguageSupport support) {
        for (String ext : support.supportedExtensions()) {
            REGISTRY.put(ext, support);
        }
    }

    static AntlrLanguageSupport forExtension(String ext) {
        return REGISTRY.get(ext);
    }

    static Set<String> supportedExtensionsAll() {
        return REGISTRY.keySet();
    }
}
