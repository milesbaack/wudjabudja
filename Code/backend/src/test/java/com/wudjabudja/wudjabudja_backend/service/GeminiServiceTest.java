package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Runs GeminiService's parsing and checks on a real saved Gemini answer (MVP-05).
// gemini/itinerary_raw_sample.json is Gemini's Oct 27 reply to samples/trip_context.json.
// No API key or network needed.
class GeminiServiceTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final GeminiService gemini = new GeminiService(mapper);
    private final TripContext sampleTrip = mapper.readValue(
            Files.readString(Path.of("../ai-pipeline/samples/trip_context.json")), TripContext.class);
    private final String savedAnswer = new ClassPathResource("gemini/itinerary_raw_sample.json")
            .getContentAsString(StandardCharsets.UTF_8);

    GeminiServiceTest() throws Exception {}

    @Test
    void savedAnswerPassesEveryCheck() {
        Itinerary itinerary = gemini.parseAndCheck(savedAnswer, sampleTrip);

        assertEquals(3, itinerary.days().size());
        for (Itinerary.Day day : itinerary.days()) {
            assertEquals(3, day.blocks().size(), day.date() + " should have morning, afternoon, evening");
            for (Itinerary.Block block : day.blocks()) {
                for (Itinerary.Subgroup sub : block.subgroups()) {
                    assertEquals(3, sub.options().size(), day.date() + " " + block.timeBlock() + " should have 3 options");
                }
            }
        }
    }

    @Test
    void optionOverBudgetCapIsAClearError() {
        // Push the first option's cost to $500. Both sample travelers cap at about $30 a block.
        String tampered = gemini.extractModelText(savedAnswer)
                .replaceFirst("\"estimated_cost_usd\"\\s*:\\s*[0-9.]+", "\"estimated_cost_usd\": 500");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> gemini.parseAndCheck(asGeminiResponse(tampered), sampleTrip));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().contains("costs $500"), error.getReason());
    }

    @Test
    void answerThatIsNotJsonIsAClearError() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> gemini.parseAndCheck(asGeminiResponse("Sorry, I can't help with that."), sampleTrip));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().startsWith("Gemini's answer is not valid itinerary JSON"), error.getReason());
    }

    @Test
    void missingBudgetCapDoesNotCrash() {
        // per_block_cap_usd is optional in user_preferences. GeminiTest crashed on null here.
        List<TripContext.Participant> noCaps = sampleTrip.participants().stream()
                .map(p -> new TripContext.Participant(p.userId(), p.role(), p.wakeTime(), p.experienceLevel(),
                        p.maxRadiusMiles(), p.categoricalBudgets(), null, p.interests()))
                .toList();
        TripContext tripWithoutCaps = new TripContext(sampleTrip.tripId(), sampleTrip.destination(),
                sampleTrip.startDate(), sampleTrip.endDate(), sampleTrip.numDays(), sampleTrip.tripType(),
                sampleTrip.totalBudget(), noCaps);

        assertDoesNotThrow(() -> gemini.parseAndCheck(savedAnswer, tripWithoutCaps));
    }

    // Wraps model text the way Gemini's Interactions API does: steps[model_output].content[text].text
    private String asGeminiResponse(String modelText) {
        return mapper.writeValueAsString(Map.of("steps", List.of(Map.of(
                "type", "model_output",
                "content", List.of(Map.of("type", "text", "text", modelText))))));
    }
}
