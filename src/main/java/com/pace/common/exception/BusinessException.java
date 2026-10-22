package com.pace.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    public final HttpStatus status;
    public final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.");
    }

    public static BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
