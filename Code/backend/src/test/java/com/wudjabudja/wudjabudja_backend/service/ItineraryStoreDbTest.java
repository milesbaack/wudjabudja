package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.ItineraryResponse;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.dto.TripRequest;
import com.wudjabudja.wudjabudja_backend.model.Trip;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Saves the sample itinerary into real Postgres and reads it back (MVP-07), so the JSONB and TIME
// column mappings get tested too. Needs local Postgres with schema.sql + seed.sql, like contextLoads.
// @Transactional rolls everything back at the end, so no test rows are left behind.
@SpringBootTest
@Transactional
class ItineraryStoreDbTest {

    @Autowired TripService tripService;
    @Autowired ItineraryStore store;
    @Autowired GeminiService gemini;
    @Autowired EntityManager entityManager;

    @Test
    void savedItineraryComesBackFromPostgresAndReplaceSwapsIt() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        TripContext context = mapper.readValue(
                Files.readString(Path.of("../ai-pipeline/samples/trip_context.json")), TripContext.class);
        Itinerary sample = gemini.parseAndCheck(
                new ClassPathResource("gemini/itinerary_raw_sample.json").getContentAsString(StandardCharsets.UTF_8), context);

        Trip trip = tripService.create(new TripRequest(null, "San Diego", "2027-03-12", "2027-03-14",
                "guys_trip", new BigDecimal("2400")));

        ItineraryResponse saved = store.replaceForTrip(trip.getId(), sample);
        entityManager.flush();
        entityManager.clear();   // forget cached rows so loadForTrip really reads from Postgres

        ItineraryResponse loaded = store.loadForTrip(trip.getId()).orElseThrow();
        assertEquals(saved, loaded);
        assertEquals(36, optionIds(loaded).size());
        assertTrue(optionIds(loaded).stream().allMatch(id -> id != null));

        // Regenerating replaces the old rows instead of piling up more
        ItineraryResponse again = store.replaceForTrip(trip.getId(), sample);
        entityManager.flush();
        entityManager.clear();
        List<Long> newIds = optionIds(store.loadForTrip(trip.getId()).orElseThrow());
        assertEquals(36, newIds.size());
        assertEquals(optionIds(again), newIds);
        assertTrue(newIds.stream().noneMatch(optionIds(loaded)::contains), "old option rows should be gone");
    }

    @Test
    void tripWithNoItineraryLoadsEmpty() {
        Trip trip = tripService.create(new TripRequest(null, "Austin", "2027-04-01", "2027-04-02",
                "solo_trip", new BigDecimal("500")));
        assertTrue(store.loadForTrip(trip.getId()).isEmpty());
    }

    private static List<Long> optionIds(ItineraryResponse r) {
        List<Long> ids = new ArrayList<>();
        r.days().forEach(d -> d.blocks().forEach(b -> b.subgroups().forEach(s -> s.options().forEach(o -> ids.add(o.id())))));
        return ids;
    }
}
