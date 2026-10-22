package com.pace.user.service;

import com.pace.user.dto.*;

public interface UserService {
    MeResponse me(String subject);
    MeResponse update(String subject, UpdateProfileRequest request);
    SettingsResponse settings(String subject);
    SettingsResponse updateSettings(String subject, UpdateSettingsRequest request);
    void delete(String subject);
}
