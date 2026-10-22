package com.pace.run.dto;

import java.util.List;

public record RunListResponse(List<RunSummaryResponse> items, String nextCursor) {}
