package com.reasonix.code.antlr.css;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CssLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".css");
    private static final Set<String> CLASS_RULES = Set.of("selector");
    private static final Set<String> FUNCTION_RULES = Set.of();
    private static final Set<String> INTERFACE_RULES = Set.of();
    private static final Set<String> ENUM_RULES = Set.of();

    static {
        AntlrLanguageSupport.register(new CssLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new css3Lexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new css3Parser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((css3Parser) parser).stylesheet();
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
