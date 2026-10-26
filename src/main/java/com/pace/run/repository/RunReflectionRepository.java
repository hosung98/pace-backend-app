package com.pace.run.repository;

import com.pace.run.entity.RunReflection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunReflectionRepository extends JpaRepository<RunReflection, Long> {
    Optional<RunReflection> findByRunId(long runId);
}
