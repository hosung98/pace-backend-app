package com.pace.race.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "races")
public class Race {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 26)
    private String publicId;
    @Column(nullable = false, length = 60)
    private String title;
    @Column(nullable = false)
    private Long hostUserId;
    @Column(nullable = false)
    private Integer distanceMeters;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Integer joinDeadlineMinutes;
    @Column(nullable = false)
    private Integer minParticipants;
    @Column(nullable = false)
    private Integer maxParticipants;
    @Column(nullable = false, length = 16)
    private String visibility;
    @Column(nullable = false)
    private boolean cancelled;
    private Instant deadlineProcessedAt;
    private Integer deadlineParticipantCount;
    @Column(unique = true, length = 8)
    private String inviteCode;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Race() {}

    public Race(String title, long hostUserId, int distanceMeters, Instant startsAt, int joinDeadlineMinutes,
                int minParticipants, int maxParticipants, String visibility, String inviteCode) {
        publicId = UlidCreator.getUlid().toString();
        this.title = title;
        this.hostUserId = hostUserId;
        this.distanceMeters = distanceMeters;
        this.startsAt = startsAt;
        this.joinDeadlineMinutes = joinDeadlineMinutes;
        this.minParticipants = minParticipants;
        this.maxParticipants = maxParticipants;
        this.visibility = visibility;
        this.inviteCode = inviteCode;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getTitle() { return title; }
    public Long getHostUserId() { return hostUserId; }
    public Integer getDistanceMeters() { return distanceMeters; }
    public Instant getStartsAt() { return startsAt; }
    public Integer getJoinDeadlineMinutes() { return joinDeadlineMinutes; }
    public Integer getMinParticipants() { return minParticipants; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public String getVisibility() { return visibility; }
    public boolean isCancelled() { return cancelled; }
    public Instant getDeadlineProcessedAt() { return deadlineProcessedAt; }
    public Integer getDeadlineParticipantCount() { return deadlineParticipantCount; }
    public String getInviteCode() { return inviteCode; }
    public void cancel() { cancelled = true; }
    public void processDeadline(Instant at, int count) {
        if (deadlineProcessedAt != null) return;
        deadlineProcessedAt = at;
        deadlineParticipantCount = count;
        if (count < minParticipants) cancelled = true;
    }
}
