package com.pace.common.util;

import com.pace.common.exception.BusinessException;
import java.time.ZoneId;

public final class TimeZones {
    private TimeZones() {}

    public static ZoneId require(String value) {
        if (!ZoneId.getAvailableZoneIds().contains(value)) throw BusinessException.invalid("유효한 IANA 시간대를 입력해 주세요.");
        return ZoneId.of(value);
    }
}
