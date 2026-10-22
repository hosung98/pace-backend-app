package com.pace.run.dto;

/** 러닝 상세 조회의 경로 표현 방식을 묶습니다. */
public record RunDetailQuery(String route) {
    public RunDetailQuery {
        route = route == null || route.isEmpty() ? "simplified" : route;
    }
}
