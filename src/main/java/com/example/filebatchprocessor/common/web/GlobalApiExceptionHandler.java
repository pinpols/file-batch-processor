package com.example.filebatchprocessor.common.web;

import com.example.filebatchprocessor.exception.BusinessException;
import com.example.filebatchprocessor.exception.ErrorCode;
import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.exception.SystemException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常 → HTTP 契约。
 *
 * <p>错误码自带 HTTP 语义({@link ErrorCode#getHttpStatus()}),advice 集中映射为统一响应体
 * {@link ApiError}。4xx 用 {@code warn}(不打全栈),5xx 兜底用 {@code error}(打全栈)。
 *
 * <p>认证/授权异常(AccessDeniedException、AuthenticationException)刻意不在此处理,交给
 * Spring Security 的 401/403 链。
 */
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        HttpStatus status = ex.getCode().getHttpStatus();
        logByStatus(status, ex);
        return ResponseEntity.status(status).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiError> handleSystem(SystemException ex) {
        HttpStatus status = ex.getCode().getHttpStatus();
        logByStatus(status, ex);
        return ResponseEntity.status(status).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(RecordValidationException.class)
    public ResponseEntity<ApiError> handleRecordValidation(RecordValidationException ex) {
        log.warn("record validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ErrorCode.INVALID_ARGUMENT, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        log.warn("illegal state (conflict): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("method argument not valid: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ARGUMENT", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() == null ? "?" : ex.getRequiredType().getSimpleName();
        String message = "parameter '" + ex.getName() + "' should be of type " + requiredType;
        log.warn("argument type mismatch: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ARGUMENT", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ARGUMENT", "malformed request body"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("method not allowed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("optimistic locking failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", ex.getMessage()));
    }

    // 更具体:必须先于 DataIntegrityViolationException 声明(DuplicateKeyException 是其子类)。
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("duplicate key: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", ex.getMessage()));
    }

    // 更泛:不回显底层 SQL 细节,避免泄漏 schema。
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("CONFLICT", "data integrity violation"));
    }

    // 兜底:不回显原始 message(脱敏),但记录全栈。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnhandled(Exception ex) {
        log.error("unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, "internal error"));
    }

    private void logByStatus(HttpStatus status, Exception ex) {
        if (status.is5xxServerError()) {
            log.error("server error ({}): {}", status.value(), ex.getMessage(), ex);
        } else {
            log.warn("client error ({}): {}", status.value(), ex.getMessage());
        }
    }
}
