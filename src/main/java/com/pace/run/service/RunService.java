package com.pace.run.service;

import com.pace.run.dto.*;

public interface RunService {
    CreateRunResult create(String subject, CreateRunRequest request, String key);
    RunListResponse list(String subject, String cursor, int limit);
    RunDetailResponse detail(String subject, String publicId, String mode);
    RunDetailResponse title(String subject, String publicId, String title);
    void delete(String subject, String publicId);
    RunDetailResponse restore(String subject, String publicId);
}
