package com.pace.user.dto;

import java.time.Instant;
import java.util.List;

public record MeResponse(String id, String nickname, String handle, String profileImageUrl, String bio,
                         String profileVisibility, List<String> linkedProviders, Instant createdAt,
                         UserCountsResponse counts, Object crew) {}
