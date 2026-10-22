package com.pace.race.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record RaceSummaryResponse(String id, String title, String hostNickname, int distanceMeters, Instant startsAt,
                                  int joinDeadlineMinutes, int minParticipants, int maxParticipants,
                                  long participantCount, String visibility,
                                  @JsonProperty("isJoined") boolean isJoined,
                                  @JsonProperty("isHost") boolean isHost,
                                  boolean cancelled) {}
