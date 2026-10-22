package com.pace.user.repository;

import com.pace.user.entity.User;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPublicIdAndStatus(String publicId, String status);
    boolean existsByPublicIdAndStatus(String publicId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.publicId=:id and u.status='active'")
    Optional<User> lockActive(@Param("id") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id=:id")
    Optional<User> lockById(@Param("id") long id);

    @Modifying
    @Query(value = """
            DELETE FROM users WHERE id IN
            (SELECT id FROM users WHERE status='deleting' ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED)
            """, nativeQuery = true)
    int purgeDeleted();
}
