package com.pace.race.repository;

import com.pace.race.entity.RaceParticipant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RaceParticipantRepository extends JpaRepository<RaceParticipant, RaceParticipant.Key> {
    boolean existsByRaceIdAndUserId(long raceId, long userId);
    Optional<RaceParticipant> findByRaceIdAndUserId(long raceId, long userId);
    List<RaceParticipant> findAllByRaceId(long raceId);
    List<RaceParticipant> findAllByUserId(long userId);

    @Query(value = "SELECT count(*) FROM race_participants WHERE race_id=:raceId", nativeQuery = true)
    long countForRace(@Param("raceId") long raceId);

    @Query(value = """
        SELECT u.public_id AS userId, u.nickname AS nickname,
               (rp.user_id=:hostId) AS isHost, (rp.user_id=:viewerId) AS isMe
          FROM race_participants rp JOIN users u ON u.id=rp.user_id
         WHERE rp.race_id=:raceId
         ORDER BY (rp.user_id=:hostId) DESC, rp.joined_at ASC
        """, nativeQuery = true)
    List<RaceParticipantRow> participants(@Param("raceId") long raceId,
                                          @Param("hostId") long hostId,
                                          @Param("viewerId") long viewerId);

    @Modifying
    @Query("delete from RaceParticipant p where p.raceId=:raceId and p.userId=:userId")
    int remove(@Param("raceId") long raceId, @Param("userId") long userId);
}
