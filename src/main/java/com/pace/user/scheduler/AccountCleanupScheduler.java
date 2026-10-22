package com.pace.user.scheduler;

import com.pace.user.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountCleanupScheduler {
    private final UserRepository users;
    public AccountCleanupScheduler(UserRepository users) { this.users = users; }

    @Scheduled(fixedDelayString = "${pace.account-cleanup-delay-ms:60000}")
    @Transactional
    public void purgeDeletedAccounts() {
        users.purgeDeleted();
    }
}
