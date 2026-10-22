package com.pace.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewSearchResponse(String id, String name, String intro, CrewRegion region, long memberCount,
                                int maxMembers, long weekDistanceMeters,
                                @JsonProperty("isMine") boolean isMine) {}
