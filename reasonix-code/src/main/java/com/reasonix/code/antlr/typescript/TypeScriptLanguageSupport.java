package com.reasonix.code.antlr.typescript;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TypeScriptLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".ts", ".tsx");

    static {
        AntlrLanguageSupport.register(new TypeScriptLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new TypeScriptLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new TypeScriptParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((TypeScriptParser) parser).program();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        walkTSTree(tree, symbols);
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

    private void walkTSTree(ParseTree ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx instanceof TypeScriptParser.ClassDeclarationContext cls) {
            symbols.add(new SymbolExtractor.Symbol(
                    cls.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    cls.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.InterfaceDeclarationContext iface) {
            symbols.add(new SymbolExtractor.Symbol(
                    iface.identifier().getText(),
                    SymbolExtractor.SymbolType.INTERFACE,
                    iface.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.EnumDeclarationContext en) {
            symbols.add(new SymbolExtractor.Symbol(
                    en.identifier().getText(),
                    SymbolExtractor.SymbolType.ENUM,
                    en.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.FunctionDeclarationContext fn) {
            symbols.add(new SymbolExtractor.Symbol(
                    fn.identifier().getText(),
                    SymbolExtractor.SymbolType.FUNCTION,
                    fn.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.TypeAliasDeclarationContext ta) {
            symbols.add(new SymbolExtractor.Symbol(
                    ta.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    ta.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.NamespaceDeclarationContext ns) {
            symbols.add(new SymbolExtractor.Symbol(
                    ns.namespaceName().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    ns.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.MethodDeclarationExpressionContext method) {
            symbols.add(new SymbolExtractor.Symbol(
                    method.propertyName().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    method.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.PropertyDeclarationExpressionContext prop) {
            symbols.add(new SymbolExtractor.Symbol(
                    prop.propertyName().getText(),
                    SymbolExtractor.SymbolType.FIELD,
                    prop.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.ConstructorDeclarationContext ctor) {
            symbols.add(new SymbolExtractor.Symbol(
                    "constructor",
                    SymbolExtractor.SymbolType.METHOD,
                    ctor.getStart().getLine()));
        } else if (ctx instanceof TypeScriptParser.GetterSetterDeclarationExpressionContext gs) {
            String name = "?";
            if (gs.getAccessor() != null) {
                var getter = gs.getAccessor().getter();
                if (getter != null && getter.classElementName() != null) {
                    var pn = getter.classElementName().propertyName();
                    if (pn != null) name = pn.getText();
                }
            } else if (gs.setAccessor() != null) {
                var setter = gs.setAccessor().setter();
                if (setter != null && setter.classElementName() != null) {
                    var pn = setter.classElementName().propertyName();
                    if (pn != null) name = pn.getText();
                }
            }
            symbols.add(new SymbolExtractor.Symbol(
                    name,
                    SymbolExtractor.SymbolType.METHOD,
                    gs.getStart().getLine()));
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            walkTSTree(ctx.getChild(i), symbols);
        }
    }
}
