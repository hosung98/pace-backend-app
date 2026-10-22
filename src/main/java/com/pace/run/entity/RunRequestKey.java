package com.pace.run.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "run_idempotency_keys")
@IdClass(RunRequestKey.Key.class)
public class RunRequestKey {
    @Id private Long userId;
    @Id private String key;
    private Long runId;

    protected RunRequestKey() {}
    public RunRequestKey(long userId, String key, long runId) {
        this.userId = userId;
        this.key = key;
        this.runId = runId;
    }
    public Long getRunId() { return runId; }

    public static class Key implements Serializable {
        public Long userId;
        public String key;
        public Key() {}
        public Key(long userId, String key) { this.userId = userId; this.key = key; }
        @Override public boolean equals(Object other) {
            return other instanceof Key that && Objects.equals(userId, that.userId) && Objects.equals(key, that.key);
        }
        @Override public int hashCode() { return Objects.hash(userId, key); }
    }
}
