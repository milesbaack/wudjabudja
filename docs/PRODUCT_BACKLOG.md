# WudjaBudja — Master Product Backlog

**Project Purpose:** A unified, AI-powered travel planning tool designed to optimize group trips based on combined interests and budgets, eliminating the stress of designated "group planners."

**Architecture Summary:**
* **Frontend:** JavaFX (Single-window `BorderPane` with persistent sidebar navigation; Java 11 `HttpClient` + Jackson).
* **Backend:** Java Spring Boot REST API (`http://localhost:8080`).
* **Database:** PostgreSQL (Local instance for MVP Sprints 1–2; Supabase/Neon cloud hosting in Sprint 6).
* **Security:** Spring Security (Permit-all on MVP routes for Sprints 1–2; BCrypt + JWT authentication in Sprint 3).
* **AI Service:** Google Gemini Flash API (`gemini-3.6-flash`).

---

## 1. Development Strategy & MVP Scaffolding

The team operates on a **Scaffolding First / Local-First** execution philosophy:
1. Sprints 1 & 2 focus strictly on connecting the core end-to-end loop: Preference Intake → Backend Aggregation → Gemini Generation → Database Persistence → JavaFX Display → Borda Count Voting.
2. All components run locally on `localhost:8080` and local PostgreSQL, with seeded initial entities (`user_id = 1`, `trip_id = 1`) to eliminate blockers from uncompleted authentication or cloud services.
3. Authentication, cloud hosting, email dispatch, and supplementary AI modules are deferred to Sprints 3–6.

---

## 2. Feature Assessment Matrix

| Feature | Category | Target Sprint | Description |
| :--- | :--- | :---: | :--- |
| **Granular Preference Intake** | Must-Have (MVP) | Sprint 1 | Wake-up times, max radius, categorical budgets, and activity tags. |
| **Trip Context Aggregator** | Must-Have (MVP) | Sprint 1 | Consolidates user profiles and constraints into a single Gemini prompt payload. |
| **Gemini Itinerary Engine** | Must-Have (MVP) | Sprint 1 | Generates 3 options per time block (morning, afternoon, evening) with subgroup split logic. |
| **Itinerary Display** | Must-Have (MVP) | Sprint 1 | JavaFX UI displaying generated options with local filtering/sorting. |
| **Shared Trips** | Must-Have (MVP) | Sprint 2 | Multi-user support on a single `trip_id` with conflicting preference resolution. |
| **Democratic Voting (Borda Count)** | Must-Have (MVP) | Sprint 2 | Ranked-choice voting (1st=3pts, 2nd=2pts, 3rd=1pt) with cost-based tie breaking. |
| **JWT Authentication** | Deferred Core | Sprint 3 | User registration, login, BCrypt password hashing, and token sessions. |
| **Email Invitations & Join Codes** | Deferred Core | Sprint 3 | SMTP email delivery and alphanumeric join codes for inviting travel companions. |
| **Trip Briefing Module** | Deferred Core | Sprint 4 | Secondary AI engine for destination packing lists, cultural customs, and souvenirs. |
| **Live Weather Integration** | Deferred Core | Sprint 4 | OpenWeatherMap API for weather-aware "Perfect Days" scheduling. |
| **UI Polish & Animations** | Deferred Polish | Sprint 5 | View transitions, toast error banners, and loading indicators. |
| **Cloud Hosting & Production DB** | Deferred Infra | Sprint 6 | Supabase/Neon cloud PostgreSQL and containerized Spring Boot hosting. |
| **Transit / Live Pricing / Calendar** | Nice-to-Have | Post-MVP | Google Maps transit routing, Skyscanner pricing, and `.ics` export. |

---

## 3. Product Backlog by Sprint

### Sprint 1: Connect Everything (Active MVP)
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

### Sprint 2: Shared Trips & Voting (Active MVP)
* **Goal:** Two users join the same trip, save different preferences, generate group activity options, vote, and see the same winners.
* **Dates:** Nov 3 to Nov 9, 2026 | **Points:** 25 pts

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **GRP-01** | Group-Aware Gemini | Ethan | P0 | 5 | Combines 2+ users' preferences into one Gemini request. Tested with conflicting interests, budgets and subgroup splits. Returns valid options. |
| **GRP-02** | Trip Participation API | George | P0 | 3 | Adds users to a trip (`trip_participants`) and returns group preferences. Both JavaFX clients hit the same Spring Boot app and PostgreSQL data. |
| **GRP-03** | Shared Trip UI | Miles | P0 | 3 | Shows the users in a shared trip and their group activity options. |
| **VOTE-01** | Voting UI | Miles | P0 | 5 | Rank 1, 2, 3 interface for each time block. Shows the winners. |
| **VOTE-02** | Submit Votes API | George | P0 | 3 | `POST /api/trips/{id}/votes`. Two clients see consistent trip and vote data. |
| **VOTE-03** | Vote Storage & Validation | Lukas | P0 | 3 | Saves votes to `votes`. Blocks duplicate rankings and checks the voter is on the trip. |
| **VOTE-04** | Tally & Tie-Breaker | Lukas | P0 | 3 | Borda Count: 1st = 3 pts, 2nd = 2, 3rd = 1. Ties settle consistently (lowest `estimated_cost_usd`). Sets `is_final = true` on winners. |

---

### Sprint 3: Authentication & Group Coordination (Deferred)
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

### Sprint 4: Trip Briefing & Context Expansion (Deferred)
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

### Sprint 5: UI Polish & Resilience (Deferred)
* **Goal:** Elevate user experience with smooth navigation, error feedback, and retry resilience.
* **Estimated Points:** 18 pts

| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :---: | :---: | :--- |
| **AI-04** | LLM Error & Retry Handling | Lukas | P1 | 5 | Catches `JsonParseException` or malformed LLM outputs, auto-retries twice before erroring. |
| **UI-01** | Global Toast & Alert System | Miles | P1 | 5 | Non-blocking alert banners for network failures, validation errors, and async progress. |
| **UI-02** | Responsive Layout & Theming | Miles | P2 | 5 | CSS clean-up, responsive window resizing, and consistent spacing across all views. |
| **API-ERR** | API Error Handling Polish | George | P1 | 3 | Consistent RFC 7807 error responses across all controllers. |

---

### Sprint 6+: Production Deployment & Extended Epics (Deferred)
* **Cloud Database:** Migrate local PostgreSQL to Supabase or Neon.
* **Containerization:** Docker container setup for Spring Boot backend.
* **Calendar Sync (.ics):** Downloadable calendar files for winning itineraries.
* **Expense Tracker:** Post-trip cost splitting and debt calculation.
