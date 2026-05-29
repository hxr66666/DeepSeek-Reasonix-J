package com.reasonix.code.antlr.scala;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScalaLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".scala");
    private static final Set<String> CLASS_RULES = Set.of("classDef", "objectDef", "enumDef", "givenDef");
    private static final Set<String> FUNCTION_RULES = Set.of("defDef");
    private static final Set<String> INTERFACE_RULES = Set.of();
    private static final Set<String> ENUM_RULES = Set.of("enumCase");

    static {
        AntlrLanguageSupport.register(new ScalaLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new Scala3Lexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new Scala3Parser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((Scala3Parser) parser).compilationUnit();
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
