package com.reasonix.common.exception;

public class ReasonixException extends RuntimeException {

    private final String code;

    public ReasonixException(String message) {
        super(message);
        this.code = "GENERAL";
    }

    public ReasonixException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ReasonixException(String message, Throwable cause) {
        super(message, cause);
        this.code = "GENERAL";
    }

    public ReasonixException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ReasonixException config(String message) {
        return new ReasonixException("CONFIG", message);
    }

    public static ReasonixException tool(String toolName, String message) {
        return new ReasonixException("TOOL", "Tool '" + toolName + "': " + message);
    }

    public static ReasonixException model(String message) {
        return new ReasonixException("MODEL", message);
    }

    public static ReasonixException context(String message) {
        return new ReasonixException("CONTEXT", message);
    }

    public static ReasonixException memory(String message) {
        return new ReasonixException("MEMORY", message);
    }

    public static ReasonixException io(String message, Throwable cause) {
        return new ReasonixException("IO", message, cause);
    }
}
