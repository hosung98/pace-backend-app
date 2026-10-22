package com.pace.crew.dto;

import jakarta.validation.constraints.NotBlank;

/** 크루장 권한을 넘길 대상 사용자를 지정합니다. */
public record LeaderTransferRequest(@NotBlank String userId) {}
