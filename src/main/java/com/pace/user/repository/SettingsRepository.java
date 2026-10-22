package com.pace.user.repository;

import com.pace.user.entity.UserSettings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<UserSettings, Long> {}
