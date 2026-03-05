package com.example.filebatchprocessor.exception;

/**
 * 记录级校验异常：可跳过。
 */
public class RecordValidationException extends RuntimeException {
    public RecordValidationException(String message) {
        super(message);
    }
}
