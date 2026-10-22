package com.pace.crew.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "crew_members")
@IdClass(CrewMember.Key.class)
public class CrewMember {
    @Id private Long crewId;
    @Id private Long userId;
    @Column(nullable = false, length = 16)
    private String role;
    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    protected CrewMember() {}
    public CrewMember(long crewId, long userId, String role) {
        this.crewId = crewId;
        this.userId = userId;
        this.role = role;
    }
    public Long getCrewId() { return crewId; }
    public Long getUserId() { return userId; }
    public String getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
    public void setRole(String value) { role = value; }

    public static class Key implements Serializable {
        private Long crewId;
        private Long userId;
        public Key() {}
        public Key(Long crewId, Long userId) { this.crewId = crewId; this.userId = userId; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && java.util.Objects.equals(crewId, key.crewId)
                    && java.util.Objects.equals(userId, key.userId);
        }
        @Override public int hashCode() { return java.util.Objects.hash(crewId, userId); }
    }
}
