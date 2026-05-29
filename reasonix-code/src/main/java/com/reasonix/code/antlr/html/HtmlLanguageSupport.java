package com.reasonix.code.antlr.html;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.AntlrSymbolExtractor;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HtmlLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".html", ".htm");
    private static final Set<String> CLASS_RULES = Set.of();
    private static final Set<String> FUNCTION_RULES = Set.of("script");
    private static final Set<String> INTERFACE_RULES = Set.of();
    private static final Set<String> ENUM_RULES = Set.of();

    static {
        AntlrLanguageSupport.register(new HtmlLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new HTMLLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new HTMLParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((HTMLParser) parser).htmlDocument();
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
