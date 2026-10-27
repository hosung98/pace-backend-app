package com.pace.crew.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "crew_join_requests")
@IdClass(CrewJoinRequest.Key.class)
public class CrewJoinRequest {
    @Id private Long crewId;
    @Id private Long userId;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CrewJoinRequest() {}
    public CrewJoinRequest(long crewId, long userId) { this.crewId = crewId; this.userId = userId; }
    public Long getCrewId() { return crewId; }
    public Long getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    public static class Key implements Serializable {
        private Long crewId;
        private Long userId;
        public Key() {}
        public Key(Long crewId, Long userId) { this.crewId = crewId; this.userId = userId; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(crewId, key.crewId)
                    && Objects.equals(userId, key.userId);
        }
        @Override public int hashCode() { return Objects.hash(crewId, userId); }
    }
}
