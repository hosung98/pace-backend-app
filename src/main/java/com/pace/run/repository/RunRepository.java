package com.pace.run.repository;

import com.pace.run.entity.Run;

import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, Long> {
    Optional<Run> findByUserIdAndClientRunId(long userId, String clientRunId);
    Optional<Run> findByUserIdAndPublicId(long userId, String publicId);
    long countByUserIdAndDeletedAtIsNull(long userId);
    List<Run> findAllByUserIdAndDeletedAtIsNullOrderByStartedAtAscIdAsc(long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Run r where r.userId=:userId and r.publicId=:publicId")
    Optional<Run> lockByUserIdAndPublicId(@Param("userId") long userId, @Param("publicId") String publicId);
}
