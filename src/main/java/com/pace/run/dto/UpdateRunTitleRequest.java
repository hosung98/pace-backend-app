package com.pace.run.dto;

import jakarta.validation.constraints.Size;

public record UpdateRunTitleRequest(@Size(max = 60) String title) {}
