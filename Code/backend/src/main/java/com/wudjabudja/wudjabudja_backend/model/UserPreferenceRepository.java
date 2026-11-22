package com.wudjabudja.wudjabudja_backend.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    Optional<UserPreference> findByTripIdAndUserId(Long tripId, Long userId);

    List<UserPreference> findByTripIdOrderByUserIdAsc(Long tripId);

    boolean existsByTripIdAndUserId(Long tripId, Long userId);
}