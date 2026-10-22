package com.pace.crew.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "crews")
public class Crew {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 26)
    private String publicId;
    @Column(nullable = false, length = 20)
    private String name;
    @Column(nullable = false, length = 100)
    private String intro;
    @Column(length = 30)
    private String sido;
    @Column(length = 40)
    private String sigungu;
    @Column(nullable = false)
    private Long leaderUserId;
    @Column(nullable = false, unique = true, length = 8)
    private String inviteCode;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Crew() {}

    public Crew(String name, String intro, String sido, String sigungu, long leaderUserId, String inviteCode) {
        publicId = UlidCreator.getUlid().toString();
        this.name = name;
        this.intro = intro;
        this.sido = sido;
        this.sigungu = sigungu;
        this.leaderUserId = leaderUserId;
        this.inviteCode = inviteCode;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getName() { return name; }
    public String getIntro() { return intro; }
    public String getSido() { return sido; }
    public String getSigungu() { return sigungu; }
    public Long getLeaderUserId() { return leaderUserId; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String value) { inviteCode = value; }
    public void setLeaderUserId(long value) { leaderUserId = value; }
}
