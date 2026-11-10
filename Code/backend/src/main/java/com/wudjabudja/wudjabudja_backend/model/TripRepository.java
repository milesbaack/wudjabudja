package com.wudjabudja.wudjabudja_backend.model;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    // Used to keep join codes unique before we insert
    boolean existsByJoinCode(String joinCode);

    // Newest first, for the "Your trips" list
    List<Trip> findByCreatorIdOrderByIdDesc(Long creatorId);

    // SELECT ... FOR UPDATE: holds this trip's row until the transaction ends, so two saves
    // of the same trip's itinerary take turns instead of mixing their rows together
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> lockById(@Param("id") Long id);
}
