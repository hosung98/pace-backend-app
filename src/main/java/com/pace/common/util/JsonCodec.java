package com.pace.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {
    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("응용 프로그램 데이터를 JSON으로 변환할 수 없습니다.", ex);
        }
    }

    public JsonNode read(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("저장된 JSON 형식이 올바르지 않습니다.", ex);
        }
    }
}
