package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.TripBriefing;
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

    @Test
    void temporary503RetriesAndThenSucceeds() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger sleeps = new java.util.concurrent.atomic.AtomicInteger();

        GeminiService retryingGemini = new GeminiService(
                mapper,
                req -> {
                    int n = calls.incrementAndGet();
                    if (n < 3) {
                        return fakeHttpResponse(503, "{\"error\":\"high demand\"}");
                    }
                    return fakeHttpResponse(200, savedAnswer);
                },
                d -> sleeps.incrementAndGet(),
                "test-gemini-key"
        );

        Itinerary result = retryingGemini.generateItinerary(sampleTrip);

        assertEquals(3, result.days().size());
        assertEquals(3, calls.get(), "should succeed on the 3rd try after two 503 responses");
        assertEquals(2, sleeps.get(), "should wait between tries 1->2 and 2->3");
    }

    @Test
    void temporary503GivesUpAfterThreeTries() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger sleeps = new java.util.concurrent.atomic.AtomicInteger();

        GeminiService failingGemini = new GeminiService(
                mapper,
                req -> {
                    calls.incrementAndGet();
                    return fakeHttpResponse(503, "Service temporarily overloaded");
                },
                d -> sleeps.incrementAndGet(),
                "test-gemini-key"
        );

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> failingGemini.generateItinerary(sampleTrip));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().contains("503"), error.getReason());
        assertEquals(3, calls.get(), "should try at most 3 times");
        assertEquals(2, sleeps.get(), "should sleep twice between the 3 tries");
    }

    @Test
    void failedOutputChecksDoNotAutoRetry() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        String overCapJson = gemini.extractModelText(savedAnswer)
                .replaceFirst("\"estimated_cost_usd\"\\s*:\\s*[0-9.]+", "\"estimated_cost_usd\": 500");

        GeminiService oneShotGemini = new GeminiService(
                mapper,
                req -> {
                    calls.incrementAndGet();
                    return fakeHttpResponse(200, asGeminiResponse(overCapJson));
                },
                d -> {},
                "test-gemini-key"
        );

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> oneShotGemini.generateItinerary(sampleTrip));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals(1, calls.get(), "failed output check should not auto-retry");
    }

    @Test
    void briefingResponseIsParsedAndCheckedAgainstParticipants() {
        String sampleBriefingJson = """
                {
                  "customs": [
                    {"tip": "Stay 15ft from sea lions", "why": "Protected marine wildlife"},
                    {"tip": "No glass on beaches", "why": "City ordinance"},
                    {"tip": "Tip 18-20%", "why": "Standard service etiquette"},
                    {"tip": "Casual coastal dress", "why": "Local norm"}
                  ],
                  "language": {
                    "primary_language": "English (SoCal Slang)",
                    "phrases": [
                      {"phrase": "Cali Burrito", "meaning": "Burrito with fries"},
                      {"phrase": "June Gloom", "meaning": "Morning marine layer"},
                      {"phrase": "The 5", "meaning": "Interstate 5"},
                      {"phrase": "Taco Tuesday", "meaning": "Weekly taco specials"},
                      {"phrase": "Sunset sesh", "meaning": "Evening beach hangout"}
                    ]
                  },
                  "weather_note": "Typical mid-March weather: 68F sunny days, 55F evenings.",
                  "packing_list": [
                    {"item": "Light jacket", "reason": "Cool ocean breeze"}
                  ],
                  "historical_facts": [
                    "Balboa Park hosted the 1915 Exposition."
                  ],
                  "season_advisory": {
                    "crowd_level": "medium",
                    "summary": "Moderate spring crowds."
                  },
                  "souvenirs": [
                    {"user_id": 1, "idea": "Padres cap", "estimated_cost_usd": 30},
                    {"user_id": 2, "idea": "Craft hot sauce", "estimated_cost_usd": 15}
                  ]
                }
                """;

        TripBriefing briefing = gemini.parseAndCheckBriefing(asGeminiResponse(sampleBriefingJson), sampleTrip);

        assertEquals(4, briefing.customs().size());
        assertEquals("English (SoCal Slang)", briefing.language().primaryLanguage());
        assertEquals(5, briefing.language().phrases().size());
        assertEquals("medium", briefing.seasonAdvisory().crowdLevel());
        assertEquals(2, briefing.souvenirs().size());
    }

    // Wraps model text the way Gemini's Interactions API does: steps[model_output].content[text].text
    private String asGeminiResponse(String modelText) {
        return mapper.writeValueAsString(Map.of("steps", List.of(Map.of(
                "type", "model_output",
                "content", List.of(Map.of("type", "text", "text", modelText))))));
    }

    private static java.net.http.HttpResponse<String> fakeHttpResponse(int status, String body) {
        return new java.net.http.HttpResponse<>() {
            @Override public int statusCode() { return status; }
            @Override public java.net.http.HttpRequest request() { return null; }
            @Override public java.util.Optional<java.net.http.HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
            @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true); }
            @Override public String body() { return body; }
            @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
            @Override public java.net.URI uri() { return java.net.URI.create("https://example.com"); }
            @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_1_1; }
        };
    }
}
