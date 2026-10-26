package com.pace.crew.repository;

import com.pace.crew.entity.CrewMonthlyGoal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewMonthlyGoalRepository extends JpaRepository<CrewMonthlyGoal, CrewMonthlyGoal.Key> {
    Optional<CrewMonthlyGoal> findByCrewIdAndMonthStart(long crewId, LocalDate monthStart);
}
