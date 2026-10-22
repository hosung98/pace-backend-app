package com.pace.crew.repository;

import com.pace.crew.entity.CrewMember;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CrewMemberRepository extends JpaRepository<CrewMember, CrewMember.Key> {
    Optional<CrewMember> findByUserId(long userId);
    Optional<CrewMember> findByCrewIdAndUserId(long crewId, long userId);
    boolean existsByCrewIdAndUserId(long crewId, long userId);
    List<CrewMember> findAllByCrewId(long crewId);

    @Modifying
    @Query("delete from CrewMember m where m.crewId=:crewId and m.userId=:userId")
    int remove(@Param("crewId") long crewId, @Param("userId") long userId);
}
