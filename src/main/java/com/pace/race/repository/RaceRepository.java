package com.pace.race.repository;

import com.pace.race.entity.Race;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RaceRepository extends JpaRepository<Race, Long> {
    Optional<Race> findByPublicId(String publicId);
    Optional<Race> findByInviteCode(String inviteCode);
    List<Race> findAllByVisibilityAndCancelledFalseOrderByStartsAtAsc(String visibility);
    List<Race> findAllByHostUserIdOrderByStartsAtAsc(long hostUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Race r where r.publicId=:publicId")
    Optional<Race> lockByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Race r where r.id=:id")
    Optional<Race> lockById(@Param("id") long id);

    @Query(value = """
        SELECT * FROM races
         WHERE deadline_processed_at IS NULL
           AND starts_at - make_interval(mins => join_deadline_minutes) <= :now
         ORDER BY starts_at
        """, nativeQuery = true)
    List<Race> dueForDeadlineProcessing(@Param("now") Instant now);

    @Query(value = "SELECT nickname FROM users WHERE id=:hostId", nativeQuery = true)
    String hostNickname(@Param("hostId") long hostId);
}
