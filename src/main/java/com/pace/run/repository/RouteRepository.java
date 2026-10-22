package com.pace.run.repository;

import com.pace.run.entity.RunRoute;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<RunRoute, Long> {}
