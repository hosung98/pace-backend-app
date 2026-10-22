package com.pace.user.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public class UpdateProfileRequest {
    @JsonSetter(nulls = Nulls.FAIL) @Size(min = 2, max = 30)
    private String nickname;
    @Schema(description = "생략 시 유지, null이면 삭제. 영문 소문자·숫자·밑줄·마침표 3~30자")
    @JsonSetter
    private JsonNode handle;
    @Schema(description = "생략 시 유지, null이면 삭제. 최대 150자")
    @JsonSetter
    private JsonNode bio;
    @JsonSetter(nulls = Nulls.FAIL) @Pattern(regexp = "public|private")
    private String profileVisibility;

    public String nickname() { return nickname; }
    public JsonNode handle() { return handle; }
    public JsonNode bio() { return bio; }
    public String profileVisibility() { return profileVisibility; }
}
