package com.pace.user.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pace.auth.repository.TokenRepository;
import com.pace.common.exception.BusinessException;
import com.pace.run.repository.RunRepository;
import com.pace.user.dto.*;
import com.pace.user.entity.*;
import com.pace.user.repository.*;
import com.pace.user.service.*;
import com.pace.common.util.TimeZones;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {
    private final UserAccess access;
    private final UserRepository users;
    private final SettingsRepository settings;
    private final TokenRepository tokens;
    private final RunRepository runs;

    public UserServiceImpl(UserAccess access, UserRepository users, SettingsRepository settings,
                           TokenRepository tokens, RunRepository runs) {
        this.access = access;
        this.users = users;
        this.settings = settings;
        this.tokens = tokens;
        this.runs = runs;
    }

    @Transactional(readOnly = true)
    public MeResponse me(String subject) {
        return response(access.user(subject, false));
    }

    @Transactional
    public MeResponse update(String subject, UpdateProfileRequest request) {
        User user = access.user(subject, true);
        if (request.nickname() != null) {
            if (request.nickname().isBlank()) throw BusinessException.invalid("닉네임은 공백만 사용할 수 없습니다.");
            user.setNickname(request.nickname());
        }
        if (request.handle() != null) {
            String handle = nullableText(request.handle(), "handle");
            if (handle != null && !handle.matches("^[a-z0-9_.]{3,30}$")) {
                throw BusinessException.invalid("사용자 ID는 영문 소문자·숫자·밑줄·마침표로 3~30자여야 합니다.");
            }
            user.setHandle(handle);
        }
        if (request.bio() != null) {
            String bio = nullableText(request.bio(), "bio");
            if (bio != null && bio.length() > 150) throw BusinessException.invalid("소개는 150자 이하여야 합니다.");
            user.setBio(bio);
        }
        if (request.profileVisibility() != null) user.setProfileVisibility(request.profileVisibility());
        users.flush();
        return response(user);
    }

    @Transactional(readOnly = true)
    public SettingsResponse settings(String subject) {
        User user = access.user(subject, false);
        return response(settings.findById(user.getId()).orElseThrow(), user);
    }

    @Transactional
    public SettingsResponse updateSettings(String subject, UpdateSettingsRequest request) {
        User user = access.user(subject, true);
        UserSettings value = settings.findById(user.getId()).orElseThrow();
        if (request.weeklyGoalMeters() != null) {
            if (request.weeklyGoalMeters() % 5000 != 0) throw BusinessException.invalid("주간 목표는 5,000m 단위여야 합니다.");
            value.setWeeklyGoalMeters(request.weeklyGoalMeters());
        }
        if (request.distanceUnit() != null) value.setDistanceUnit(request.distanceUnit());
        if (request.timeZone() != null) {
            TimeZones.require(request.timeZone());
            value.setTimeZone(request.timeZone());
        }
        if (request.profileVisibility() != null) user.setProfileVisibility(request.profileVisibility());
        UpdateNotificationRequest notifications = request.notifications();
        if (notifications != null) {
            if (notifications.runReminder() != null) value.setNotifyRunReminder(notifications.runReminder());
            if (notifications.weeklySummary() != null) value.setNotifyWeeklySummary(notifications.weeklySummary());
            if (notifications.race() != null) value.setNotifyRace(notifications.race());
            if (notifications.crew() != null) value.setNotifyCrew(notifications.crew());
            if (notifications.runReminderTime() != null) value.setRunReminderTime(LocalTime.parse(notifications.runReminderTime()));
        }
        return response(value, user);
    }

    @Transactional
    public void delete(String subject) {
        User user = access.user(subject, true);
        user.markDeleting();
        tokens.revokeUser(user.getId());
    }

    private MeResponse response(User user) {
        return new MeResponse(user.getPublicId(), user.getNickname(), user.getHandle(), user.getProfileImageUrl(),
                user.getBio(), user.getProfileVisibility(), List.of("google"), user.getCreatedAt(),
                new UserCountsResponse(runs.countByUserIdAndDeletedAtIsNull(user.getId())), null);
    }

    private SettingsResponse response(UserSettings value, User user) {
        return new SettingsResponse(value.getWeeklyGoalMeters(), value.getDistanceUnit(), value.getTimeZone(),
                new NotificationSettingsResponse(value.isNotifyRunReminder(), value.isNotifyWeeklySummary(),
                        value.getRunReminderTime().toString(), value.isNotifyRace(), value.isNotifyCrew()),
                user.getProfileVisibility());
    }

    private String nullableText(JsonNode node, String field) {
        if (node.isNull()) return null;
        if (!node.isTextual()) throw BusinessException.invalid(field + " 값은 문자열 또는 null이어야 합니다.");
        return node.textValue();
    }
}
