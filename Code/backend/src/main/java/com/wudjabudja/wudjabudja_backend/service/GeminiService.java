package com.wudjabudja.wudjabudja_backend.service;

import com.wudjabudja.wudjabudja_backend.dto.Itinerary;
import com.wudjabudja.wudjabudja_backend.dto.TripBriefing;
import com.wudjabudja.wudjabudja_backend.dto.TripContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Sends a trip context to Gemini and returns checked activity options (MVP-05 / GRP-01 / MVP-12).
// Moved from Code/ai-pipeline/GeminiTest.java: callGemini, extractModelText, checkItinerary.
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions";
    private static final String DEFAULT_MODEL = "gemini-3.6-flash";   // decisions-log, Decision 3
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    static final int MAX_TRIES = 3;
    static final Duration RETRY_WAIT = Duration.ofSeconds(2);
    private static final Set<Integer> TEMPORARY_HTTP_CODES = Set.of(429, 500, 502, 503, 504);

    @FunctionalInterface
    interface GeminiTransport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final JsonMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final GeminiTransport transport;
    private final Sleeper sleeper;
    private final String apiKeyOverride;
    private final String systemPrompt;
    private final JsonNode schema;
    private final String briefingSystemPrompt;
    private final JsonNode briefingSchema;

    // Spring hands us its JSON mapper. The prompts and schemas are read once, when the app starts.
    // Copies of Code/ai-pipeline/prompts and schemas, keep them in sync.
    @Autowired
    public GeminiService(JsonMapper mapper) throws IOException {
        this(mapper, null, d -> Thread.sleep(d.toMillis()), null);
    }

    // Package-private constructor for unit testing auto-retry (MVP-12) without waiting or hitting the network.
    GeminiService(JsonMapper mapper, GeminiTransport transport, Sleeper sleeper, String apiKeyOverride) throws IOException {
        this.mapper = mapper;
        this.transport = transport != null
                ? transport
                : req -> http.send(req, HttpResponse.BodyHandlers.ofString());
        this.sleeper = sleeper != null ? sleeper : d -> Thread.sleep(d.toMillis());
        this.apiKeyOverride = apiKeyOverride;
        this.systemPrompt = new ClassPathResource("ai/itinerary_system.txt").getContentAsString(StandardCharsets.UTF_8);
        this.schema = mapper.readTree(new ClassPathResource("ai/itinerary_schema.json").getContentAsString(StandardCharsets.UTF_8));
        this.briefingSystemPrompt = new ClassPathResource("ai/briefing_system.txt").getContentAsString(StandardCharsets.UTF_8);
        this.briefingSchema = mapper.readTree(new ClassPathResource("ai/briefing_schema.json").getContentAsString(StandardCharsets.UTF_8));
    }

    // The one method other code calls: trip context in, checked itinerary out (or a clear error).
    public Itinerary generateItinerary(TripContext context) {
        String raw = callGemini(mapper.writeValueAsString(context));
        return parseAndCheck(raw, context);
    }

    // Generates and validates a pre-trip destination briefing (BRIEF-01).
    public TripBriefing generateBriefing(TripContext context) {
        String raw = callGeminiWithPrompt(mapper.writeValueAsString(context), briefingSystemPrompt, briefingSchema, "a trip briefing");
        return parseAndCheckBriefing(raw, context);
    }

    // Split out from generateBriefing so tests can feed in a saved answer without calling Gemini.
    TripBriefing parseAndCheckBriefing(String raw, TripContext context) {
        String modelText = extractModelText(raw);

        TripBriefing briefing;
        try {
            briefing = mapper.readValue(modelText, TripBriefing.class);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini's answer is not valid briefing JSON: " + e.getOriginalMessage());
        }

        List<String> problems = checkBriefing(context, briefing);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini's briefing failed " + problems.size() + " check(s): " + String.join("; ", problems));
        }
        return briefing;
    }

    List<String> checkBriefing(TripContext trip, TripBriefing out) {
        List<String> problems = new ArrayList<>();
        if (out == null) {
            return List.of("briefing output is empty");
        }
        if (orEmpty(out.customs()).isEmpty()) problems.add("customs list is empty");
        if (out.language() == null || orEmpty(out.language().phrases()).isEmpty()) problems.add("language phrases are missing");
        if (orEmpty(out.packingList()).isEmpty()) problems.add("packing_list is empty");
        if (orEmpty(out.historicalFacts()).isEmpty()) problems.add("historical_facts is empty");
        if (out.seasonAdvisory() == null || out.seasonAdvisory().summary() == null) problems.add("season_advisory is missing");

        Set<Long> expectedUsers = new HashSet<>();
        for (TripContext.Participant p : orEmpty(trip.participants())) {
            if (p.userId() != null) expectedUsers.add(p.userId());
        }
        Set<Long> gotUsers = new HashSet<>();
        for (TripBriefing.SouvenirIdea s : orEmpty(out.souvenirs())) {
            if (s.userId() != null) gotUsers.add(s.userId());
        }
        if (!expectedUsers.isEmpty() && !gotUsers.containsAll(expectedUsers)) {
            problems.add("souvenirs cover " + gotUsers + ", expected " + expectedUsers);
        }
        return problems;
    }

    // Split out from generateItinerary so tests can feed in a saved answer without calling Gemini.
    Itinerary parseAndCheck(String raw, TripContext context) {
        String modelText = extractModelText(raw);

        Itinerary itinerary;
        try {
            itinerary = mapper.readValue(modelText, Itinerary.class);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini's answer is not valid itinerary JSON: " + e.getOriginalMessage());
        }

        List<String> problems = checkItinerary(context, itinerary);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini's itinerary failed " + problems.size() + " check(s): " + String.join("; ", problems));
        }
        return itinerary;
    }

    // ---------- the API call (with MVP-12 auto-retry on temporary errors) ----------

    String callGemini(String tripContextJson) {
        return callGeminiWithPrompt(tripContextJson, systemPrompt, schema, "an itinerary");
    }

    String callGeminiWithPrompt(String tripContextJson, String instruction, JsonNode outputSchema, String label) {
        // Checked here, not at startup, so teammates without a key can still run the backend.
        String key = apiKeyOverride != null ? apiKeyOverride : System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "GEMINI_API_KEY is not set on the backend");
        }
        String model = System.getenv().getOrDefault("GEMINI_MODEL", DEFAULT_MODEL);

        // Request shape from Google's Interactions API quickstart (structured output section).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("system_instruction", instruction);
        body.put("input", tripContextJson);
        body.put("response_format", Map.of("type", "text", "mime_type", "application/json", "schema", outputSchema));

        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("x-goog-api-key", key)
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        for (int attempt = 1; attempt <= MAX_TRIES; attempt++) {
            log.info("Calling {} for {} (try {}/{})", model, label, attempt, MAX_TRIES);
            HttpResponse<String> response;
            try {
                response = transport.send(request);
            } catch (HttpTimeoutException e) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                        "Gemini took longer than " + TIMEOUT.toMinutes() + " minutes");
            } catch (IOException e) {
                if (attempt < MAX_TRIES) {
                    log.warn("Temporary error reaching Gemini on try {}/{}: {}. Retrying...",
                            attempt, MAX_TRIES, e.getMessage());
                    waitBeforeRetry();
                    continue;
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Gemini: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini call was interrupted");
            }

            if (response.statusCode() == 200) {
                return response.body();
            }

            if (TEMPORARY_HTTP_CODES.contains(response.statusCode()) && attempt < MAX_TRIES) {
                log.warn("Gemini returned temporary HTTP {} on try {}/{}: {}. Retrying...",
                        response.statusCode(), attempt, MAX_TRIES, shorten(response.body()));
                waitBeforeRetry();
                continue;
            }

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini returned HTTP " + response.statusCode() + ": " + shorten(response.body()));
        }

        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini failed after " + MAX_TRIES + " tries");
    }

    private void waitBeforeRetry() {
        try {
            sleeper.sleep(RETRY_WAIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini call was interrupted");
        }
    }

    // The answer lives at steps[type=model_output].content[type=text].text
    String extractModelText(String raw) {
        JsonNode interaction;
        try {
            interaction = mapper.readTree(raw);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini's response is not JSON");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode step : interaction.path("steps")) {
            if (!"model_output".equals(step.path("type").asString())) continue;
            for (JsonNode part : step.path("content")) {
                if ("text".equals(part.path("type").asString())) text.append(part.path("text").asString());
            }
        }
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini's response has no model_output text");
        }
        return text.toString();
    }

    // ---------- checks the schema can't enforce ----------

    List<String> checkItinerary(TripContext trip, Itinerary out) {
        List<String> problems = new ArrayList<>();
        Map<Long, TripContext.Participant> people = new LinkedHashMap<>();
        for (TripContext.Participant p : trip.participants()) people.put(p.userId(), p);
        LocalDate start = LocalDate.parse(trip.startDate());

        List<Itinerary.Day> days = orEmpty(out.days());
        if (days.size() != trip.numDays()) problems.add("expected " + trip.numDays() + " days, got " + days.size());

        for (int d = 0; d < days.size(); d++) {
            Itinerary.Day day = days.get(d);
            String expected = start.plusDays(d).toString();
            if (!expected.equals(day.date())) problems.add("day " + (d + 1) + " is " + day.date() + ", expected " + expected);

            int splitBlocks = 0;
            for (Itinerary.Block block : orEmpty(day.blocks())) {
                String where = day.date() + " " + block.timeBlock();
                List<Itinerary.Subgroup> subgroups = orEmpty(block.subgroups());
                if (subgroups.size() > 1) splitBlocks++;

                Set<Long> seen = new HashSet<>();
                for (Itinerary.Subgroup sub : subgroups) {
                    BigDecimal cap = null;   // lowest per_block_cap_usd in the subgroup, null if nobody set one
                    int latestWake = 0;
                    for (Long id : orEmpty(sub.subgroupParticipants())) {
                        if (!seen.add(id)) problems.add(where + ": " + id + " is in more than one subgroup");
                        TripContext.Participant person = people.get(id);
                        if (person == null) { problems.add(where + ": unknown participant " + id); continue; }
                        // per_block_cap_usd is optional in user_preferences, so skip people without one
                        BigDecimal personCap = person.perBlockCapUsd();
                        if (personCap != null && (cap == null || personCap.compareTo(cap) < 0)) cap = personCap;
                        latestWake = Math.max(latestWake, minutes(person.wakeTime()));
                    }
                    for (Itinerary.Option opt : orEmpty(sub.options())) {
                        String name = where + " \"" + opt.title() + "\"";
                        BigDecimal cost = opt.estimatedCostUsd();
                        if (cap != null && cost != null && cost.compareTo(cap) > 0) {
                            problems.add(name + " costs $" + cost + ", cap is $" + cap);
                        }
                        if (opt.startTime() == null || !opt.startTime().matches("\\d{2}:\\d{2}.*")) {
                            problems.add(name + " has no valid start_time");
                        } else if (minutes(opt.startTime()) < latestWake + 60) {
                            problems.add(name + " starts " + opt.startTime() + ", before latest wake time + 1 hour");
                        }
                    }
                }
                if (!seen.equals(people.keySet())) problems.add(where + ": subgroups cover " + seen + ", expected " + people.keySet());
            }
            if (splitBlocks > 1) problems.add(day.date() + " has " + splitBlocks + " split blocks, max is 1");
        }
        return problems;
    }

    // "07:30" or "07:30:00" -> minutes after midnight
    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String shorten(String text) {
        return text == null || text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
