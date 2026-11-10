package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.ItineraryResponse;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import com.wudjabudja.wudjabudja_backend.model.ItineraryOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// Checks that flattening Gemini's tree into itinerary_options rows and rebuilding it loses nothing (MVP-07).
// Uses the saved Gemini answer from GeminiServiceTest. No database or API key needed.
class ItineraryStoreTest {

    private static final OffsetDateTime GENERATED = OffsetDateTime.parse("2026-11-05T14:30:00-08:00");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Itinerary sample;

    ItineraryStoreTest() throws Exception {
        GeminiService gemini = new GeminiService(mapper);
        TripContext trip = mapper.readValue(
                Files.readString(Path.of("../ai-pipeline/samples/trip_context.json")), TripContext.class);
        String raw = new ClassPathResource("gemini/itinerary_raw_sample.json").getContentAsString(StandardCharsets.UTF_8);
        sample = gemini.parseAndCheck(raw, trip);
    }

    @Test
    void flattenMakesOneRowPerOptionWithItsBlockInfo() {
        List<ItineraryOption> rows = ItineraryStore.flatten(7L, sample, GENERATED);

        assertEquals(36, rows.size(), "3 days x 3 blocks, some split in two, 3 options each");
        for (ItineraryOption row : rows) {
            assertEquals(7L, row.getTripId());
            assertNotNull(row.getDate());
            assertTrue(List.of("morning", "afternoon", "evening").contains(row.getTimeBlock()));
            assertFalse(row.getSubgroupParticipants().isEmpty());
            assertNotNull(row.getStartTime(), row.getTitle());
            assertEquals(GENERATED, row.getCreatedAt());
        }
        // Split blocks keep their reason on every row of both subgroups
        assertTrue(rows.stream().anyMatch(r -> r.getSubgroupParticipants().equals(List.of(2L)) && r.getSplitReason() != null));
    }

    @Test
    void rebuildGivesBackTheSameItinerary() {
        List<ItineraryOption> rows = withIds(ItineraryStore.flatten(7L, sample, GENERATED));
        Collections.shuffle(rows, new Random(42));   // the order rows come back in shouldn't matter

        ItineraryResponse rebuilt = ItineraryStore.rebuild(7L, rows);

        assertEquals(twoDecimalCosts(sample), withoutIds(rebuilt));
        assertEquals(7L, rebuilt.tripId());
        assertEquals("2026-11-05T22:30Z", rebuilt.generatedAt(), "generated_at is sent in UTC");
    }

    @Test
    void everyOptionGetsItsDatabaseId() {
        ItineraryResponse rebuilt = ItineraryStore.rebuild(7L, withIds(ItineraryStore.flatten(7L, sample, GENERATED)));

        List<Long> ids = new ArrayList<>();
        rebuilt.days().forEach(d -> d.blocks().forEach(b -> b.subgroups().forEach(s -> s.options().forEach(o -> ids.add(o.id())))));
        assertEquals(36, ids.size());
        assertEquals(1L, ids.get(0), "first option Gemini gave keeps the first id");
        assertEquals(36, ids.stream().distinct().count());
    }

    @Test
    void daysAndBlocksComeBackInOrderEvenIfSavedOutOfOrder() {
        Itinerary scrambled = new Itinerary(List.of(
                day("2027-03-13", block("evening"), block("morning")),
                day("2027-03-12", block("afternoon"), block("morning"), block("evening"))));

        ItineraryResponse rebuilt = ItineraryStore.rebuild(1L, withIds(ItineraryStore.flatten(1L, scrambled, GENERATED)));

        assertEquals(List.of("2027-03-12", "2027-03-13"), rebuilt.days().stream().map(ItineraryResponse.Day::date).toList());
        assertEquals(List.of("morning", "afternoon", "evening"), blockNames(rebuilt.days().get(0)));
        assertEquals(List.of("morning", "evening"), blockNames(rebuilt.days().get(1)));
    }

    @Test
    void missingCostBecomesZeroAndOddTimesAreHandled() {
        Itinerary odd = new Itinerary(List.of(day("2027-03-12", new Itinerary.Block("morning", List.of(
                new Itinerary.Subgroup(List.of(1L), null, List.of(
                        new Itinerary.Option("Beach walk", null, "outdoors", "La Jolla", "9:00", "noon", null, "Free and outdoors."))))))));

        ItineraryOption row = ItineraryStore.flatten(1L, odd, GENERATED).get(0);

        assertEquals(BigDecimal.ZERO, row.getEstimatedCostUsd(), "null cost -> 0, the column is NOT NULL");
        assertEquals("", row.getDescription(), "null description -> empty, the column is NOT NULL");
        assertEquals(LocalTime.of(9, 0), row.getStartTime(), "\"9:00\" still reads as 09:00");
        assertNull(row.getEndTime(), "unreadable time -> null instead of a crash");
    }

    @Test
    void timeParsingAcceptsTheUsualShapes() {
        assertEquals(LocalTime.of(9, 30), ItineraryStore.parseTime("09:30"));
        assertEquals(LocalTime.of(21, 0), ItineraryStore.parseTime("21:00:00"));
        assertNull(ItineraryStore.parseTime("25:00"));
        assertNull(ItineraryStore.parseTime(null));
    }

    // ---------- helpers ----------

    // Pretend the database assigned ids 1, 2, 3... in insert order
    private static List<ItineraryOption> withIds(List<ItineraryOption> rows) {
        for (int i = 0; i < rows.size(); i++) rows.get(i).setId(i + 1L);
        return new ArrayList<>(rows);
    }

    // Turn the response back into Gemini's shape so we can compare with assertEquals
    private static Itinerary withoutIds(ItineraryResponse r) {
        return new Itinerary(r.days().stream().map(d -> new Itinerary.Day(d.date(), d.blocks().stream()
                .map(b -> new Itinerary.Block(b.timeBlock(), b.subgroups().stream()
                        .map(s -> new Itinerary.Subgroup(s.subgroupParticipants(), s.splitReason(), s.options().stream()
                                .map(o -> new Itinerary.Option(o.title(), o.description(), o.category(), o.neighborhood(),
                                        o.startTime(), o.endTime(), o.estimatedCostUsd(), o.whyItFits()))
                                .toList()))
                        .toList()))
                .toList())).toList());
    }

    // Gemini sends 15, the database column holds 15.00. Same money, so compare at 2 decimals.
    private static Itinerary twoDecimalCosts(Itinerary it) {
        return new Itinerary(it.days().stream().map(d -> new Itinerary.Day(d.date(), d.blocks().stream()
                .map(b -> new Itinerary.Block(b.timeBlock(), b.subgroups().stream()
                        .map(s -> new Itinerary.Subgroup(s.subgroupParticipants(), s.splitReason(), s.options().stream()
                                .map(o -> new Itinerary.Option(o.title(), o.description(), o.category(), o.neighborhood(),
                                        o.startTime(), o.endTime(), o.estimatedCostUsd().setScale(2), o.whyItFits()))
                                .toList()))
                        .toList()))
                .toList())).toList());
    }

    private static Itinerary.Day day(String date, Itinerary.Block... blocks) {
        return new Itinerary.Day(date, List.of(blocks));
    }

    private static Itinerary.Block block(String timeBlock) {
        return new Itinerary.Block(timeBlock, List.of(new Itinerary.Subgroup(List.of(1L), null, List.of(
                new Itinerary.Option(timeBlock + " option", "desc", "food", "Downtown", "12:00", "13:00", BigDecimal.TEN, "Fits.")))));
    }

    private static List<String> blockNames(ItineraryResponse.Day day) {
        return day.blocks().stream().map(ItineraryResponse.Block::timeBlock).toList();
    }
}
