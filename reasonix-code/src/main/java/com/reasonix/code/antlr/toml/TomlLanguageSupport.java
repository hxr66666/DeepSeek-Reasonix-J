package com.reasonix.code.antlr.toml;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TomlLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".toml");
    private static final Set<String> CLASS_RULES = Set.of("standard_table", "array_table");
    private static final Set<String> FUNCTION_RULES = Set.of();
    private static final Set<String> INTERFACE_RULES = Set.of();
    private static final Set<String> ENUM_RULES = Set.of();
    private static final Set<String> FIELD_RULES = Set.of("key_value");

    static {
        AntlrLanguageSupport.register(new TomlLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new TomlLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new TomlParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((TomlParser) parser).document();
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
