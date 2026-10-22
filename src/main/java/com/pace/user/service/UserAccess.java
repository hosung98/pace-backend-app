package com.pace.user.service;

import com.pace.common.exception.BusinessException;
import com.pace.user.entity.User;
import com.pace.user.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class UserAccess {
    private final UserRepository users;

    public UserAccess(UserRepository users) { this.users = users; }

    public User user(String publicId, boolean lock) {
        return (lock ? users.lockActive(publicId) : users.findByPublicIdAndStatus(publicId, "active"))
                .orElseThrow(BusinessException::unauthorized);
    }

    public long id(String publicId, boolean lock) { return user(publicId, lock).getId(); }
}
