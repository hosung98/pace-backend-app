package com.pace.crew.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "community_user_state")
public class CommunityUserState {
    @Id private Long userId;
    private Instant lastCrewLeftAt;

    protected CommunityUserState() {}
    public CommunityUserState(long userId) { this.userId = userId; }
    public Long getUserId() { return userId; }
    public Instant getLastCrewLeftAt() { return lastCrewLeftAt; }
    public void setLastCrewLeftAt(Instant value) { lastCrewLeftAt = value; }
}
