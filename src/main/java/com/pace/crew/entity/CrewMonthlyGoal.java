package com.pace.crew.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "crew_monthly_goals")
@IdClass(CrewMonthlyGoal.Key.class)
public class CrewMonthlyGoal {
    @Id
    private Long crewId;
    @Id
    private LocalDate monthStart;
    @Column(nullable = false)
    private int targetKm;
    @Column(nullable = false)
    private Instant configuredAt = Instant.now();
    @Column(nullable = false)
    private boolean rewardEligible;
    private Instant attainedAt;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected CrewMonthlyGoal() {}

    public CrewMonthlyGoal(long crewId, LocalDate monthStart, int targetKm) {
        this.crewId = crewId;
        this.monthStart = monthStart;
        this.targetKm = targetKm;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getCrewId() { return crewId; }
    public LocalDate getMonthStart() { return monthStart; }
    public int getTargetKm() { return targetKm; }
    public void setTargetKm(int value) { targetKm = value; }
    public Instant getConfiguredAt() { return configuredAt; }
    public boolean isRewardEligible() { return rewardEligible; }
    public Instant getAttainedAt() { return attainedAt; }
    public void configure(int value, boolean eligible) {
        targetKm = value;
        configuredAt = Instant.now();
        rewardEligible = eligible;
        attainedAt = null;
    }
    public void attain(Instant value) { attainedAt = value; }

    public static class Key implements Serializable {
        private Long crewId;
        private LocalDate monthStart;
        public Key() {}
        public Key(Long crewId, LocalDate monthStart) { this.crewId = crewId; this.monthStart = monthStart; }
        @Override public boolean equals(Object other) {
            return other instanceof Key key && java.util.Objects.equals(crewId, key.crewId)
                    && java.util.Objects.equals(monthStart, key.monthStart);
        }
        @Override public int hashCode() { return java.util.Objects.hash(crewId, monthStart); }
    }
}
