package com.reasonix.code.antlr.csharp;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CSharpLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".cs");
    private static final Set<String> CLASS_RULES = Set.of("class_definition", "struct_definition");
    private static final Set<String> FUNCTION_RULES = Set.of("method_declaration", "constructor_declaration");
    private static final Set<String> INTERFACE_RULES = Set.of("interface_definition");
    private static final Set<String> ENUM_RULES = Set.of("enum_definition");

    static {
        AntlrLanguageSupport.register(new CSharpLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new CSharpLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new CSharpParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((CSharpParser) parser).compilation_unit();
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
