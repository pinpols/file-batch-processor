package com.example.filebatchprocessor.exception;

/**
 * 可重试的导入异常（网络抖动/瞬时数据库压力等）。
 */
public class TransientImportException extends RuntimeException {
    public TransientImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
