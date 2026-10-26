package com.pace.crew.repository;

import com.pace.crew.entity.CrewMember;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CrewMemberRepository extends JpaRepository<CrewMember, CrewMember.Key> {
    Optional<CrewMember> findByUserId(long userId);
    Optional<CrewMember> findByCrewIdAndUserId(long crewId, long userId);
    boolean existsByCrewIdAndUserId(long crewId, long userId);
    List<CrewMember> findAllByCrewId(long crewId);

    @Modifying
    @Query("delete from CrewMember m where m.crewId=:crewId and m.userId=:userId")
    int remove(@Param("crewId") long crewId, @Param("userId") long userId);

    @Query(value = """
        SELECT u.public_id AS userId, u.nickname AS nickname,
               u.profile_visibility AS profileVisibility,
               COALESCE(SUM(r.distance_meters), 0) AS distanceMeters,
               (cm.user_id=:viewerId) AS isMe
          FROM crew_members cm
          JOIN users u ON u.id=cm.user_id
          LEFT JOIN runs r ON r.user_id=cm.user_id AND r.deleted_at IS NULL
               AND r.started_at >= GREATEST(:monthStart, cm.joined_at)
               AND r.started_at < :monthEnd
         WHERE cm.crew_id=:crewId
         GROUP BY u.public_id, u.nickname, u.profile_visibility, cm.user_id
         ORDER BY COALESCE(SUM(r.distance_meters), 0) DESC, u.nickname
        """, nativeQuery = true)
    List<CrewGoalContributorRow> monthlyContributors(@Param("crewId") long crewId,
                                                     @Param("viewerId") long viewerId,
                                                     @Param("monthStart") java.time.Instant monthStart,
                                                     @Param("monthEnd") java.time.Instant monthEnd);

    @Query(value = """
        SELECT r.user_id AS userId, r.distance_meters AS distanceMeters, r.ended_at AS endedAt
          FROM crew_members cm JOIN users u ON u.id=cm.user_id AND u.status='active'
          JOIN runs r ON r.user_id=cm.user_id AND r.deleted_at IS NULL
         WHERE cm.crew_id=:crewId
           AND r.started_at >= GREATEST(:monthStart, cm.joined_at)
           AND r.started_at < :monthEnd AND r.ended_at <= :now
           AND (r.created_at < :configuredAt OR r.ended_at >= :configuredAt)
         ORDER BY r.ended_at, r.id
        """, nativeQuery = true)
    List<CrewGoalRunRow> rewardRuns(@Param("crewId") long crewId,
                                    @Param("monthStart") java.time.Instant monthStart,
                                    @Param("monthEnd") java.time.Instant monthEnd,
                                    @Param("configuredAt") java.time.Instant configuredAt,
                                    @Param("now") java.time.Instant now);
}
