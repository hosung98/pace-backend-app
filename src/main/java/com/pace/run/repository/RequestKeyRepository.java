package com.pace.run.repository;

import com.pace.run.entity.RunRequestKey;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestKeyRepository extends JpaRepository<RunRequestKey, RunRequestKey.Key> {}
