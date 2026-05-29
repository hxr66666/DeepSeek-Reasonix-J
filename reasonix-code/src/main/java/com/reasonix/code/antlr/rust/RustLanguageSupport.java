package com.reasonix.code.antlr.rust;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RustLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".rs");

    static {
        AntlrLanguageSupport.register(new RustLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new RustLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new RustParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((RustParser) parser).crate();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        walkRustTree(tree, symbols);
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

    private void walkRustTree(ParseTree ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx instanceof RustParser.Function_Context fn) {
            symbols.add(new SymbolExtractor.Symbol(
                    fn.identifier().getText(),
                    SymbolExtractor.SymbolType.FUNCTION,
                    fn.getStart().getLine()));
        } else if (ctx instanceof RustParser.StructStructContext struct_) {
            symbols.add(new SymbolExtractor.Symbol(
                    struct_.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    struct_.getStart().getLine()));
            extractStructFields(struct_.structFields(), symbols);
        } else if (ctx instanceof RustParser.TupleStructContext tupleStruct) {
            symbols.add(new SymbolExtractor.Symbol(
                    tupleStruct.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    tupleStruct.getStart().getLine()));
        } else if (ctx instanceof RustParser.EnumerationContext en) {
            symbols.add(new SymbolExtractor.Symbol(
                    en.identifier().getText(),
                    SymbolExtractor.SymbolType.ENUM,
                    en.getStart().getLine()));
            extractEnumItems(en.enumItems(), symbols);
        } else if (ctx instanceof RustParser.Trait_Context trait) {
            symbols.add(new SymbolExtractor.Symbol(
                    trait.identifier().getText(),
                    SymbolExtractor.SymbolType.INTERFACE,
                    trait.getStart().getLine()));
        } else if (ctx instanceof RustParser.TypeAliasContext ta) {
            symbols.add(new SymbolExtractor.Symbol(
                    ta.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    ta.getStart().getLine()));
        } else if (ctx instanceof RustParser.ModuleContext mod) {
            symbols.add(new SymbolExtractor.Symbol(
                    mod.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    mod.getStart().getLine()));
        } else if (ctx instanceof RustParser.ConstantItemContext con) {
            var id = con.identifier();
            if (id != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        id.getText(),
                        SymbolExtractor.SymbolType.FIELD,
                        con.getStart().getLine()));
            }
        } else if (ctx instanceof RustParser.StaticItemContext stat) {
            symbols.add(new SymbolExtractor.Symbol(
                    stat.identifier().getText(),
                    SymbolExtractor.SymbolType.FIELD,
                    stat.getStart().getLine()));
        } else if (ctx instanceof RustParser.Union_Context union) {
            symbols.add(new SymbolExtractor.Symbol(
                    union.identifier().getText(),
                    SymbolExtractor.SymbolType.CLASS,
                    union.getStart().getLine()));
        }

        for (int i = 0; i < ctx.getChildCount(); i++) {
            walkRustTree(ctx.getChild(i), symbols);
        }
    }

    private void extractStructFields(RustParser.StructFieldsContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var field : ctx.structField()) {
            var id = field.identifier();
            if (id != null) {
                symbols.add(new SymbolExtractor.Symbol(
                        id.getText(),
                        SymbolExtractor.SymbolType.FIELD,
                        field.getStart().getLine()));
            }
        }
    }

    private void extractEnumItems(RustParser.EnumItemsContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var item : ctx.enumItem()) {
            symbols.add(new SymbolExtractor.Symbol(
                    item.identifier().getText(),
                    SymbolExtractor.SymbolType.ENUM_CONSTANT,
                    item.getStart().getLine()));
        }
    }
}
