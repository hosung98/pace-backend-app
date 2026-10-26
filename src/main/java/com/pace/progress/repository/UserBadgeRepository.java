package com.pace.progress.repository;

import com.pace.progress.entity.UserBadge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadge.Key> {
    List<UserBadge> findAllByUserId(long userId);

    @Modifying
    @Query(value = """
        INSERT INTO user_badges(user_id, badge_id, earned_at) VALUES (:userId, 'crew-goal', :earnedAt)
        ON CONFLICT (user_id, badge_id) DO NOTHING
        """, nativeQuery = true)
    int awardCrewGoal(@Param("userId") long userId, @Param("earnedAt") Instant earnedAt);
}
