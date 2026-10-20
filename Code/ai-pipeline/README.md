# AI Pipeline (Gemini)

Sprint 1, INFRA-05 (Ethan). The prompts, output schemas and a test runner for the two Gemini calls.

| File | What it is |
| --- | --- |
| `prompts/itinerary_system.txt` | System prompt for the Itinerary Engine (AI-02) |
| `schemas/itinerary_schema.json` | JSON shape Gemini must return for itineraries: 3 blocks a day, 3 options per subgroup |
| `prompts/briefing_system.txt` | System prompt for the Trip Briefing (BRIEF-01) |
| `schemas/briefing_schema.json` | JSON shape for the briefing: customs, language, packing, facts, season, souvenirs |
| `samples/trip_context.json` | Example of what the Constraint Aggregator (AI-01) will build from the database |
| `GeminiTest.java` | Sends a prompt to Gemini, saves the answer, and runs the checks the schema can't enforce |

## How it flows

1. The aggregator turns the `trips` and `user_preferences` rows into one trip context JSON (see the sample).
2. The backend sends that JSON to Gemini with a system prompt and a schema.
3. Gemini returns JSON in exactly that schema.
4. Java runs the extra checks (dates, everyone in one subgroup per block, cost caps, wake times). A failed check triggers a retry (AI-04).
5. Itinerary options go to `itinerary_options` for voting. The briefing goes in a JSON column on `trips`.

## Running the test

Needs Java 11 or newer and a Gemini API key from Google AI Studio.

```bash
cd Code/ai-pipeline

# Mac / Linux
export GEMINI_API_KEY=your-key-here

# Windows PowerShell
$env:GEMINI_API_KEY="your-key-here"

java GeminiTest.java itinerary
java GeminiTest.java briefing
```

Results land in `output/` (ignored by git): `*_raw.json` is the full API response, `*_output.json` is Gemini's answer.

To re-run the checks on a saved answer without spending another API call:

```bash
java GeminiTest.java itinerary --from-file output/itinerary_raw.json
```

Optional: `GEMINI_MODEL=gemini-3.5-flash-lite` to try the cheaper model. Default is `gemini-3.8-flash`.

## API key rules

- Never commit the key or paste it into any doc. This repo is public.
- Only the Spring Boot backend calls Gemini. The JavaFX app never holds the key.

## Still open

- CSV vs JSON input (this assumes JSON)
- Three time blocks a day (morning, afternoon, evening)
- Budget rule: lowest member's cap wins
- Budget category names and `trip_type` values depend on the final `schema.sql`

`GeminiTest.java` is a throwaway harness with its own tiny JSON parser so it runs with no setup. The real client in Sprint 5 uses Jackson inside Spring Boot.
