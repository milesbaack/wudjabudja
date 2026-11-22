package com.wudjabudja.wudjabudja_backend.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripParticipantRepository extends JpaRepository<TripParticipant, Long> {

    List<TripParticipant> findByTripIdOrderByIdAsc(Long tripId);

    boolean existsByTripIdAndUserId(Long tripId, Long userId);

    Optional<TripParticipant> findByTripIdAndUserId(Long tripId, Long userId);
}
