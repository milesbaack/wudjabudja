# WudjaBudja — Sprint Backlog (Sprints 1 to 3)

**Approach:** Scaffolding first. Everything runs locally (local PostgreSQL, local Spring Boot) and we connect the pieces we already built in the most minimal way possible, then expand from there. Auth, email invites, hosting and nice-to-have features move to later sprints.

*Story points are proposed and get confirmed at lab.*

---

## Team Point Distribution Summary

| Team Member | Role | Sprint 1 | Sprint 2 | Sprint 3 | Total | Core Responsibilities |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **Ethan** | AI Integrator | 11 | 3 | 5 | **19** | Data shape alignment (MVP-00), trip context aggregator, Gemini service in Spring Boot, group-aware Gemini, Gemini auto-retry. |
| **Miles** | UI & Data Architect | 7 | 0 | 6 | **13** | Quiz inputs and Save/Load/Generate actions, options display, shared trip screen, voting screen. |
| **Lukas** | Storage & Algorithms | 8 | 0 | 6 | **14** | Local PostgreSQL, saving preferences and Gemini options, vote storage, Borda Count tally, JSON storage. |
| **George** | Backend API Plumber | 6 | 0 | 6 | **12** | Spring Boot routes connecting the frontend, database and Gemini service, add-traveler and voting routes. |
| **Total Points** | | **32** | **3** | **23** | **58** | |

*Sprint 2's only new points are MVP-11 (Ethan, 3 pts). The Sprint 1 carryover finished in Sprint 2 stays counted in Sprint 1 under the original owners.*

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

## Sprint 2: Finish the End-to-End Flow (planned: Shared Trips & Voting)

* **Dates:** Nov 3 to Nov 9, 2026
* **Planned Goal:** Two users join the same trip, save different preferences, generate group activity options, vote, and see the same winners.
* **What shipped:** The Sprint 1 carryover plus trip creation (MVP-11). One traveler can now plan a trip, take the quiz, generate a Gemini itinerary and reopen it later (merged to `main` in PR #6). The shared-trip and voting tasks moved to Sprint 3.
* **Total Points:** 3 pts new (MVP-11). Carryover points were counted in Sprint 1.

### Sprint 2 Task Status

| ID | Task | Assignee | Priority | Pts | Status | Acceptance Criteria / What Shipped |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **MVP-01** | Local PostgreSQL Setup | Lukas | P0 | 2 (S1) | Done | `seed.sql` adds demo user 1. Schema and seed run on a local Postgres. |
| **MVP-02** | Save/Load Preferences API | George | P0 | 3 (S1) | Done | Save route checks the trip exists. `GET /api/trips/{id}/preferences?userId=` loads answers (`404` if none). Errors come back as JSON with a `message`. |
| **MVP-06** | Generate & Load Options API | George | P0 | 3 (S1) | Done | `POST /api/trips/{id}/itinerary` generates and saves. `GET /api/trips/{id}/itinerary` loads the saved itinerary. Uses `/itinerary` instead of `/options`. |
| **MVP-07** | Save Gemini Options | Lukas | P0 | 3 (S1) | Done | Gemini's tree is saved as `itinerary_options` rows and rebuilt on load. A database test checks the round trip. |
| **MVP-08** | Quiz Inputs + Save/Load | Miles | P0 | 3 (S1) | Done | Quiz saves and loads for the current trip. A failed save or load shows the server's error. |
| **MVP-09** | Generate + Options Display | Miles | P0 | 3 (S1) | Done | Itinerary screen with Generate and Regenerate. Options show by day and time block. |
| **MVP-10** | README Rewrite | Ethan, Miles | P1 | 2 (S1) | Done | README "Run it locally" section with setup, click path, API routes and gotchas. |
| **MVP-11** | Trip Setup Screen + Trips API | Ethan | P0 | 3 | Done | `POST /api/trips`, `GET /api/trips` and `GET /api/trips/{id}`. Plan a Trip screen creates a trip and starts the quiz. "Your trips" reopens an earlier trip. |
| **LOCAL-01** | JSON Storage | Lukas | P1 | TBD | Carried over | Lukas's JSON models are on `main` in `com.wudjabudja.storage` (`JsonTrip`, `JsonTripPreferences`, `JsonProjectData`). The real storage code isn't pushed yet. |
| **GRP-01** | Group-Aware Gemini | Ethan | P0 | 5 | Moved to Sprint 3 | See Sprint 3. |
| **GRP-02** | Trip Participation API | George | P0 | 3 | Moved to Sprint 3 | See Sprint 3. |
| **GRP-03** | Shared Trip UI | Miles | P0 | 3 | Moved to Sprint 3 | See Sprint 3. |
| **VOTE-01** | Voting UI | Miles | P0 | 5 | Moved to Sprint 3 | See Sprint 3. |
| **VOTE-02** | Submit Votes API | George | P0 | 3 | Moved to Sprint 3 | See Sprint 3. |
| **VOTE-03** | Vote Storage & Validation | Lukas | P0 | 3 | Moved to Sprint 3 | See Sprint 3. |
| **VOTE-04** | Tally & Tie-Breaker | Lukas | P0 | 3 | Moved to Sprint 3 | See Sprint 3. |

### Sprint 2 Checkpoints
* **Nov 3 – Nov 9:** Sprint 1 carryover finished and Trip Setup (MVP-11) added.
* **Nov 9:** Scrum meeting. Sprint 3 planned. The two-user voting demo moved to Sprint 3.

---

## Sprint 3: Group Trips & Voting

* **Dates:** Nov 10 to Nov 23, 2026
* **Sprint Goal:** Group trips and voting. Move from one traveler to a whole group.
* **Total Points:** 23 pts
* **Testing approach:** One login on one laptop. The trip creator adds travelers by email and enters each person's quiz answers and votes to simulate the group.
* **Storage:** Build against the current backend (Spring Boot + local Postgres). The JSON storage swap is tracked in LOCAL-01.
* **Deferred:** Login/auth, Postgres cloud sync, travel mode (car/plane/train).

### Sprint 3 Point Distribution
* **George (6 pts):** GRP-02 (3), VOTE-02 (3)
* **Miles (6 pts):** GRP-03 (3), VOTE-01 (3)
* **Lukas (6 pts):** VOTE-03 (2), VOTE-04 (3), LOCAL-01 (1)
* **Ethan (5 pts):** GRP-01 (3), MVP-12 (2)

### Sprint 3 Task Breakdown

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **GRP-01** | Group-Aware Gemini Prompt | Ethan | P0 | 3 | Trip context includes every traveler on the trip and their preferences. One Gemini request plans for 2+ travelers with conflicting interests and budgets, splitting into subgroups when needed. Every traveler is in exactly one subgroup per time block and the existing output checks pass. Tested with at least 2 travelers whose interests conflict. |
| **MVP-12** | Gemini Auto-Retry | Ethan | P1 | 2 | Temporary Gemini errors (like 503 "high demand") retry automatically with a short wait, up to 3 tries, before the user sees an error. Failed output checks still show the error and Regenerate (no auto-retry). Tests cover retry-then-success and giving up after the last try. |
| **GRP-02** | Add Travelers API | George | P0 | 3 | `POST /api/trips/{id}/participants` with an email adds that traveler to `trip_participants` as a `member`, creating the `users` row if the email is new. Adding the same email twice returns a clear error. `GET /api/trips/{id}/participants` lists travelers with their role and whether their quiz is saved. |
| **GRP-03** | Shared Trip Screen | Miles | P0 | 3 | Shows who's on the trip and the group's options. The trip creator adds travelers by email and picks which traveler they're entering quiz answers for. |
| **VOTE-01** | Voting Screen | Miles | P0 | 3 | Rank 1, 2, 3 for each time block. The trip creator picks which traveler is voting. Shows the winners once votes are in. |
| **VOTE-02** | Submit Votes API | George | P0 | 3 | `POST /api/trips/{id}/votes` takes one traveler's rankings. A matching GET returns each time block's winners. Errors come back as JSON with a `message`. |
| **VOTE-03** | Save Votes & Block Duplicates | Lukas | P0 | 2 | Saves rankings to `votes`. A traveler can't rank the same option twice or give two options the same rank in one time block. Rejects voters who aren't on the trip. |
| **VOTE-04** | Borda Tally & Tie-Breaker | Lukas | P0 | 3 | Borda Count per time block: 1st = 3 pts, 2nd = 2, 3rd = 1. The highest total wins, so the group gets the least-unfavorable option. Ties go to the cheaper option (lowest `estimated_cost_usd`). Sets `is_final = true` on winners. |
| **LOCAL-01** | Push JSON Storage Code | Lukas | P1 | 1 | Carryover from Sprint 2. Push the real JSON storage code to its own branch for review, building on `com.wudjabudja.storage`. The app keeps using the current backend this sprint. |

### Sprint 3 Checkpoints
* **Nov 23:** **Demo: a group trip on one laptop.** The creator adds travelers by email, enters everyone's quiz and votes, and every traveler sees the same winners.

---

## Open Questions

* **Database Save Layer Ownership:** Who owns saving to the database? The sprint plan and the Oct 27 meeting put Lukas on storage with George exposing the routes. The MVP-00 doc lists George on the save layer. This backlog follows the sprint plan until confirmed at lab.
