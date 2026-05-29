package com.reasonix.code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SymbolExtractor {

    private static final Logger log = LoggerFactory.getLogger(SymbolExtractor.class);

    static {
        ensureLanguageSupportRegistered();
    }

    private static volatile boolean registered = false;

    private static void ensureLanguageSupportRegistered() {
        if (registered) return;
        synchronized (SymbolExtractor.class) {
            if (registered) return;
            try {
                Class.forName("com.reasonix.code.antlr.JavaLanguageSupport");
                Class.forName("com.reasonix.code.antlr.python.PythonLanguageSupport");
                Class.forName("com.reasonix.code.antlr.javascript.JavaScriptLanguageSupport");
                Class.forName("com.reasonix.code.antlr.typescript.TypeScriptLanguageSupport");
                Class.forName("com.reasonix.code.antlr.golang.GoLanguageSupport");
                Class.forName("com.reasonix.code.antlr.rust.RustLanguageSupport");
                Class.forName("com.reasonix.code.antlr.kotlin.KotlinLanguageSupport");
                Class.forName("com.reasonix.code.antlr.sql.SqlLanguageSupport");
                Class.forName("com.reasonix.code.antlr.css.CssLanguageSupport");
                Class.forName("com.reasonix.code.antlr.html.HtmlLanguageSupport");
                Class.forName("com.reasonix.code.antlr.xml.XmlLanguageSupport");
                Class.forName("com.reasonix.code.antlr.php.PhpLanguageSupport");
                Class.forName("com.reasonix.code.antlr.csharp.CSharpLanguageSupport");
                Class.forName("com.reasonix.code.antlr.lua.LuaLanguageSupport");
                Class.forName("com.reasonix.code.antlr.scala.ScalaLanguageSupport");
                Class.forName("com.reasonix.code.antlr.json.JsonLanguageSupport");
                Class.forName("com.reasonix.code.antlr.toml.TomlLanguageSupport");
                Class.forName("com.reasonix.code.antlr.scss.ScssLanguageSupport");
            } catch (ClassNotFoundException e) {
                log.debug("Some ANTLR language support classes not found: {}", e.getMessage());
            }
            registered = true;
        }
    }

    private final AntlrSymbolExtractor antlrExtractor = new AntlrSymbolExtractor();
    private final RegexSymbolExtractor regexExtractor = new RegexSymbolExtractor();

    public List<Symbol> extractSymbols(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String name = filePath.getFileName().toString();
            return extractSymbols(content, name);
        } catch (IOException e) {
            log.debug("Failed to read file for symbol extraction: {}", filePath);
            return List.of();
        }
    }

    public List<Symbol> extractSymbols(String content, String fileName) {
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";

        if (AntlrLanguageSupport.forExtension(ext) != null) {
            return antlrExtractor.extractSymbols(content, fileName);
        }

        return regexExtractor.extractSymbols(content, fileName);
    }

    public enum SymbolType { CLASS, INTERFACE, METHOD, FUNCTION, VARIABLE, ENUM, FIELD, ANNOTATION, ENUM_CONSTANT }

    public record Symbol(String name, SymbolType type, int lineNumber) {}
}
