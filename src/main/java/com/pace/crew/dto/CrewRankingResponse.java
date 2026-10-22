package com.pace.crew.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CrewRankingResponse(String id, String name, CrewRegion region, int rank, long memberCount,
                                  long distanceMeters, @JsonProperty("isMine") boolean isMine) {}
