package com.wudjabudja.wudjabudja_backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Guards the MVP-04 contract: TripContext's JSON must use the same field names,
// in the same order, as Code/ai-pipeline/samples/trip_context.json.
// No database or Gemini key needed.
class TripContextTest {

    private static final Path SAMPLE = Path.of("../ai-pipeline/samples/trip_context.json");

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @SuppressWarnings("unchecked")
    void jsonFieldNamesMatchTheSample() throws Exception {
        TripContext context = new TripContext(
                1L, "San Diego", "2027-03-12", "2027-03-14", 3, "guys_trip", new BigDecimal("2400"),
                List.of(new TripContext.Participant(
                        1L, "leader", "07:30", "intermediate", 15,
                        Map.of("lodging", 300.0, "transit", 60.0, "food", 150.0, "activities", 120.0),
                        new BigDecimal("30"), List.of("sports", "outdoors"))));

        Map<String, Object> ours = mapper.readValue(mapper.writeValueAsString(context), LinkedHashMap.class);
        Map<String, Object> sample = mapper.readValue(Files.readString(SAMPLE), LinkedHashMap.class);

        assertEquals(new ArrayList<>(sample.keySet()), new ArrayList<>(ours.keySet()), "trip-level fields");
        assertEquals(new ArrayList<>(firstParticipant(sample).keySet()), new ArrayList<>(firstParticipant(ours).keySet()),
                "participant fields");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstParticipant(Map<String, Object> context) {
        return ((List<Map<String, Object>>) context.get("participants")).get(0);
    }
}
