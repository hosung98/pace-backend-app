package com.pace.run.service.impl;

import com.pace.common.exception.BusinessException;
import com.pace.run.dto.RunReflectionRequest;
import com.pace.run.dto.RunReflectionResponse;
import com.pace.run.entity.Run;
import com.pace.run.entity.RunReflection;
import com.pace.run.repository.RunReflectionRepository;
import com.pace.run.repository.RunRepository;
import com.pace.run.service.RunReflectionService;
import com.pace.user.service.UserAccess;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunReflectionServiceImpl implements RunReflectionService {
    private final RunRepository runs;
    private final RunReflectionRepository reflections;
    private final UserAccess access;

    public RunReflectionServiceImpl(RunRepository runs, RunReflectionRepository reflections, UserAccess access) {
        this.runs = runs;
        this.reflections = reflections;
        this.access = access;
    }

    @Override
    @Transactional(readOnly = true)
    public RunReflectionResponse get(String subject, String publicId) {
        Run run = ownedRun(subject, publicId, false);
        return reflections.findByRunId(run.getId())
                .map(value -> new RunReflectionResponse(value.getEffort(), value.getNote()))
                .orElseGet(() -> new RunReflectionResponse(null, ""));
    }

    @Override
    @Transactional
    public RunReflectionResponse update(String subject, String publicId, RunReflectionRequest request) {
        Run run = ownedRun(subject, publicId, true);
        RunReflection reflection = reflections.findById(run.getId())
                .orElseGet(() -> new RunReflection(run.getId(), request.effort(), request.note()));
        reflection.update(request.effort(), request.note());
        reflections.save(reflection);
        return new RunReflectionResponse(reflection.getEffort(), reflection.getNote());
    }

    private Run ownedRun(String subject, String publicId, boolean lock) {
        long userId = access.id(subject, lock);
        Run run = (lock ? lockedRun(userId, publicId) : runs.findByUserIdAndPublicId(userId, publicId))
                .orElseThrow(BusinessException::missing);
        if (run.getDeletedAt() != null) throw BusinessException.missing();
        return run;
    }

    private Optional<Run> lockedRun(long userId, String publicId) {
        return runs.lockByUserIdAndPublicId(userId, publicId);
    }
}
