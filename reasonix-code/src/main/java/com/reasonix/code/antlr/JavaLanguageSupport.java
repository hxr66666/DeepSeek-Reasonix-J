package com.reasonix.code.antlr;

import com.reasonix.code.AntlrLanguageSupport;
import com.reasonix.code.SymbolExtractor;
import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JavaLanguageSupport implements AntlrLanguageSupport {

    private static final Set<String> EXTENSIONS = Set.of(".java");

    static {
        AntlrLanguageSupport.register(new JavaLanguageSupport());
    }

    @Override
    public Lexer createLexer(CharStream input) {
        return new JavaLexer(input);
    }

    @Override
    public Parser createParser(CommonTokenStream tokens) {
        return new JavaParser(tokens);
    }

    @Override
    public ParserRuleContext parse(Parser parser) {
        return ((JavaParser) parser).compilationUnit();
    }

    @Override
    public List<SymbolExtractor.Symbol> extractSymbols(ParserRuleContext tree) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        JavaParser.CompilationUnitContext ctx = (JavaParser.CompilationUnitContext) tree;
        extractFromCompilationUnit(ctx, symbols);
        return symbols;
    }

    @Override
    public Set<String> supportedExtensions() {
        return EXTENSIONS;
    }

    private void extractFromCompilationUnit(JavaParser.CompilationUnitContext ctx, List<SymbolExtractor.Symbol> symbols) {
        for (var typeDecl : ctx.typeDeclaration()) {
            extractFromTypeDeclaration(typeDecl, symbols);
        }
    }

    private void extractFromTypeDeclaration(JavaParser.TypeDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx.classDeclaration() != null) {
            extractFromClassDeclaration(ctx.classDeclaration(), symbols);
        } else if (ctx.interfaceDeclaration() != null) {
            extractFromInterfaceDeclaration(ctx.interfaceDeclaration(), symbols);
        } else if (ctx.enumDeclaration() != null) {
            extractFromEnumDeclaration(ctx.enumDeclaration(), symbols);
        } else if (ctx.recordDeclaration() != null) {
            extractFromRecordDeclaration(ctx.recordDeclaration(), symbols);
        } else if (ctx.annotationTypeDeclaration() != null) {
            extractFromAnnotationTypeDeclaration(ctx.annotationTypeDeclaration(), symbols);
        }
    }

    private void extractFromClassDeclaration(JavaParser.ClassDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        symbols.add(new SymbolExtractor.Symbol(
                ctx.identifier().getText(),
                SymbolExtractor.SymbolType.CLASS,
                ctx.getStart().getLine()));
        extractFromClassBody(ctx.classBody(), symbols);
    }

    private void extractFromInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        symbols.add(new SymbolExtractor.Symbol(
                ctx.identifier().getText(),
                SymbolExtractor.SymbolType.INTERFACE,
                ctx.getStart().getLine()));
        extractFromInterfaceBody(ctx.interfaceBody(), symbols);
    }

    private void extractFromEnumDeclaration(JavaParser.EnumDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        symbols.add(new SymbolExtractor.Symbol(
                ctx.identifier().getText(),
                SymbolExtractor.SymbolType.ENUM,
                ctx.getStart().getLine()));
        extractFromEnumConstants(ctx.enumConstants(), symbols);
        if (ctx.enumBodyDeclarations() != null) {
            for (var decl : ctx.enumBodyDeclarations().classBodyDeclaration()) {
                if (decl.memberDeclaration() != null) {
                    extractFromMemberDeclaration(decl.memberDeclaration(), symbols);
                }
            }
        }
    }

    private void extractFromEnumConstants(JavaParser.EnumConstantsContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var constant : ctx.enumConstant()) {
            symbols.add(new SymbolExtractor.Symbol(
                    constant.identifier().getText(),
                    SymbolExtractor.SymbolType.ENUM_CONSTANT,
                    constant.getStart().getLine()));
        }
    }

    private void extractFromRecordDeclaration(JavaParser.RecordDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        symbols.add(new SymbolExtractor.Symbol(
                ctx.identifier().getText(),
                SymbolExtractor.SymbolType.CLASS,
                ctx.getStart().getLine()));
        extractFromRecordHeader(ctx.recordHeader(), symbols);
        extractFromRecordBody(ctx.recordBody(), symbols);
    }

    private void extractFromRecordHeader(JavaParser.RecordHeaderContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null || ctx.recordComponentList() == null) return;
        for (var component : ctx.recordComponentList().recordComponent()) {
            symbols.add(new SymbolExtractor.Symbol(
                    component.identifier().getText(),
                    SymbolExtractor.SymbolType.FIELD,
                    component.getStart().getLine()));
        }
    }

    private void extractFromRecordBody(JavaParser.RecordBodyContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var item : ctx.classBodyDeclaration()) {
            if (item.memberDeclaration() != null) {
                extractFromMemberDeclaration(item.memberDeclaration(), symbols);
            }
        }
        for (var compactCtor : ctx.compactConstructorDeclaration()) {
            symbols.add(new SymbolExtractor.Symbol(
                    compactCtor.identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    compactCtor.getStart().getLine()));
        }
    }

    private void extractFromAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        symbols.add(new SymbolExtractor.Symbol(
                ctx.identifier().getText(),
                SymbolExtractor.SymbolType.ANNOTATION,
                ctx.getStart().getLine()));
        extractFromAnnotationTypeBody(ctx.annotationTypeBody(), symbols);
    }

    private void extractFromAnnotationTypeBody(JavaParser.AnnotationTypeBodyContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var elem : ctx.annotationTypeElementDeclaration()) {
            if (elem.annotationTypeElementRest() != null) {
                var rest = elem.annotationTypeElementRest();
                if (rest.annotationMethodOrConstantRest() != null) {
                    var methodOrConst = rest.annotationMethodOrConstantRest();
                    if (methodOrConst.annotationMethodRest() != null) {
                        symbols.add(new SymbolExtractor.Symbol(
                                methodOrConst.annotationMethodRest().identifier().getText(),
                                SymbolExtractor.SymbolType.METHOD,
                                methodOrConst.annotationMethodRest().getStart().getLine()));
                    } else if (methodOrConst.annotationConstantRest() != null) {
                        var constRest = methodOrConst.annotationConstantRest();
                        for (var declarator : constRest.variableDeclarators().variableDeclarator()) {
                            symbols.add(new SymbolExtractor.Symbol(
                                    declarator.variableDeclaratorId().identifier().getText(),
                                    SymbolExtractor.SymbolType.FIELD,
                                    declarator.getStart().getLine()));
                        }
                    }
                } else if (rest.classDeclaration() != null) {
                    extractFromClassDeclaration(rest.classDeclaration(), symbols);
                } else if (rest.interfaceDeclaration() != null) {
                    extractFromInterfaceDeclaration(rest.interfaceDeclaration(), symbols);
                } else if (rest.enumDeclaration() != null) {
                    extractFromEnumDeclaration(rest.enumDeclaration(), symbols);
                } else if (rest.annotationTypeDeclaration() != null) {
                    extractFromAnnotationTypeDeclaration(rest.annotationTypeDeclaration(), symbols);
                } else if (rest.recordDeclaration() != null) {
                    extractFromRecordDeclaration(rest.recordDeclaration(), symbols);
                }
            }
        }
    }

    private void extractFromClassBody(JavaParser.ClassBodyContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var decl : ctx.classBodyDeclaration()) {
            if (decl.memberDeclaration() != null) {
                extractFromMemberDeclaration(decl.memberDeclaration(), symbols);
            }
        }
    }

    private void extractFromInterfaceBody(JavaParser.InterfaceBodyContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx == null) return;
        for (var decl : ctx.interfaceBodyDeclaration()) {
            if (decl.interfaceMemberDeclaration() != null) {
                extractFromInterfaceMemberDeclaration(decl.interfaceMemberDeclaration(), symbols);
            }
        }
    }

    private void extractFromInterfaceMemberDeclaration(JavaParser.InterfaceMemberDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx.interfaceMethodDeclaration() != null) {
            var method = ctx.interfaceMethodDeclaration();
            symbols.add(new SymbolExtractor.Symbol(
                    method.interfaceCommonBodyDeclaration().identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    method.getStart().getLine()));
        } else if (ctx.genericInterfaceMethodDeclaration() != null) {
            var method = ctx.genericInterfaceMethodDeclaration();
            symbols.add(new SymbolExtractor.Symbol(
                    method.interfaceCommonBodyDeclaration().identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    method.getStart().getLine()));
        } else if (ctx.constDeclaration() != null) {
            var constDecl = ctx.constDeclaration();
            for (var declarator : constDecl.constantDeclarator()) {
                symbols.add(new SymbolExtractor.Symbol(
                        declarator.identifier().getText(),
                        SymbolExtractor.SymbolType.FIELD,
                        declarator.getStart().getLine()));
            }
        } else if (ctx.classDeclaration() != null) {
            extractFromClassDeclaration(ctx.classDeclaration(), symbols);
        } else if (ctx.interfaceDeclaration() != null) {
            extractFromInterfaceDeclaration(ctx.interfaceDeclaration(), symbols);
        } else if (ctx.enumDeclaration() != null) {
            extractFromEnumDeclaration(ctx.enumDeclaration(), symbols);
        } else if (ctx.annotationTypeDeclaration() != null) {
            extractFromAnnotationTypeDeclaration(ctx.annotationTypeDeclaration(), symbols);
        } else if (ctx.recordDeclaration() != null) {
            extractFromRecordDeclaration(ctx.recordDeclaration(), symbols);
        }
    }

    private void extractFromMemberDeclaration(JavaParser.MemberDeclarationContext ctx, List<SymbolExtractor.Symbol> symbols) {
        if (ctx.methodDeclaration() != null) {
            var method = ctx.methodDeclaration();
            symbols.add(new SymbolExtractor.Symbol(
                    method.identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    method.getStart().getLine()));
        } else if (ctx.genericMethodDeclaration() != null) {
            var method = ctx.genericMethodDeclaration().methodDeclaration();
            symbols.add(new SymbolExtractor.Symbol(
                    method.identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    method.getStart().getLine()));
        } else if (ctx.fieldDeclaration() != null) {
            var field = ctx.fieldDeclaration();
            for (var declarator : field.variableDeclarators().variableDeclarator()) {
                symbols.add(new SymbolExtractor.Symbol(
                        declarator.variableDeclaratorId().identifier().getText(),
                        SymbolExtractor.SymbolType.FIELD,
                        declarator.getStart().getLine()));
            }
        } else if (ctx.constructorDeclaration() != null) {
            var ctor = ctx.constructorDeclaration();
            symbols.add(new SymbolExtractor.Symbol(
                    ctor.identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    ctor.getStart().getLine()));
        } else if (ctx.genericConstructorDeclaration() != null) {
            var ctor = ctx.genericConstructorDeclaration().constructorDeclaration();
            symbols.add(new SymbolExtractor.Symbol(
                    ctor.identifier().getText(),
                    SymbolExtractor.SymbolType.METHOD,
                    ctor.getStart().getLine()));
        } else if (ctx.classDeclaration() != null) {
            extractFromClassDeclaration(ctx.classDeclaration(), symbols);
        } else if (ctx.interfaceDeclaration() != null) {
            extractFromInterfaceDeclaration(ctx.interfaceDeclaration(), symbols);
        } else if (ctx.enumDeclaration() != null) {
            extractFromEnumDeclaration(ctx.enumDeclaration(), symbols);
        } else if (ctx.annotationTypeDeclaration() != null) {
            extractFromAnnotationTypeDeclaration(ctx.annotationTypeDeclaration(), symbols);
        } else if (ctx.recordDeclaration() != null) {
            extractFromRecordDeclaration(ctx.recordDeclaration(), symbols);
        }
    }
}
