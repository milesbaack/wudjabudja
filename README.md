# WadjaBudja — Product Backlog & Team Specification

**Project Purpose:** A unified, AI-powered travel planning tool designed to optimize group trips based on combined interests and budgets, eliminating the stress of designated "group planners."

**Architecture Summary:**
*   **Frontend:** JavaFX (Single-window `BorderPane` with persistent sidebar navigation).
*   **Backend:** Java Spring Boot REST API.
*   **Database:** PostgreSQL (Hosted via Supabase/Neon).
*   **Security:** Spring Security (BCrypt password hashing & JWT session management).
*   **AI Service:** Google Gemini Flash API.

---

## 1. Team Member Responsibilities & Point Distribution

| Team Member | Role | Total Pts | Core Responsibilities |
| :--- | :--- | :--- | :--- |
| **Miles** | **UI & Data Architect** | 26 | Database schema design (`schema.sql`), JavaFX Application Shell, and all major frontend UI components (Login, Forms, Dashboards). |
| **Ethan** | **AI Integrator** | 26 | Gemini prompt engineering, constraint aggregation, Final Itinerary UI, and the secondary "Trip Briefing" LLM engine. |
| **George** | **Backend API Plumber** | 26 | Spring Boot REST controllers, automated HTML email dispatch, core API endpoints, and Maven dependency setup. |
| **Lukas** | **Security & Algorithms** | 28 | Spring Security (JWT, BCrypt), Borda Count vote tallying engine, global API exception routing, and LLM error recovery. |

---

## 2. Feature Assessment

**Must-Have Features (MVP)**
*   **Secure Authentication:** JWT-based login and registration for all users.
*   **Trip Initialization:** "Trip Leader" defines destination, dates, and global budget.
*   **Email Invitations:** Automated SMTP email dispatch sending secure join codes.
*   **Granular Preference Intake:** Users input granular constraints: Wake-up time, travel experience level, sub-city/travel radius constraints, and categorical budget breakdowns (e.g., transit vs. lodging).
*   **LLM Itinerary Engine:** Gemini generates daily activities, automatically scheduling "split off" windows for companions with conflicting interests.
*   **Borda Count Voting:** Ranked-choice point system (1st=3pts, 2nd=2pts, 3rd=1pt) to select activities democratically.
*   **"Trip Briefing" Module:** A secondary AI-generated dashboard featuring language/customs education, localized packing lists, historical facts, tourist season advisories, and tailored souvenir recommendations.
*   **"Perfect Day" Bypass:** An override button allowing the "Trip Leader" to generate a highly opinionated, single-day itinerary without group voting.

**Nice-to-Have Features (Future Iterations / High Risk)**
*   **Public Transit Routing:** Step-by-step navigation via Google Maps API.
*   **Live Weather Sync:** OpenWeatherMap API integration for real-time forecasting.
*   **Currency Translator:** Live exchange rate API integration.
*   **Time-Specific Live Events:** Fetching live concert, festival, and sports data via Ticketmaster/SeatGeek API.
*   **Live Travel Advisories:** Government API integration for safety warnings.
*   **Jet Lag Prep:** Pre-trip sleep schedule shifting.
*   **Expense Tracker:** Splitwise-style ledger to track who paid for what.
*   **Calendar Sync:** Exporting finalized itinerary to `.ics` formats.
*   **Live Pricing API:** Real-time flight/hotel API calls (e.g., Skyscanner).

---

## 3. Product Backlog (DEEP)

### Epic 1: Infrastructure & Project Scaffolding
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **INFRA-01** | PostgreSQL Schema | Miles | P0 | 3 | Create `schema.sql`: `users`, `trips`, `trip_participants`, `user_preferences`, `itinerary_options`, `votes`. |
| **INFRA-02** | Spring Boot Init | George | P0 | 2 | Scaffold API. Add dependencies: Web, JPA, PostgreSQL, Security, Validation, JavaMailSender. |
| **INFRA-03** | JavaFX Maven Setup | George | P0 | 2 | Configure `pom.xml` for Jackson JSON parsing and Java 11 `HttpClient`. |
| **INFRA-04** | Global Exception Handler | Lukas | P1 | 3 | `@ControllerAdvice` to catch standard errors (404, 403, 500) and return them as structured JSON. |

### Epic 2: Authentication & User Management
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUTH-01** | Register API | Lukas | P0 | 5 | `POST /api/auth/register`. Hash passwords using BCrypt. Validate email uniqueness. |
| **AUTH-02** | Login API | Lukas | P0 | 5 | `POST /api/auth/login`. Verify credentials, generate 24-hour JWT. |
| **AUTH-03** | JavaFX Auth Manager | Lukas | P0 | 3 | Singleton class to store JWT in memory and attach as `Bearer` header to API calls. |
| **AUTH-04** | Login/Reg Views | Miles | P0 | 5 | FXML views with client-side validation (passwords match, non-empty fields). |

### Epic 3: Navigation & Trip Setup
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NAV-01** | Main App Shell | Miles | P0 | 3 | `Main.fxml` with static left Sidebar and dynamic center `StackPane` for view swapping. |
| **TRIP-01** | Create Trip API | George | P0 | 3 | `POST /api/trips`. Validates dates, takes Trip Type (Guy trip, Romantic). Generates `join_code`. |
| **TRIP-02** | Email Invite Engine | George | P1 | 5 | `POST /api/trips/{id}/invite`. Accepts `List<String>`. Connects to SMTP. Sends HTML email with code. |
| **TRIP-03** | Join Trip Flow | George | P1 | 3 | `POST /api/trips/join`. Accepts `join_code` and maps user to `trip_participants`. |

### Epic 4: Participant Preferences
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PREF-01** | Save Preferences API | George | P0 | 3 | `POST /api/trips/{id}/preferences`. Upserts budget breakdowns, wake time, experience level, and tags. |
| **PREF-02** | Preference UI View | Miles | P1 | 5 | FXML view with sliders/checkboxes capturing the new Tier 1 data points (Wake Time, Radius). |
| **PREF-03** | Readiness Tracker | Lukas | P2 | 2 | API endpoint verifying all invited users submitted preferences before enabling LLM generation. |

### Epic 5: Gemini Flash Itinerary Engine
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AI-01** | Constraint Aggregator | Ethan | P0 | 3 | Backend service combining all granular user constraints into a contextual JSON map. |
| **AI-02** | Gemini API Client | Ethan | P0 | 5 | Constructs System Prompt demanding strict JSON, enforcing logic for "Split off" conflict windows. |
| **AI-03** | JSON Deserialization | Ethan | P0 | 5 | Maps LLM response to Java objects. Saves exactly 3 options per time-block per day to DB. |
| **AI-04** | Error & Retry Handling | Lukas | P1 | 5 | Catches `JsonParseException` on hallucinated formatting, auto-retries twice before throwing 500. |

### Epic 6: Consensus Voting & Finalization
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **VOTE-01** | Fetch Candidate Options | George | P1 | 2 | `GET /api/trips/{id}/options`. Returns activities grouped by Day and Time block. |
| **VOTE-02** | Submit Votes API | George | P1 | 3 | `POST /api/trips/{id}/votes`. Validates user hasn't voted. Saves rank points (1st=3, 2nd=2, 3rd=1). |
| **VOTE-03** | Tally & Tie-Breaker | Lukas | P1 | 5 | Calculates `SUM(points)`. Tie-breaker selects the lowest `estimated_cost` option. Sets `is_final = true`. |
| **FINAL-01** | Final Itinerary UI | Ethan | P1 | 5 | Fetches winning options. Renders chronological timeline. Displays Total Cost vs. Group Budget. |
| **VOTE-04** | Voting Component UI | Miles | P2 | 5 | Drag-and-drop or dropdown UI to allow assigning 1st, 2nd, and 3rd rank points to options. |

### Epic 7: Trip Briefing & Context Expansion
| ID | Task | Assignee | Priority | Pts | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **BRIEF-01** | Briefing AI Engine | Ethan | P2 | 5 | Secondary Gemini prompt generating language/customs, packing lists, facts, and tourist season warnings. |
| **BRIEF-02** | Briefing APIs | George | P2 | 3 | `GET /api/trips/{id}/briefing` and `POST /api/trips/{id}/perfect-day` endpoints. |
| **BRIEF-03** | Briefing UI Dashboard | Miles | P2 | 5 | JavaFX tab to display the briefing data cleanly, including the Key User "Perfect Day" trigger button. |
| **BRIEF-04** | "Perfect Day" Bypass | Ethan | P2 | 3 | Business logic that generates a 1-day itinerary and automatically marks it `is_final = true`, bypassing votes. |

---

## 4. Detailed User Stories & Estimations

### User Story 1: Expanded Granular Preference Intake (Epic 4)
**As a Secondary User**, I want to specify my exact wake-up time, travel radius, and budget broken down by categories, **so that** the AI doesn't schedule an expensive 7:00 AM activity three hours away when I want to sleep in and save money for food.

**Acceptance Criteria:**
*   JavaFX UI includes inputs for: Wake-up time dropdown, Travel Experience (Novice to Expert), Max Travel Radius (miles), and categorical budget sliders (Transit vs. Lodging).
*   Backend strictly validates and saves these parameters to the `user_preferences` table.
*   The LLM Constraint Aggregator successfully maps these variables into the prompt context.

**Story Beats & Cost Estimation (Total: 8 Points / ~18 Developer Hours):**
1.  **Database Expansion (Miles - 1 point):** Update PostgreSQL `user_preferences` table to add columns for `wake_time`, `experience_level`, `max_radius_miles`, and JSONB for `categorical_budgets`.
2.  **Spring Boot DTOs & Controller (George - 3 points):** Expand the `PreferenceRequest` Java payload and `POST /api/trips/{id}/preferences` logic.
3.  **JavaFX Form Layout (Miles - 2 points):** Expand the FXML layout to handle the new Tier 1 inputs clearly without overwhelming the user.
4.  **JavaFX HTTP Client Update (Miles - 2 points):** Update the JSON serialization map in the frontend to include the new fields in the POST request.

### User Story 2: "Trip Briefing" AI Generation (Epic 7)
**As a group traveler**, I want a single dashboard that tells me what to pack, local customs, historical facts, and tourist season advisories, **so that** I am prepared for the culture and climate before the trip begins.

**Acceptance Criteria:**
*   A secondary Gemini API call generates non-itinerary context (packing, customs, facts).
*   The data is stored in a structured JSON column tied to the `trips` table.
*   The UI renders this information in a dedicated "Briefing" tab distinct from the voting/itinerary views.

**Story Beats & Cost Estimation (Total: 13 Points / ~30 Developer Hours):**
1.  **Briefing Prompt Engineering (Ethan - 5 points):** Design the system prompt and JSON schema specifically for extracting cultural norms, weather expectations, and packing suggestions.
2.  **Briefing API Controller (George - 3 points):** Create the `GET /api/trips/{id}/briefing` endpoint to serve this data to the client.
3.  **JavaFX Briefing Dashboard (Miles - 5 points):** Design an FXML layout that cleanly separates the different briefing categories using JavaFX Accordions or TabPanes.

### User Story 3: "Perfect Day" Override Bypass (Epic 7)
**As a Key User**, I want the ability to bypass the group voting system entirely for a single day, **so that** I can force a highly opinionated, premium 1-day itinerary that I know the group will love without having to wait for consensus.

**Acceptance Criteria:**
*   The Briefing Dashboard includes a "Generate Perfect Day" button only visible to the Key User.
*   Clicking the button sends a request to a dedicated backend endpoint.
*   The endpoint calls Gemini to generate a 1-day itinerary and immediately saves it to the database with `is_final = true`, completely bypassing the `votes` table.
*   The frontend instantly updates the Final Itinerary view with the new data.

**Story Beats & Cost Estimation (Total: 3 Points / ~7 Developer Hours):**
1.  **"Perfect Day" Backend Bypass Logic (Ethan - 3 points):** Build the `POST /api/trips/{id}/perfect-day` endpoint. Reuse Ethan's existing Gemini HTTP client logic, but write custom database logic that intercepts the response and instantly writes it to the `itinerary_options` table with the `is_final` flag permanently set to true.
