package com.pace.progress.service;

import com.pace.progress.dto.UserProgressResponse;

public interface ProgressService {
    UserProgressResponse progress(String subject);
    UserProgressResponse setRepresentativeBadge(String subject, String badgeId);
    void recordAchievements(long userId);
}
