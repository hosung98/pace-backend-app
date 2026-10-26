package com.pace.progress.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "user_badges")
@IdClass(UserBadge.Key.class)
public class UserBadge {
    @Id
    private Long userId;
    @Id
    @Column(length = 40)
    private String badgeId;
    @Column(nullable = false)
    private Instant earnedAt;

    protected UserBadge() {}

    public UserBadge(long userId, String badgeId, Instant earnedAt) {
        this.userId = userId;
        this.badgeId = badgeId;
        this.earnedAt = earnedAt;
    }

    public Long getUserId() { return userId; }
    public String getBadgeId() { return badgeId; }
    public Instant getEarnedAt() { return earnedAt; }

    public static class Key implements Serializable {
        private Long userId;
        private String badgeId;
        public Key() {}
        public Key(Long userId, String badgeId) { this.userId = userId; this.badgeId = badgeId; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && java.util.Objects.equals(userId, key.userId)
                    && java.util.Objects.equals(badgeId, key.badgeId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(userId, badgeId); }
    }
}
