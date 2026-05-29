package com.reasonix.code.antlr.sql;
/** SQL modes that control parsing behavior. */
public enum SqlMode {
    NoMode,
    AnsiQuotes,
    HighNotPrecedence,
    PipesAsConcat,
    IgnoreSpace,
    NoBackslashEscapes
}
