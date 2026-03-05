package com.example.filebatchprocessor.exception;

import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;

public final class ErrorCodeClassifier {

    private ErrorCodeClassifier() {
    }

    public static ErrorCode classify(Throwable t) {
        if (t == null) {
            return ErrorCode.INTERNAL_ERROR;
        }
        if (t instanceof BusinessException be && be.getCode() != null) {
            return be.getCode();
        }
        if (t instanceof SystemException se && se.getCode() != null) {
            return se.getCode();
        }
        if (t instanceof RecordValidationException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (t instanceof TransientImportException) {
            return ErrorCode.DB_ERROR;
        }
        if (t instanceof DataAccessException) {
            return ErrorCode.DB_ERROR;
        }
        if (t instanceof SocketTimeoutException) {
            return ErrorCode.NETWORK_ERROR;
        }
        if (t instanceof IOException) {
            return ErrorCode.IO_ERROR;
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}
