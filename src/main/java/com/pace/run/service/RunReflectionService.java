package com.pace.run.service;

import com.pace.run.dto.RunReflectionRequest;
import com.pace.run.dto.RunReflectionResponse;

public interface RunReflectionService {
    RunReflectionResponse get(String subject, String runId);
    RunReflectionResponse update(String subject, String runId, RunReflectionRequest request);
}
