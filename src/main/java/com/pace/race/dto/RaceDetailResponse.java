package com.pace.race.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record RaceDetailResponse(String id, String title, String hostNickname, int distanceMeters, Instant startsAt,
                                 int joinDeadlineMinutes, int minParticipants, int maxParticipants,
                                 long participantCount, String visibility,
                                 @JsonProperty("isJoined") boolean isJoined,
                                 @JsonProperty("isHost") boolean isHost,
                                 boolean cancelled, List<RaceParticipantResponse> participants,
                                 String inviteCode) {}
