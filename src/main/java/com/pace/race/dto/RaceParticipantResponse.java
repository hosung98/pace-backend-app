package com.pace.race.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RaceParticipantResponse(String userId, String nickname,
                                      @JsonProperty("isHost") boolean isHost,
                                      @JsonProperty("isMe") boolean isMe, Object result) {}
