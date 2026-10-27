package com.pace.user.scheduler;

import com.pace.user.repository.UserRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴 완료된 계정 데이터를 주기적으로 물리 삭제하는 스케줄러입니다. */
@Component
public class AccountCleanupScheduler {
    private final UserRepository users;

    public AccountCleanupScheduler(UserRepository users) { this.users = users; }

    /**
     * 탈퇴 대기 상태 사용자를 정리해 개인정보 잔존을 줄입니다.
     * ShedLock으로 여러 파드 중 하나만 실행합니다.
     */
    @Scheduled(fixedDelayString = "${pace.account-cleanup-delay-ms:60000}")
    @SchedulerLock(
            name = "accountCleanupScheduler.purgeDeletedAccounts",
            lockAtMostFor = "${pace.scheduler.account-cleanup.lock-at-most-for:PT2M}",
            lockAtLeastFor = "${pace.scheduler.account-cleanup.lock-at-least-for:PT0S}")
    @Transactional
    public void purgeDeletedAccounts() {
        users.purgeDeleted();
    }
}
