package com.example.filebatchprocessor.exception;

public class SystemException extends RuntimeException {

    private final ErrorCode code;

    public SystemException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public SystemException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
