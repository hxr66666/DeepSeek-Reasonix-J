package com.reasonix.code.antlr.python;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PythonLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".py", ".pyw");

    static {
        AntlrLanguageSupport.register(new PythonLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new Python3Lexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new Python3Parser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((Python3Parser) parser).file_input();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        walkPythonTree(tree, symbols);
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

    private void walkPythonTree(ParseTree ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx instanceof Python3Parser.FuncdefContext func) {
            symbols.add(new SymbolExtractor.Symbol(
                    func.name().getText(),
                    SymbolExtractor.SymbolType.FUNCTION,
                    func.getStart().getLine()));
        } else if (ctx instanceof Python3Parser.ClassdefContext cls) {
            symbols.add(new SymbolExtractor.Symbol(
                    cls.name().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    cls.getStart().getLine()));
        } else if (ctx instanceof Python3Parser.DecoratedContext decorated) {
            if (decorated.classdef() != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        decorated.classdef().name().getText(),
                        SymbolExtractor.SymbolType.CLASS,
                        decorated.getStart().getLine()));
            } else if (decorated.funcdef() != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        decorated.funcdef().name().getText(),
                        SymbolExtractor.SymbolType.FUNCTION,
                        decorated.getStart().getLine()));
            } else if (decorated.async_funcdef() != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        decorated.async_funcdef().funcdef().name().getText(),
                        SymbolExtractor.SymbolType.FUNCTION,
                        decorated.getStart().getLine()));
            }
            return;
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            walkPythonTree(ctx.getChild(i), symbols);
        }
    }
}
