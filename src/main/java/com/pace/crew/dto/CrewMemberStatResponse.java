package com.pace.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewMemberStatResponse(String userId, String nickname, String role, long distanceMeters,
                                     long runCount, @JsonProperty("isMe") boolean isMe) {}
