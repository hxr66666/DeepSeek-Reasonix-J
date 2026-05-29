package com.reasonix.code.antlr.json;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JsonLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".json");
    private static final Set<String> CLASS_RULES = Set.of();
    private static final Set<String> FUNCTION_RULES = Set.of();
    private static final Set<String> INTERFACE_RULES = Set.of();
    private static final Set<String> ENUM_RULES = Set.of();
    private static final Set<String> FIELD_RULES = Set.of("pair");

    static {
        AntlrLanguageSupport.register(new JsonLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new JSONLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new JSONParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((JSONParser) parser).json();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree, String[] ruleNames) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        AntlrSymbolExtractor.extractByRuleNames(tree, ruleNames, symbols, CLASS_RULES, FUNCTION_RULES, INTERFACE_RULES, ENUM_RULES, FIELD_RULES);
        return symbols;
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }
}
