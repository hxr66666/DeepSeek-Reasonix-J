package com.reasonix.code.antlr.javascript;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JavaScriptLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".js", ".mjs", ".cjs", ".jsx");

    static {
        AntlrLanguageSupport.register(new JavaScriptLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new JavaScriptLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new JavaScriptParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((JavaScriptParser) parser).program();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        walkJsTree(tree, symbols);
        return symbols;
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree, String[] ruleNames) {
        return extractSymbols(tree);
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    private void walkJsTree(ParseTree ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx instanceof JavaScriptParser.ClassDeclarationContext cls) {
            symbols.add(new SymbolExtractor.Symbol(
                    cls.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    cls.getStart().getLine()));
        } else if (ctx instanceof JavaScriptParser.FunctionDeclarationContext fn) {
            symbols.add(new SymbolExtractor.Symbol(
                    fn.identifier().getText(),
                    SymbolExtractor.SymbolType.FUNCTION,
                    fn.getStart().getLine()));
        } else if (ctx instanceof JavaScriptParser.MethodDefinitionContext method) {
            var elementName = method.classElementName();
            if (elementName != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        elementName.getText(),
                        SymbolExtractor.SymbolType.METHOD,
                        method.getStart().getLine()));
            }
        } else if (ctx instanceof JavaScriptParser.FieldDefinitionContext field) {
            var elementName = field.classElementName();
            if (elementName != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        elementName.getText(),
                        SymbolExtractor.SymbolType.FIELD,
                        field.getStart().getLine()));
            }
        } else if (ctx instanceof JavaScriptParser.FunctionPropertyContext fnProp) {
            symbols.add(new SymbolExtractor.Symbol(
                    fnProp.propertyName().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    fnProp.getStart().getLine()));
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            walkJsTree(ctx.getChild(i), symbols);
        }
    }
}
