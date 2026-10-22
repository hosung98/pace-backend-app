package com.pace.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_auth_providers")
public class AuthProvider {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String provider = "google";
    private String providerUserId;
    private Instant createdAt = Instant.now();
    private Instant lastLoginAt = Instant.now();

    protected AuthProvider() {}
    public AuthProvider(long userId, String subject) {
        this.userId = userId;
        this.providerUserId = subject;
    }
    public Long getUserId() { return userId; }
    public void login() { lastLoginAt = Instant.now(); }
}
