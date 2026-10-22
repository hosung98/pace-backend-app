package com.pace.crew.dto;

/** 크루 검색어와 지역 조건을 기존 파라미터 이름 그대로 받습니다. */
public record CrewSearchQuery(String query, String sido, String sigungu) {}
