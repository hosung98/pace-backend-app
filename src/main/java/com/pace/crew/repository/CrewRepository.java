package com.pace.crew.repository;

import com.pace.crew.entity.Crew;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CrewRepository extends JpaRepository<Crew, Long> {
    Optional<Crew> findByPublicId(String publicId);
    Optional<Crew> findByInviteCode(String inviteCode);
    boolean existsByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Crew c where c.publicId=:publicId")
    Optional<Crew> lockByPublicId(@Param("publicId") String publicId);

    @Query(value = """
        SELECT c.id AS crewId, c.public_id AS id, c.name AS name, c.intro AS intro,
               c.sido AS sido, c.sigungu AS sigungu,
               (SELECT count(*) FROM crew_members all_members WHERE all_members.crew_id=c.id) AS memberCount,
               COALESCE(SUM(r.distance_meters), 0) AS distanceMeters,
               EXISTS(SELECT 1 FROM crew_members mine WHERE mine.crew_id=c.id AND mine.user_id=:viewerId) AS isMine
          FROM crews c
          LEFT JOIN crew_members cm ON cm.crew_id=c.id
          LEFT JOIN users member_user ON member_user.id=cm.user_id
          LEFT JOIN runs r ON r.user_id=cm.user_id AND r.deleted_at IS NULL
               AND r.started_at >= GREATEST(:periodStart, cm.joined_at)
               AND (member_user.profile_visibility='public' OR cm.user_id=:viewerId)
         WHERE (:query IS NULL OR lower(c.name) LIKE concat('%', lower(:query), '%')
                           OR lower(c.intro) LIKE concat('%', lower(:query), '%'))
           AND (:sido IS NULL OR c.sido=:sido)
           AND (:sigungu IS NULL OR c.sigungu=:sigungu)
         GROUP BY c.id
         ORDER BY COALESCE(SUM(r.distance_meters), 0) DESC, c.created_at ASC
        """, nativeQuery = true)
    List<CrewListRow> search(@Param("viewerId") long viewerId, @Param("query") String query,
                             @Param("sido") String sido, @Param("sigungu") String sigungu,
                             @Param("periodStart") java.time.Instant periodStart);

    @Query(value = """
        SELECT c.id AS crewId, c.public_id AS id, c.name AS name, c.intro AS intro,
               c.sido AS sido, c.sigungu AS sigungu,
               (SELECT count(*) FROM crew_members all_members WHERE all_members.crew_id=c.id) AS memberCount,
               COALESCE(SUM(r.distance_meters), 0) AS distanceMeters,
               EXISTS(SELECT 1 FROM crew_members mine WHERE mine.crew_id=c.id AND mine.user_id=:viewerId) AS isMine
          FROM crews c
          LEFT JOIN crew_members cm ON cm.crew_id=c.id
          LEFT JOIN users member_user ON member_user.id=cm.user_id
          LEFT JOIN runs r ON r.user_id=cm.user_id AND r.deleted_at IS NULL
               AND r.started_at >= GREATEST(:periodStart, cm.joined_at)
               AND (member_user.profile_visibility='public' OR cm.user_id=:viewerId)
         GROUP BY c.id
         ORDER BY COALESCE(SUM(r.distance_meters), 0) DESC, c.created_at ASC
        """, nativeQuery = true)
    List<CrewListRow> rankings(@Param("viewerId") long viewerId,
                               @Param("periodStart") java.time.Instant periodStart);

    @Query(value = """
        SELECT u.public_id AS userId, u.nickname AS nickname, cm.role AS role,
               COALESCE(SUM(r.distance_meters), 0) AS distanceMeters,
               count(r.id) AS runCount, (cm.user_id=:viewerId) AS isMe
          FROM crew_members cm JOIN users u ON u.id=cm.user_id
          LEFT JOIN runs r ON r.user_id=cm.user_id AND r.deleted_at IS NULL
               AND r.started_at >= GREATEST(:periodStart, cm.joined_at)
         WHERE cm.crew_id=:crewId AND (u.profile_visibility='public' OR cm.user_id=:viewerId)
         GROUP BY cm.user_id, u.public_id, u.nickname, cm.role, cm.joined_at
         ORDER BY COALESCE(SUM(r.distance_meters), 0) DESC, u.nickname
        """, nativeQuery = true)
    List<CrewMemberRow> memberStats(@Param("crewId") long crewId, @Param("viewerId") long viewerId,
                                    @Param("periodStart") java.time.Instant periodStart);

    @Query(value = "SELECT nickname FROM users WHERE id=:userId", nativeQuery = true)
    String nickname(@Param("userId") long userId);

    @Query(value = "SELECT count(*) FROM crew_members WHERE crew_id=:crewId", nativeQuery = true)
    long memberCount(@Param("crewId") long crewId);

    @Query(value = "SELECT count(*) FROM crew_join_requests WHERE crew_id=:crewId", nativeQuery = true)
    long pendingCount(@Param("crewId") long crewId);
}
