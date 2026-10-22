package com.pace.auth.repository;

import com.pace.auth.entity.RefreshToken;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String hash);

    @Query("select t.userId from RefreshToken t where t.tokenHash=:hash")
    Optional<Long> findUserId(@Param("hash") String hash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt=CURRENT_TIMESTAMP where t.familyId=:family and t.revokedAt is null")
    int revokeFamily(@Param("family") String family);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt=CURRENT_TIMESTAMP where t.userId=:user and t.revokedAt is null")
    int revokeUser(@Param("user") long user);
}
