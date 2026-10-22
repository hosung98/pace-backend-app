package com.pace.run.vo;

import com.pace.common.exception.BusinessException;

public record IdempotencyKey(String value) {
    private static final int MAX_LENGTH = 128;

    public static IdempotencyKey fromNullable(String raw) {
        if (raw == null) return null;
        if (raw.isBlank() || raw.length() > MAX_LENGTH) {
            throw BusinessException.invalid("Idempotency-Key는 1~128자여야 합니다.");
        }
        return new IdempotencyKey(raw);
    }
}
