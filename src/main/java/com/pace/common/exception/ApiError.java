package com.pace.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/** MVC와 보안 필터가 함께 사용하는 공통 오류 응답입니다. */
public record ApiError(String code, String message, String requestId, List<FieldError> fieldErrors) {
    /** 내부 예외 정보 없이 요청 추적 ID를 포함한 오류를 만듭니다. */
    public static ApiError of(String code, String message, HttpServletRequest request) {
        return new ApiError(code, message, String.valueOf(request.getAttribute("requestId")), List.of());
    }

    /** 입력 필드별 검증 실패 사유를 표현합니다. */
    public record FieldError(String field, String reason) {}
}
