package com.reasonix.code.antlr.kotlin;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KotlinLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".kt", ".kts");
    private static final Set<String> CLASS_RULES = Set.of("classDeclaration", "objectDeclaration");
    private static final Set<String> FUNCTION_RULES = Set.of("functionDeclaration");
    private static final Set<String> INTERFACE_RULES = Set.of();
    private static final Set<String> ENUM_RULES = Set.of("enumEntry");

    static {
        AntlrLanguageSupport.register(new KotlinLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new KotlinLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new KotlinParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((KotlinParser) parser).kotlinFile();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree, String[] ruleNames) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        AntlrSymbolExtractor.extractByRuleNames(tree, ruleNames, symbols, CLASS_RULES, FUNCTION_RULES, INTERFACE_RULES, ENUM_RULES);
        return symbols;
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }
}
