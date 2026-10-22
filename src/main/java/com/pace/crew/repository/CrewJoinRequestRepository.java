package com.pace.crew.repository;

import com.pace.crew.entity.CrewJoinRequest;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CrewJoinRequestRepository extends JpaRepository<CrewJoinRequest, CrewJoinRequest.Key> {
    Optional<CrewJoinRequest> findByUserId(long userId);
    Optional<CrewJoinRequest> findByCrewIdAndUserId(long crewId, long userId);
    List<CrewJoinRequest> findAllByCrewIdOrderByCreatedAtAsc(long crewId);

    @Modifying
    @Query("delete from CrewJoinRequest r where r.crewId=:crewId and r.userId=:userId")
    int remove(@Param("crewId") long crewId, @Param("userId") long userId);
}
