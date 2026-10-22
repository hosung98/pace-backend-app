package com.pace.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.pace.auth.security.SecurityErrorHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

/** MVC의 비즈니스·검증·보안 예외를 공통 오류 응답으로 변환합니다. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final SecurityErrorHandler securityErrors;

    public GlobalExceptionHandler(SecurityErrorHandler securityErrors) {
        this.securityErrors = securityErrors;
    }

    /** 서비스에서 발생한 인증 오류도 필터와 같은 형식으로 처리합니다. */
    @ExceptionHandler(AuthenticationException.class)
    void authentication(AuthenticationException ex, HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        securityErrors.commence(request, response, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    void denied(AccessDeniedException ex, HttpServletRequest request,
                HttpServletResponse response) throws IOException {
        securityErrors.handle(request, response, ex);
    }

    private static ApiError body(String code, String message, HttpServletRequest request) {
        return ApiError.of(code, message, request);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException ex, HttpServletRequest request) {
        var response = ResponseEntity.status(ex.status);
        if (ex.status.value() == 401) response.header("WWW-Authenticate", "Bearer");
        return response.body(body(ex.code, ex.getMessage(), request));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<?> validation(BindException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "요청 값을 확인해 주세요.",
                String.valueOf(request.getAttribute("requestId")),
                ex.getFieldErrors().stream().map(error -> new ApiError.FieldError(
                        error.getField(), String.valueOf(error.getDefaultMessage()))).toList()));
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
