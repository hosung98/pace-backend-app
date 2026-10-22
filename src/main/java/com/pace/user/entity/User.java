package com.pace.user.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 26)
    private String publicId;
    @Column(nullable = false, length = 30)
    private String nickname;
    @Column(unique = true, length = 30)
    private String handle;
    @Column(length = 500)
    private String profileImageUrl;
    @Column(length = 150)
    private String bio;
    @Column(nullable = false, length = 16)
    private String profileVisibility = "public";
    @Column(nullable = false, length = 16)
    private String status = "active";
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    private Instant deletedAt;

    protected User() {}

    public User(String nickname) {
        this.publicId = UlidCreator.getUlid().toString();
        this.nickname = nickname;
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getNickname() { return nickname; }
    public String getHandle() { return handle; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getBio() { return bio; }
    public String getProfileVisibility() { return profileVisibility; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setNickname(String value) { nickname = value; }
    public void setHandle(String value) { handle = value; }
    public void setBio(String value) { bio = value; }
    public void setProfileVisibility(String value) { profileVisibility = value; }
    public void markDeleting() { status = "deleting"; deletedAt = Instant.now(); }
}
