package com.reasonix.code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexSymbolExtractor {

    private static final Logger log = LoggerFactory.getLogger(RegexSymbolExtractor.class);

    private static final Pattern JAVA_CLASS = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:abstract|final|static)?\\s*(?:class|interface|enum|record)\\s+(\\w+)"
    );
    private static final Pattern JAVA_METHOD = Pattern.compile(
            "(?:public|private|protected)?\\s*(?:static|abstract|final|synchronized)?\\s*\\S+\\s+(\\w+)\\s*\\("
    );
    private static final Pattern PYTHON_DEF = Pattern.compile(
            "(?:async\\s+)?def\\s+(\\w+)\\s*\\("
    );
    private static final Pattern PYTHON_CLASS = Pattern.compile(
            "class\\s+(\\w+)"
    );
    private static final Pattern JS_FUNC = Pattern.compile(
            "(?:async\\s+)?(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s+)?\\()"
    );
    private static final Pattern TS_INTERFACE = Pattern.compile(
            "(?:export\\s+)?interface\\s+(\\w+)"
    );
    private static final Pattern GO_FUNC = Pattern.compile(
            "func\\s+(?:\\([^)]+\\)\\s*)?(\\w+)\\s*\\("
    );
    private static final Pattern RUST_FN = Pattern.compile(
            "(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)\\s*[<(]"
    );
    private static final Pattern RUST_STRUCT = Pattern.compile(
            "(?:pub\\s+)?struct\\s+(\\w+)"
    );

    public List<SymbolExtractor.Symbol> extractSymbols(Path filePath) {
        try {
            String content = Files.readString(filePath);
            String name = filePath.getFileName().toString();
            return extractSymbols(content, name);
        } catch (IOException e) {
            log.debug("Failed to read file for symbol extraction: {}", filePath);
            return List.of();
        }
    }

    public List<SymbolExtractor.Symbol> extractSymbols(String content, String fileName) {
        List<SymbolExtractor.Symbol> symbols = new ArrayList<>();
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";

        switch (ext) {
            case ".java" -> extractJavaSymbols(content, symbols);
            case ".py" -> extractPythonSymbols(content, symbols);
            case ".js", ".ts", ".tsx", ".jsx" -> extractJsTsSymbols(content, symbols);
            case ".go" -> extractGoSymbols(content, symbols);
            case ".rs" -> extractRustSymbols(content, symbols);
            default -> extractGenericSymbols(content, symbols);
        }

        return symbols;
    }

    private void extractJavaSymbols(String content, List<SymbolExtractor.Symbol> symbols) {
        extractMatches(JAVA_CLASS, content, SymbolExtractor.SymbolType.CLASS, symbols);
        extractMatches(JAVA_METHOD, content, SymbolExtractor.SymbolType.METHOD, symbols);
    }

    private void extractPythonSymbols(String content, List<SymbolExtractor.Symbol> symbols) {
        extractMatches(PYTHON_CLASS, content, SymbolExtractor.SymbolType.CLASS, symbols);
        extractMatches(PYTHON_DEF, content, SymbolExtractor.SymbolType.FUNCTION, symbols);
    }

    private void extractJsTsSymbols(String content, List<SymbolExtractor.Symbol> symbols) {
        extractMatches(TS_INTERFACE, content, SymbolExtractor.SymbolType.INTERFACE, symbols);
        extractMatches(JS_FUNC, content, SymbolExtractor.SymbolType.FUNCTION, symbols);
    }

    private void extractGoSymbols(String content, List<SymbolExtractor.Symbol> symbols) {
        extractMatches(GO_FUNC, content, SymbolExtractor.SymbolType.FUNCTION, symbols);
    }

    private void extractRustSymbols(String content, List<SymbolExtractor.Symbol> symbols) {
        extractMatches(RUST_STRUCT, content, SymbolExtractor.SymbolType.CLASS, symbols);
        extractMatches(RUST_FN, content, SymbolExtractor.SymbolType.FUNCTION, symbols);
    }

    private void extractGenericSymbols(String content, List<SymbolExtractor.Symbol> symbols) {
        extractMatches(JAVA_CLASS, content, SymbolExtractor.SymbolType.CLASS, symbols);
        extractMatches(PYTHON_DEF, content, SymbolExtractor.SymbolType.FUNCTION, symbols);
    }

    private void extractMatches(Pattern pattern, String content, SymbolExtractor.SymbolType type,
                                List<SymbolExtractor.Symbol> symbols) {
        Matcher matcher = pattern.matcher(content);
        int lineNum = 1;
        int lastEnd = 0;

        while (matcher.find()) {
            for (int i = lastEnd; i < matcher.start(); i++) {
                if (content.charAt(i) == '\n') lineNum++;
            }
            lastEnd = matcher.start();

            String name = null;
            for (int g = 1; g <= matcher.groupCount(); g++) {
                if (matcher.group(g) != null) {
                    name = matcher.group(g);
                    break;
                }
            }
            if (name != null) {
                symbols.add(new SymbolExtractor.Symbol(name, type, lineNum));
            }
        }
    }
}
