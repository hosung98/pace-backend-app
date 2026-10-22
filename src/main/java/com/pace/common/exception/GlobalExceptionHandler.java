package com.pace.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static Map<String, Object> body(String code, String message, HttpServletRequest request) {
        return Map.of("code", code, "message", message,
                "requestId", String.valueOf(request.getAttribute("requestId")), "fieldErrors", List.of());
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status).body(body(ex.code, ex.getMessage(), request));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<?> validation(BindException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "VALIDATION_ERROR", "message", "요청 값을 확인해 주세요.",
                "requestId", String.valueOf(request.getAttribute("requestId")),
                "fieldErrors", ex.getFieldErrors().stream().map(error -> Map.of(
                        "field", error.getField(), "reason", String.valueOf(error.getDefaultMessage()))).toList()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "요청 형식 또는 값이 올바르지 않습니다.", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("DB 제약 조건으로 요청이 거부되었습니다. requestId={}", request.getAttribute("requestId"));
        return ResponseEntity.status(409).body(body("CONFLICT", "이미 사용 중이거나 충돌하는 데이터입니다.", request));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<?> missing(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(body("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.", request));
    }

    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class,
            ResponseStatusException.class})
    ResponseEntity<?> status(Exception ex, HttpServletRequest request) {
        int status = ex instanceof org.springframework.web.ErrorResponse error ? error.getStatusCode().value() : 400;
        return ResponseEntity.status(status).body(body("REQUEST_ERROR", "지원하지 않는 요청입니다.", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex, HttpServletRequest request) {
        log.error("처리하지 못한 서버 오류입니다. requestId={}", request.getAttribute("requestId"), ex);
        return ResponseEntity.internalServerError()
                .body(body("INTERNAL_ERROR", "서버 오류가 발생했습니다.", request));
    }
}
