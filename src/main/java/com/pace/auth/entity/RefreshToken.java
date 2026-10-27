package com.pace.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(64)", length = 64)
    private String tokenHash;
    private String familyId;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}
    public RefreshToken(long userId, String hash, String family, Instant expires) {
        this.userId = userId;
        this.tokenHash = hash;
        this.familyId = family;
        this.expiresAt = expires;
    }
    public Long getUserId() { return userId; }
    public Long getId() { return id; }
    public String getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
}
