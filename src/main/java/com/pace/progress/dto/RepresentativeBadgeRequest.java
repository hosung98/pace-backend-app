package com.pace.progress.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record RepresentativeBadgeRequest(@JsonProperty(value = "badgeId", required = true)
                                         @Size(max = 40) String badgeId) {}
