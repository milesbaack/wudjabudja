# WudjaBudja — Sprint Backlog (Sprints 1 & 2)

**Approach:** Scaffolding first. Everything runs locally (local PostgreSQL, local Spring Boot) and we connect the pieces we already built in the most minimal way possible, then expand from there. Auth, email invites, hosting and nice-to-have features move to later sprints.

*Story points are proposed and get confirmed at lab.*

---

## Team Point Distribution Summary

| Team Member | Role | Sprint 1 | Sprint 2 | Total | Core Responsibilities |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **Ethan** | AI Integrator | 11 | 5 | **16** | Data shape alignment (MVP-00), trip context aggregator, Gemini service in Spring Boot, group-aware Gemini. |
| **Miles** | UI & Data Architect | 7 | 8 | **15** | Quiz inputs and Save/Load/Generate actions, options display, shared trip UI, voting UI. |
| **Lukas** | Storage & Algorithms | 8 | 6 | **14** | Local PostgreSQL, saving preferences and Gemini options, vote storage, Borda Count tally. |
| **George** | Backend API Plumber | 6 | 6 | **12** | Spring Boot routes connecting the frontend, database and Gemini service, shared-user and voting routes. |
| **Total Points** | | **32** | **25** | **57** | |

---

## Sprint 1: Connect Everything

* **Dates:** Oct 27 to Nov 2, 2026
* **Sprint Goal:** One user takes the quiz, answers save to PostgreSQL, Gemini generates options, the options save, and JavaFX displays them. No manual JSON files.
* **Total Points:** 32 pts

### Sprint 1 Point Distribution
* **Ethan (11 pts):** MVP-00 (2), MVP-04 (3), MVP-05 (5), MVP-10 (1)
* **Lukas (8 pts):** MVP-01 (2), MVP-03 (3), MVP-07 (3)
* **Miles (7 pts):** MVP-08 (3), MVP-09 (3), MVP-10 (1)
* **George (6 pts):** MVP-02 (3), MVP-06 (3)

### Sprint 1 Task Breakdown

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **MVP-00** | Data Shape Alignment | Ethan | P0 | 2 | Fix name, type and allowed-value mismatches so every file matches `schema.sql` (see the MVP-00 doc). `trip_context.json`, `itinerary_schema.json` and `GeminiTest.java` updated. Open decisions signed off and logged in `docs/decisions-log.md`. |
| **MVP-01** | Local PostgreSQL Setup | Lukas | P0 | 2 | `schema.sql` runs on a local Postgres. Seed a test user (id 1) and trip (id 1), since the quiz hardcodes both. |
| **MVP-02** | Save/Load Preferences API | George | P0 | 3 | `POST /api/trips/{id}/preferences` and a matching GET. Request body matches `PreferenceRequest.java` (snake_case). Datasource points at local Postgres. MVP routes open in Spring Security so the quiz doesn't get a 401. |
| **MVP-03** | Preference Persistence | Lukas | P0 | 3 | Upserts into `user_preferences` (one row per trip and user). Saved preferences survive a restart. |
| **MVP-04** | Trip Context Aggregator | Ethan | P0 | 3 | Backend service reads `trips` and `user_preferences` and builds one trip context JSON shaped like `trip_context.json`. |
| **MVP-05** | Gemini Service | Ethan | P0 | 5 | Move the Gemini call from `GeminiTest.java` into a Spring Boot service. Key from the `GEMINI_API_KEY` env variable, never committed. Runs the existing output checks and returns options or a clear error. |
| **MVP-06** | Generate & Load Options API | George | P0 | 3 | Generate route calls Ethan's Gemini service, saves the result and returns it. `GET /api/trips/{id}/options` returns saved options grouped by day and time block. Clear success/error JSON. |
| **MVP-07** | Save Gemini Options | Lukas | P0 | 3 | Flattens Gemini's day, block, subgroup tree into `itinerary_options` rows and assigns option IDs (agreed with George). Options survive a restart. |
| **MVP-08** | Quiz Inputs + Save/Load | Miles | P0 | 3 | All quiz fields filled and buttons wired so a user can click through. Save and Load hit Spring Boot. A failed save shows as a failure. |
| **MVP-09** | Generate + Options Display | Miles | P0 | 3 | Generate button triggers the backend. Real Gemini options display by day and time block. Sorting and filtering stay local. |
| **MVP-10** | README Rewrite | Ethan, Miles | P1 | 2 | README matches the MVP sprints, new point totals and full backlog. 1 pt each. |

### Sprint 1 Checkpoints
* **Oct 28:** Data shape decisions signed off at lab.
* **Oct 27 – Oct 30:** JSON formats agreed, database and API connected, Gemini tested through the backend.
* **Nov 2:** **Demo: Single-user flow end to end.**

---

## Sprint 2: Shared Trips & Voting

* **Dates:** Nov 3 to Nov 9, 2026
* **Sprint Goal:** Two users join the same trip, save different preferences, generate group activity options, vote, and see the same winners.
* **Total Points:** 25 pts

### Sprint 2 Point Distribution
* **Miles (8 pts):** GRP-03 (3), VOTE-01 (5)
* **Lukas (6 pts):** VOTE-03 (3), VOTE-04 (3)
* **George (6 pts):** GRP-02 (3), VOTE-02 (3)
* **Ethan (5 pts):** GRP-01 (5)

### Sprint 2 Task Breakdown

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **GRP-01** | Group-Aware Gemini | Ethan | P0 | 5 | Combines 2+ users' preferences into one Gemini request. Tested with conflicting interests, budgets and subgroup splits. Returns valid options. |
| **GRP-02** | Trip Participation API | George | P0 | 3 | Adds users to a trip (`trip_participants`) and returns group preferences. Both JavaFX clients hit the same Spring Boot app and PostgreSQL data. |
| **GRP-03** | Shared Trip UI | Miles | P0 | 3 | Shows the users in a shared trip and their group activity options. |
| **VOTE-01** | Voting UI | Miles | P0 | 5 | Rank 1, 2, 3 interface for each time block. Shows the winners. |
| **VOTE-02** | Submit Votes API | George | P0 | 3 | `POST /api/trips/{id}/votes`. Two clients see consistent trip and vote data. |
| **VOTE-03** | Vote Storage & Validation | Lukas | P0 | 3 | Saves votes to `votes`. Blocks duplicate rankings and checks the voter is on the trip. |
| **VOTE-04** | Tally & Tie-Breaker | Lukas | P0 | 3 | Borda Count: 1st = 3 pts, 2nd = 2, 3rd = 1. Ties settle consistently (lowest `estimated_cost_usd`). Sets `is_final = true` on winners. |

### Sprint 2 Checkpoints
* **Nov 3 – Nov 6:** Shared-trip users and voting integration.
* **Nov 9:** **Demo: Two users vote and see the same winners.**

---

## Open Questions

* **Database Save Layer Ownership:** Who owns saving to the database? The sprint plan and the Oct 27 meeting put Lukas on storage with George exposing the routes. The MVP-00 doc lists George on the save layer. This backlog follows the sprint plan until confirmed at lab.
