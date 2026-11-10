package com.wudjabudja.wudjabudja_backend.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItineraryOptionRepository extends JpaRepository<ItineraryOption, Long> {

    // Insert order = id order, which keeps Gemini's option order when we read them back
    List<ItineraryOption> findByTripIdOrderByIdAsc(Long tripId);

    // One DELETE statement instead of loading every row first. Votes on these rows go too (ON DELETE CASCADE).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ItineraryOption o WHERE o.tripId = :tripId")
    int deleteByTripId(@Param("tripId") Long tripId);
}
