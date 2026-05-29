package com.reasonix.code.antlr.golang;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GoLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".go");

    static {
        AntlrLanguageSupport.register(new GoLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new GoLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new GoParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((GoParser) parser).sourceFile();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        walkGoTree(tree, symbols);
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

    private void walkGoTree(ParseTree ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx instanceof GoParser.FunctionDeclContext fn) {
            symbols.add(new SymbolExtractor.Symbol(
                    fn.IDENTIFIER().getText(),
                    SymbolExtractor.SymbolType.FUNCTION,
                    fn.getStart().getLine()));
        } else if (ctx instanceof GoParser.MethodDeclContext method) {
            symbols.add(new SymbolExtractor.Symbol(
                    method.IDENTIFIER().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    method.getStart().getLine()));
        } else if (ctx instanceof GoParser.TypeSpecContext typeSpec) {
            if (typeSpec.aliasDecl() != null) {
                var alias = typeSpec.aliasDecl();
                symbols.add(new SymbolExtractor.Symbol(
                        alias.IDENTIFIER().getText(),
                        SymbolExtractor.SymbolType.CLASS,
                        alias.getStart().getLine()));
            } else if (typeSpec.typeDef() != null) {
                var typeDef = typeSpec.typeDef();
                String name = typeDef.IDENTIFIER().getText();
                var type = typeDef.type_();
                SymbolExtractor.SymbolType symbolType = SymbolExtractor.SymbolType.CLASS;
                if (type != null && hasChildOfType(type, GoParser.InterfaceTypeContext.class)) {
                    symbolType = SymbolExtractor.SymbolType.INTERFACE;
                }
                symbols.add(new SymbolExtractor.Symbol(name, symbolType, typeDef.getStart().getLine()));
            }
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            walkGoTree(ctx.getChild(i), symbols);
        }
    }

    private boolean hasChildOfType(ParseTree ctx, Class<?> targetClass) {
        if (targetClass.isInstance(ctx)) return true;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (hasChildOfType(ctx.getChild(i), targetClass)) return true;
        }
        return false;
    }
}
