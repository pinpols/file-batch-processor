package com.example.filebatchprocessor.common.web;

import com.example.filebatchprocessor.exception.ErrorCode;

/** 统一错误响应体(单体单进程,无 traceId/i18n)。 */
public record ApiError(String code, String message) {
    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message);
    }
}
