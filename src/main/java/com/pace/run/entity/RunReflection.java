package com.pace.run.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "run_reflections")
public class RunReflection {
    @Id
    private Long runId;
    @Column(nullable = false, length = 16)
    private String effort;
    @Column(nullable = false, length = 500)
    private String note;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected RunReflection() {}

    public RunReflection(long runId, String effort, String note) {
        this.runId = runId;
        this.effort = effort;
        this.note = note;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getRunId() { return runId; }
    public String getEffort() { return effort; }
    public String getNote() { return note; }
    public void update(String effort, String note) {
        this.effort = effort;
        this.note = note;
    }
}
