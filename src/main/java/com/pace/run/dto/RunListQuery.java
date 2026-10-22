package com.pace.run.dto;

/** 러닝 목록의 커서와 페이지 크기를 기존 쿼리 문자열로 받습니다. */
public record RunListQuery(String cursor, Integer limit) {
    public RunListQuery {
        limit = limit == null ? 20 : limit;
    }
}
