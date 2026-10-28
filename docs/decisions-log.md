# WudjaBudja — Architectural & Data Decisions Log

This document records the architectural and data-shape decisions agreed upon by the team, starting from the Oct 27 team meeting and the Oct 28 lab sign-off.

---

## Decision 1: MVP Scaffolding & Local-First Execution (Oct 27)

* **Decision:** Sprints 1 and 2 prioritize connecting the core end-to-end loop: Preference Intake → Aggregation → Gemini Service → PostgreSQL Persistence → JavaFX Display → Borda Count Voting.
* **Execution Details:**
  * All services run locally: Local PostgreSQL and local Spring Boot (`localhost:8080`).
  * Cloud hosting (Supabase/Neon), email dispatch (SMTP), and authentication (JWT/BCrypt) are deferred to Sprints 3+.
  * Seeded entities are used in `schema.sql`: A test user (`id = 1`) and test trip (`id = 1`) are seeded so the JavaFX quiz can immediately save and load without login friction.
  * Spring Security opens MVP routes (`/api/trips/**`) so the frontend doesn't encounter 401 Unauthorized errors during development.

---

## Decision 2: Data Shape Alignment (MVP-00) (Oct 28 Lab Sign-Off)

To resolve discrepancies between `schema.sql`, frontend DTOs (`PreferenceRequest.java`), and AI pipeline files (`trip_context.json`, `itinerary_schema.json`, `GeminiTest.java`), the team aligned on the following schema standards:

| Entity / Property | Previous Discrepancy | Agreed Standard | Rational / Details |
| :--- | :--- | :--- | :--- |
| **Destination** | Object `{ "city": "...", "sub_city": "..." }` | Single String: `"San Diego"` | Matches `trips.destination VARCHAR(100)`. Max radius is measured from destination center. |
| **Trip Budget** | `global_budget_usd` | `total_budget` | Matches `trips.total_budget NUMERIC(10, 2)`. |
| **Participant ID** | String `"p1"`, `"p2"` | Numeric `user_id` (`1`, `2`) | Matches PostgreSQL `BIGINT` foreign keys across `users` and `trip_participants`. |
| **Participant Role** | `"key"` / `"secondary"` | `"leader"` / `"member"` | Matches `trip_participants.role VARCHAR(20)`. |
| **Time Block Field** | `"block"` | `"time_block"` | Values strictly enum: `["morning", "afternoon", "evening"]`. |
| **Subgroup Members** | `"participant_ids"` | `"subgroup_participants"` | Array of numeric `user_id` integers. |
| **Activity Costs** | `estimated_cost` | `estimated_cost_usd` | Floating point/numeric per-person cost. |
| **Activity Interests** | Open text / `pop_culture` | Controlled vocabulary | Aligned to quiz checkboxes: `["sports", "outdoors", "nightlife", "food", "tour", "relaxation", "shopping"]`. Swapped `pop_culture` for `sports` and `tour`. |
| **Budget Categories** | Varied keys | Standard map | JSON object with keys: `"lodging"`, `"transit"`, `"food"`, `"activities"`. |

---

## Decision 3: AI Model Selection (Oct 28)

* **Decision:** Default model set to `gemini-3.6-flash`.
* **Reason:** In initial test harness benchmarks, `gemini-3.7` and `gemini-3.8` frequently hit free-tier rate and quota limits. `gemini-3.6-flash` reliably generates valid structured JSON complying with `itinerary_schema.json` without quota exhaustion.
* **Security Rule:** `GEMINI_API_KEY` is loaded strictly via environment variable into the Spring Boot backend and must never be committed to source control or exposed to the client.

---

## Decision 4: Open Question — Database Save Layer Ownership

* **Status:** Under Review (Target Confirmation: Lab Oct 28).
* **Context:**
  * The Sprint Plan and Oct 27 meeting assigned **Lukas** to database storage (`MVP-03` Preference Persistence, `MVP-07` Save Gemini Options) with **George** exposing the REST API controllers (`MVP-02`, `MVP-06`).
  * The earlier `MVP-00` draft listed George on the database save layer.
* **Working Agreement:** The project adheres to the Sprint Plan division of labor:
  * **Lukas (Storage & Algorithms):** Repositories, JPA entities, database upsert queries, and vote tally algorithms.
  * **George (Backend API Plumber):** Spring Boot REST controllers, request mapping, route security configuration, and service wiring.
