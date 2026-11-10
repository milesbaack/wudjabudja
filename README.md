# WudjaBudja — Product Backlog & Team Specification

**Project Purpose:** A unified, AI-powered travel planning tool designed to optimize group trips based on combined interests and budgets, eliminating the stress of designated "group planners."

**Architecture Summary:**
* **Frontend:** JavaFX (Single-window `BorderPane` with persistent sidebar navigation, targeting Java 11 `HttpClient` + Jackson).
* **Backend:** Java Spring Boot REST API (`http://localhost:8080`).
* **Database:** PostgreSQL (Local PostgreSQL instance for MVP Sprints 1–2; cloud hosting via Supabase/Neon deferred).
* **Security:** Spring Security (Permit-all on MVP routes for Sprints 1–2; BCrypt & JWT authentication deferred to Sprint 3).
* **AI Service:** Google Gemini Flash API (`gemini-3.6-flash`, called exclusively by Spring Boot backend via `GEMINI_API_KEY`).

---

## Run it locally (Mac)

The Sprint 1 flow: log in with anything → **Plan a Trip** (destination, dates, trip type, budget) → preferences quiz → **Generate** → Gemini's itinerary on screen. Trips, quiz answers and itineraries live in Postgres, so they survive restarts.

### Prerequisites

| Tool | Install | Check |
| :--- | :--- | :--- |
| Homebrew | [brew.sh](https://brew.sh) | `brew --version` |
| JDK 17 or newer | `brew install --cask temurin` | `java -version` |
| Maven (frontend only, the backend has its own wrapper) | `brew install maven` | `mvn -v` |
| PostgreSQL | Option A or B below | `psql --version` |
| Gemini API key | [Google AI Studio](https://aistudio.google.com/apikey) | |

**Postgres option A: Homebrew.** User is your Mac username, no password.
```sh
brew install postgresql@17
brew services start postgresql@17
# postgresql@17 is "keg-only", so its tools aren't on your PATH yet:
echo 'export PATH="$(brew --prefix postgresql@17)/bin:$PATH"' >> ~/.zshrc
```
Then open a new terminal tab.

**Postgres option B: EDB installer** (from postgresql.org). User is `postgres`, with the password you picked during install. Add this to `~/.zshrc` (change `18` to your version), then open a new tab:
```sh
export PATH="/Library/PostgreSQL/18/bin:$PATH"
export PGHOST=localhost
export PGUSER=postgres
```
To skip the password prompt, put `localhost:5432:*:postgres:YOUR_PASSWORD` in `~/.pgpass` and run `chmod 600 ~/.pgpass`.

Only run one Postgres server at a time. Both want port 5432.

### One-time database setup (from the repo root)
```sh
createdb wudjabudja
psql -d wudjabudja -f Code/database/schema.sql   # the 6 tables
psql -d wudjabudja -f Code/database/seed.sql     # demo user 1 (safe to run again)
psql -d wudjabudja -c "SELECT id, email FROM users;"   # expect: 1 | demo@wudjabudja.local
```
To start over from empty: `dropdb wudjabudja`, then run the three commands again. This deletes every trip.

### Point the backend at your database
Create `Code/backend/src/main/resources/application-local.properties`. Spring loads it automatically, and it is gitignored so your password never gets committed.
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wudjabudja
spring.datasource.username=YOUR_MAC_USERNAME
spring.datasource.password=
```
- Option A (Homebrew): your Mac username (`whoami`), password left blank.
- Option B (EDB): username `postgres`, password the one you picked.

Don't put comments at the end of these lines. In a `.properties` file they become part of the value. Double-check it's ignored with `git check-ignore -v Code/backend/src/main/resources/application-local.properties`, which should print a match.

### Gemini key
Add `export GEMINI_API_KEY="your-key"` to `~/.zshrc` and open a new terminal tab. Never put the key in a file inside the repo (the repo is public), and never in the JavaFX app. Only the backend uses it.

### Run it (two terminal tabs)

**Tab 1, backend** (wait for `Started WudjabudjaBackendApplication`):
```sh
cd Code/backend
echo ${GEMINI_API_KEY:+key is set}   # should print "key is set"
sh mvnw spring-boot:run
```

**Tab 2, frontend:**
```sh
cd Code/frontend
mvn javafx:run
```

### Click path
1. Log in with any email and password (real login comes in Sprint 3). You land on **🧭 Plan a Trip**.
2. Enter a destination, start and end dates (1 to 7 days), trip type and total budget. Click **Create Trip & Start Quiz**.
3. Answer the 6 quiz steps, then click **Save & Continue** on the review step.
4. On **🗺️ Itinerary**, click **Generate my itinerary**. Gemini takes about 1 to 3 minutes. You get 3 options for every morning, afternoon and evening.
5. **Regenerate** replaces the itinerary. **Plan a Trip → Your trips → Open** reopens an earlier trip.

### API routes (for curl or debugging)

| Route | What it does |
| :--- | :--- |
| `POST /api/trips` | Create a trip: `destination`, `start_date`, `end_date`, `trip_type`, `total_budget`, optional `title`. Returns `201` with its `id`. |
| `GET /api/trips` / `GET /api/trips/{id}` | List user 1's trips / get one trip |
| `POST /api/trips/{id}/preferences` | Save (upsert) quiz answers |
| `GET /api/trips/{id}/preferences?userId=1` | Load quiz answers (`404` if none) |
| `POST /api/trips/{id}/itinerary` | Call Gemini, replace the saved itinerary, return it (slow) |
| `GET /api/trips/{id}/itinerary` | Saved itinerary with each option's database `id` (`404` if none yet) |

`trip_type` is one of `guys_trip`, `girls_trip`, `friends_trip`, `couples_trip`, `family_trip`, `solo_trip`. Errors always come back as JSON with a `message`.

### Gotchas
- **Use `sh mvnw`, not `./mvnw`.** The wrapper isn't marked executable in git (or run `chmod +x mvnw` once).
- **The key has to be in the same terminal that starts the backend.** If you exported it in another tab, or added it to `~/.zshrc` without opening a new tab, Generate fails with `GEMINI_API_KEY is not set on the backend`.
- **`contextLoads` and `ItineraryStoreDbTest` need the database** (schema + seed loaded). The rest run without it: `sh mvnw test -Dtest='TripContextTest,GeminiServiceTest,TripServiceTest,ItineraryStoreTest'`.
- **Old routes still answering after a pull?** Run `sh mvnw clean` once. Classes deleted from the source stay in `target/` until you clean.
- **`Demo user 1 is missing`** means `seed.sql` wasn't loaded.
- **Gemini answers are checked** (dates, wake time + 1 hour, per-activity cap, everyone in exactly one subgroup). If one fails you'll see the exact rule in red. Click **Regenerate**. Very low per-activity caps fail more often.

---

## MVP Approach & Philosophy (Sprints 1 to 3)

**Scaffolding first.** Everything runs locally (local PostgreSQL, local Spring Boot), and we connect the pieces we already built in the most minimal way possible, then expand from there. Authentication, email invites, hosted infrastructure, and secondary nice-to-have features move to later sprints.

*Story points are proposed and get confirmed at lab.*

---

## 1. Team Point Distribution (Sprints 1 to 3)

| Team Member | Role | Sprint 1 | Sprint 2 | Sprint 3 | Total | Core Responsibilities |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **Ethan** | AI Integrator | 11 | 3 | 5 | **19** | Data shape alignment (MVP-00), trip context aggregator, Gemini service in Spring Boot, group-aware Gemini, Gemini auto-retry. |
| **Miles** | UI & Data Architect | 7 | 0 | 6 | **13** | Quiz inputs and Save/Load/Generate actions, options display, shared trip screen, voting screen. |
| **Lukas** | Storage & Algorithms | 8 | 0 | 6 | **14** | Local PostgreSQL, saving preferences and Gemini options, vote storage, Borda Count tally, JSON storage. |
| **George** | Backend API Plumber | 6 | 0 | 6 | **12** | Spring Boot routes connecting the frontend, database and Gemini service, add-traveler and voting routes. |
| **Total** | | **32** | **3** | **23** | **58** | |

*Sprint 2's only new points are MVP-11 (Ethan, 3 pts). The Sprint 1 carryover finished in Sprint 2 stays counted in Sprint 1 under the original owners.*

---

## 2. Feature Assessment

### Must-Have Features (MVP Sprints 1 to 3)
* **Granular Preference Intake & Quiz:** Users input wake-up time, travel experience, max travel radius, categorical budget breakdowns, and activity interests.
* **Database Persistence:** Local PostgreSQL storage for users, trips, participants, preferences, itinerary options, and votes.
* **Trip Context Aggregator:** Backend engine consolidating multiple participants' constraints into structured JSON for Gemini.
* **LLM Itinerary Engine:** Gemini generates daily activity options (morning, afternoon, evening) adhering to budget caps, radius, and wake times.
* **Options Display:** JavaFX interface displaying real generated options grouped by day and time block with local sorting/filtering.
* **Shared Trips:** Multiple users joined to the same trip with divergent preferences and subgroup split support.
* **Borda Count Voting & Tie-Breaker:** Ranked-choice voting (1st = 3 pts, 2nd = 2 pts, 3rd = 1 pt) with deterministic tie-breaking on lowest `estimated_cost_usd`.

### Deferred to Later Sprints (Sprints 4 to 6)
* **Secure Authentication & JWT:** User registration, password hashing (BCrypt), and 24-hour JWT token sessions.
* **Trip Invitations:** Automated email dispatch via SMTP and unique alphanumeric `join_code` sharing.
* **Cloud Database & Hosting:** Migrating local Postgres and Spring Boot to Supabase/Neon and cloud containers, including Postgres cloud sync.
* **Travel Mode:** How the group gets there (car, plane or train). Deferred at the Nov 9 scrum meeting.
* **"Trip Briefing" Module:** Secondary AI engine generating destination customs, language phrases, packing lists, and souvenirs.
* **Weather-Aware "Perfect Days":** Live weather forecast integration into final itinerary presentations.
* **UI Polish & Animations:** Visual feedback polish, transition animations, and error handling toasts.

### Nice-to-Have Features (Future Iterations / High Risk)
* **Public Transit Routing:** Step-by-step transit navigation via Google Maps API.
* **Currency Translator:** Live exchange rate API integration.
* **Live Event Discovery:** Concert, festival, and sports event integration (Ticketmaster / SeatGeek API).
* **Live Travel Advisories:** Government safety advisory API feeds.
* **Calendar Sync:** One-click `.ics` itinerary export.
* **Expense Tracker:** Splitwise-style group ledger for post-trip expense reconciliation.

---

## 3. Checkpoints & Milestones

| Date | Checkpoint | Target Outcome |
| :--- | :--- | :--- |
| **Oct 28** | Data shape decisions signed off at lab | Data contract alignment between schema, DTOs, and AI schemas logged in `docs/decisions-log.md`. |
| **Oct 27 – Oct 30** | Backend & AI Connection | JSON formats agreed, local database and Spring Boot API connected, Gemini tested through backend service. |
| **Nov 2** | **Demo: Single-User Flow End-to-End** | One user takes quiz, answers persist to PostgreSQL, Gemini generates options, options persist, and JavaFX displays them. |
| **Nov 3 – Nov 9** | Finish the End-to-End Flow | Sprint 1 carryover finished and Trip Setup (MVP-11) added: plan a trip, take the quiz, generate and reopen a Gemini itinerary. |
| **Nov 9** | Scrum meeting: Sprint 3 planned | Shared trips and voting moved to Sprint 3. Login/auth, Postgres cloud sync and travel mode deferred. |
| **Nov 23** | **Demo: A Group Trip on One Laptop** | The trip creator adds travelers by email, enters everyone's quiz answers and votes, and every traveler sees the same winners. |

---

## 4. Product Backlog: MVP Sprints

### Sprint 1: Connect Everything
* **Duration:** Oct 27 to Nov 2, 2026
* **Sprint Goal:** One user takes the quiz, answers save to PostgreSQL, Gemini generates options, the options save, and JavaFX displays them. No manual JSON files.
* **Total Points:** 32 pts

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

### Sprint 2: Finish the End-to-End Flow (planned: Shared Trips & Voting)
* **Duration:** Nov 3 to Nov 9, 2026
* **Planned Goal:** Two users join the same trip, save different preferences, generate group activity options, vote, and see the same winners.
* **What shipped:** The Sprint 1 carryover plus trip creation (MVP-11). One traveler can now plan a trip, take the quiz, generate a Gemini itinerary and reopen it later (merged to `main` in PR #6). The shared-trip and voting tasks moved to Sprint 3.
* **Total Points:** 3 pts new (MVP-11). Carryover points were counted in Sprint 1.

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

### Sprint 3: Group Trips & Voting
* **Duration:** Nov 10 to Nov 23, 2026
* **Sprint Goal:** Group trips and voting. Move from one traveler to a whole group.
* **Total Points:** 23 pts
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

## 5. Deferred Roadmap (Sprints 4+)

The original backlog tasks are preserved and prioritized for subsequent sprints. At the Nov 9 scrum meeting, login/auth, Postgres cloud sync and travel mode (car/plane/train) were deferred, so each sprint below moved back one.

### Sprint 4: Authentication & Invitations
* **AUTH-01 (Lukas - 5 pts):** `POST /api/auth/register` with BCrypt password hashing.
* **AUTH-02 (Lukas - 5 pts):** `POST /api/auth/login` returning 24-hour JWT token.
* **AUTH-03 (Ethan / Lukas - 3 pts):** JavaFX `AuthManager` singleton managing session tokens and `Bearer` headers.
* **AUTH-04 (Miles - 5 pts):** Login and registration FXML views wired to auth endpoints.
* **TRIP-02 (George - 5 pts):** Automated HTML email dispatch with invite codes.
* **TRIP-03 (George - 3 pts):** Join trip via code endpoint.

### Sprint 5: Trip Briefing & Weather Integration
* **BRIEF-01 (Ethan - 5 pts):** Secondary Gemini prompt engine generating cultural customs, phrases, packing lists, and souvenirs.
* **BRIEF-02 (George - 3 pts):** `GET /api/trips/{id}/briefing` REST endpoint.
* **BRIEF-03 (Miles - 5 pts):** JavaFX Trip Briefing tab layout.
* **WEATHER-01 (Ethan / George - 5 pts):** Weather API integration for real-time forecasting and "Perfect Days" alignment.

### Sprint 6: UI Polish & Production Scaffolding
* **UI-POLISH (Miles - 8 pts):** Consistent styling, responsive layout tuning, error banners, and loading state animations.
* **DEPLOY-01 (Lukas / George - 6 pts):** Cloud database provisioning (Supabase/Neon), containerization, and backend production deployment.

---

## 6. Open Questions & Architectural Decisions

* **Database Save Layer Ownership:** Who owns saving to the database? The sprint plan and the Oct 27 meeting put Lukas on storage with George exposing the routes. The MVP-00 doc lists George on the save layer. This backlog follows the sprint plan (Lukas on storage, George on API controllers) until confirmed at lab.
* **Decisions Log:** Full tracking of schema types, allowed values, and API contracts is maintained in [`docs/decisions-log.md`](docs/decisions-log.md).
* **Full Backlog Details:** Dedicated sprint and product backlogs are documented in [`docs/SPRINT_BACKLOG.md`](docs/SPRINT_BACKLOG.md) and [`docs/PRODUCT_BACKLOG.md`](docs/PRODUCT_BACKLOG.md).

---

## 7. Detailed User Stories & Estimations

### User Story 1: Granular Preference Intake & Options Generation (Sprint 1 MVP)
**As a traveler**, I want to fill out my travel quiz (wake-up time, radius, budget, and activity interests) and submit it, **so that** my preferences persist to the database and Gemini immediately generates customized daily options.

**Acceptance Criteria:**
* JavaFX quiz form submits valid JSON adhering to `PreferenceRequest.java`.
* Spring Boot API receives payload and upserts one row per trip and user into `user_preferences`.
* Gemini service consumes aggregated trip context and returns 3 candidate options per time block.
* Generated options persist to `itinerary_options` and render in the JavaFX dashboard.

### User Story 2: Democratic Group Voting via Borda Count (Sprint 3 MVP)
**As a participant in a shared trip**, I want to rank my top 3 choices for each time block and view the winning consensus, **so that** our group resolves conflicting preferences equitably without manual debates.

**Acceptance Criteria:**
* Two or more travelers are on the same `trip_id` with differing preferences (for now the trip creator enters everyone's answers on one laptop).
* JavaFX displays a Rank 1, 2, 3 voting interface for morning, afternoon, and evening blocks.
* Backend tallies votes using Borda Count (1st = 3 pts, 2nd = 2 pts, 3rd = 1 pt).
* The highest total wins, so the group gets the least-unfavorable option. Ties go to the cheaper option (lowest `estimated_cost_usd`).
* Every traveler sees the exact same winning itinerary.
