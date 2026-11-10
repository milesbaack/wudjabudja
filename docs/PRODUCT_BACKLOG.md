# WudjaBudja — Master Product Backlog

**Project Purpose:** A unified, AI-powered travel planning tool designed to optimize group trips based on combined interests and budgets, eliminating the stress of designated "group planners."

**Architecture Summary:**
* **Frontend:** JavaFX (Single-window `BorderPane` with persistent sidebar navigation; Java 11 `HttpClient` + Jackson).
* **Backend:** Java Spring Boot REST API (`http://localhost:8080`).
* **Database:** PostgreSQL (Local instance for MVP Sprints 1–3; Supabase/Neon cloud hosting in Sprint 7).
* **Security:** Spring Security (Permit-all on MVP routes for Sprints 1–3; BCrypt + JWT authentication in Sprint 4).
* **AI Service:** Google Gemini Flash API (`gemini-3.6-flash`).

---

## 1. Development Strategy & MVP Scaffolding

The team operates on a **Scaffolding First / Local-First** execution philosophy:
1. Sprints 1 to 3 focus strictly on connecting the core end-to-end loop: Preference Intake → Backend Aggregation → Gemini Generation → Database Persistence → JavaFX Display → Borda Count Voting.
2. All components run locally on `localhost:8080` and local PostgreSQL, with seeded initial entities (`user_id = 1`, `trip_id = 1`) to eliminate blockers from uncompleted authentication or cloud services.
3. Authentication, cloud hosting, email dispatch, and supplementary AI modules are deferred to Sprints 4–7. At the Nov 9 scrum meeting, login/auth, Postgres cloud sync and travel mode (car/plane/train) were deferred, so each later sprint moved back one.

---

## 2. Feature Assessment Matrix

| Feature | Category | Target Sprint | Description |
| :--- | :--- | :---: | :--- |
| **Granular Preference Intake** | Must-Have (MVP) | Sprint 1 | Wake-up times, max radius, categorical budgets, and activity tags. |
| **Trip Context Aggregator** | Must-Have (MVP) | Sprint 1 | Consolidates user profiles and constraints into a single Gemini prompt payload. |
| **Gemini Itinerary Engine** | Must-Have (MVP) | Sprint 1 | Generates 3 options per time block (morning, afternoon, evening) with subgroup split logic. |
| **Itinerary Display** | Must-Have (MVP) | Sprint 1 | JavaFX UI displaying generated options with local filtering/sorting. |
| **Shared Trips** | Must-Have (MVP) | Sprint 3 | Multi-user support on a single `trip_id` with conflicting preference resolution. |
| **Democratic Voting (Borda Count)** | Must-Have (MVP) | Sprint 3 | Ranked-choice voting (1st=3pts, 2nd=2pts, 3rd=1pt) with cost-based tie breaking. |
| **JWT Authentication** | Deferred Core | Sprint 4 | User registration, login, BCrypt password hashing, and token sessions. |
| **Email Invitations & Join Codes** | Deferred Core | Sprint 4 | SMTP email delivery and alphanumeric join codes for inviting travel companions. |
| **Trip Briefing Module** | Deferred Core | Sprint 5 | Secondary AI engine for destination packing lists, cultural customs, and souvenirs. |
| **Live Weather Integration** | Deferred Core | Sprint 5 | OpenWeatherMap API for weather-aware "Perfect Days" scheduling. |
| **UI Polish & Animations** | Deferred Polish | Sprint 6 | View transitions, toast error banners, and loading indicators. |
| **Cloud Hosting & Production DB** | Deferred Infra | Sprint 7 | Supabase/Neon cloud PostgreSQL and containerized Spring Boot hosting, including Postgres cloud sync. |
| **Transit / Live Pricing / Calendar** | Nice-to-Have | Post-MVP | Google Maps transit routing, Skyscanner pricing, and `.ics` export. |

---

## 3. Product Backlog by Sprint

### Sprint 1: Connect Everything (Done)
* **Goal:** One user takes the quiz, answers save to PostgreSQL, Gemini generates options, the options save, and JavaFX displays them. No manual JSON files.
* **Dates:** Oct 27 to Nov 2, 2026 | **Points:** 32 pts

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

---

### Sprint 2: Finish the End-to-End Flow (Done; planned: Shared Trips & Voting)
* **Planned Goal:** Two users join the same trip, save different preferences, generate group activity options, vote, and see the same winners.
* **What shipped:** The Sprint 1 carryover plus trip creation (MVP-11). One traveler can now plan a trip, take the quiz, generate a Gemini itinerary and reopen it later (merged to `main` in PR #6). The shared-trip and voting tasks moved to Sprint 3.
* **Dates:** Nov 3 to Nov 9, 2026 | **Points:** 3 pts new (MVP-11). Carryover points were counted in Sprint 1.

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

---

### Sprint 3: Group Trips & Voting (Active MVP)
* **Goal:** Group trips and voting. Move from one traveler to a whole group.
* **Dates:** Nov 10 to Nov 23, 2026 | **Points:** 23 pts
* **Testing approach:** One login on one laptop. The trip creator adds travelers by email and enters each person's quiz answers and votes to simulate the group.
* **Storage:** Build against the current backend (Spring Boot + local Postgres). The JSON storage swap is tracked in LOCAL-01.
* **Deferred:** Login/auth, Postgres cloud sync, travel mode (car/plane/train).

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

---

### Sprint 4: Authentication & Group Coordination (Deferred)
* **Goal:** Introduce production-grade security, persistent accounts, and invitation mechanics.
* **Estimated Points:** 24 pts

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **AUTH-01** | Register API | Lukas | P0 | 5 | `POST /api/auth/register`. Hash passwords using BCrypt. Validate unique email. |
| **AUTH-02** | Login API | Lukas | P0 | 5 | `POST /api/auth/login`. Verify credentials, generate 24-hour JWT. |
| **AUTH-03** | JavaFX Auth Manager | Ethan / Lukas | P0 | 3 | Singleton storing JWT in memory and attaching `Bearer` header to downstream calls. |
| **AUTH-04** | Login/Reg Views | Miles | P0 | 5 | FXML views with client-side validation (matching passwords, email regex). |
| **TRIP-01** | Create Trip API | George | P0 | 3 | `POST /api/trips`. Generates unique `join_code`. |
| **TRIP-02** | Email Invite Engine | George | P1 | 5 | SMTP email dispatch sending HTML emails with `join_code`. |
| **TRIP-03** | Join Trip Flow | George | P1 | 3 | `POST /api/trips/join`. Maps authenticated user to `trip_participants`. |

---

### Sprint 5: Trip Briefing & Context Expansion (Deferred)
* **Goal:** Expand AI capabilities beyond scheduling into comprehensive destination preparation and weather awareness.
* **Estimated Points:** 23 pts

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **BRIEF-01** | Briefing AI Engine | Ethan | P1 | 5 | Secondary Gemini prompt generating customs, language phrases, packing lists, and souvenirs. |
| **BRIEF-02** | Briefing APIs | George | P1 | 3 | `GET /api/trips/{id}/briefing` serving structured JSON briefing. |
| **BRIEF-03** | Briefing UI Dashboard | Miles | P1 | 5 | JavaFX tab displaying cultural norms, facts, and categorized packing checklists. |
| **WEATHER-01** | Live Weather Integration | Ethan / George | P2 | 5 | OpenWeatherMap integration to align candidate activities with forecasts. |
| **FINAL-01** | Final Itinerary Presentation | Ethan | P1 | 5 | Chronological timeline rendering the consensus "Perfect Days" with budget breakdown. |

---

### Sprint 6: UI Polish & Resilience (Deferred)
* **Goal:** Elevate user experience with smooth navigation, error feedback, and retry resilience.
* **Estimated Points:** 18 pts

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **AI-04** | LLM Error & Retry Handling | Lukas | P1 | 5 | Catches `JsonParseException` or malformed LLM outputs, auto-retries twice before erroring. |
| **UI-01** | Global Toast & Alert System | Miles | P1 | 5 | Non-blocking alert banners for network failures, validation errors, and async progress. |
| **UI-02** | Responsive Layout & Theming | Miles | P2 | 5 | CSS clean-up, responsive window resizing, and consistent spacing across all views. |
| **API-ERR** | API Error Handling Polish | George | P1 | 3 | Consistent RFC 7807 error responses across all controllers. |

---

### Sprint 7+: Production Deployment & Extended Epics (Deferred)
* **Cloud Database:** Migrate local PostgreSQL to Supabase or Neon.
* **Containerization:** Docker container setup for Spring Boot backend.
* **Calendar Sync (.ics):** Downloadable calendar files for winning itineraries.
* **Expense Tracker:** Post-trip cost splitting and debt calculation.
