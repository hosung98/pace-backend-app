package com.pace.race.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "race_participants")
@IdClass(RaceParticipant.Key.class)
public class RaceParticipant {
    @Id private Long raceId;
    @Id private Long userId;
    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    protected RaceParticipant() {}
    public RaceParticipant(long raceId, long userId) { this.raceId = raceId; this.userId = userId; }
    public Long getRaceId() { return raceId; }
    public Long getUserId() { return userId; }
    public Instant getJoinedAt() { return joinedAt; }

    public static class Key implements Serializable {
        private Long raceId;
        private Long userId;
        public Key() {}
        public Key(Long raceId, Long userId) { this.raceId = raceId; this.userId = userId; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && java.util.Objects.equals(raceId, key.raceId)
                    && java.util.Objects.equals(userId, key.userId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(raceId, userId); }
    }
}
