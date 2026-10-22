package com.pace.run.repository;

import com.pace.run.entity.Run;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, Long> {
    Optional<Run> findByUserIdAndClientRunId(long userId, String clientRunId);
    Optional<Run> findByUserIdAndPublicId(long userId, String publicId);
    long countByUserIdAndDeletedAtIsNull(long userId);
}
