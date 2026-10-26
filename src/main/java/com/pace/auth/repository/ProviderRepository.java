package com.pace.auth.repository;

import com.pace.auth.entity.AuthProvider;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<AuthProvider, Long> {
    Optional<AuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<AuthProvider> findAllByUserId(Long userId);
}
