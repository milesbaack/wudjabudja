# WadjaBudja — Product Backlog & Team Specification

**Project Purpose:** A unified, AI-powered travel planning tool designed to optimize group trips based on combined interests and budgets, eliminating the stress of designated "group planners."

**Architecture Summary:**
*   **Frontend:** JavaFX (Single-window `BorderPane` with persistent sidebar navigation).
*   **Backend:** Java Spring Boot REST API.
*   **Database:** PostgreSQL (Hosted via Supabase/Neon).
*   **Security:** Spring Security (BCrypt password hashing & JWT session management).
*   **AI Service:** Google Gemini Flash API.

---

## 1. Team Member Responsibilities

To minimize merge conflicts and leverage individual strengths, development is divided using vertical slicing by domain:

| Team Member | Role | Total Points | Core Responsibilities |
| :--- | :--- | :--- | :--- |
| **Miles** | **UI & Data Architect** | 24 | Database schema design (`schema.sql`), JavaFX Application Shell, UI views (Login/Reg, Forms), and the voting interface. |
| **Ethan** | **AI Integrator ("Vibe Coder")** | 23 | Gemini LLM prompt engineering, constraint aggregation logic, and asynchronous UI features (loading states, Final Itinerary dashboard). |
| **George** | **Backend API Plumber** | 22 | Spring Boot REST controllers, automated HTML email dispatch via `JavaMailSender`, API endpoints, and Maven dependency setup. |
| **Lukas** | **Security & Algorithms** | 21 | Spring Security (JWT authentication, BCrypt), JavaFX session management, the complex Borda Count vote tallying engine, and global API exceptions. |

---

## 2. Feature Assessment

**Must-Have Features (MVP)**
*   **Secure Authentication:** JWT-based login and registration for all users.
*   **Trip Initialization:** Key users can define a destination, dates, and global budget.
*   **Email Invitations:** Automated SMTP email dispatch sending secure join codes to friends.
*   **Hybrid Preference Intake:** Secondary users input hard constraints (budget/diet) and open-ended text requests.
*   **LLM Itinerary Engine:** Backend aggregation of group constraints fed into Gemini Flash to generate daily activity candidates.
*   **Borda Count Voting:** A ranked-choice point system (1st=3pts, 2nd=2pts, 3rd=1pt) to democratically select activities, with automatic cost-based tie-breakers.

**Nice-to-Have Features (Future Iterations)**
*   **Expense Tracker:** A Splitwise-style ledger to track who paid for what during the trip.
*   **Calendar Sync:** Exporting the finalized itinerary to `.ics` formats.
*   **Live Pricing API:** Replacing Gemini's estimated costs with live flight/hotel API calls (e.g., Skyscanner).

---

## 3. Product Backlog (DEEP)

### Epic 1: Infrastructure & Project Scaffolding
| ID | Task | Assignee | Priority | Points | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **INFRA-01** | PostgreSQL Schema | Miles | P0 | 3 | Create `schema.sql`: `users`, `trips`, `trip_participants`, `user_preferences`, `itinerary_options`, `votes`. |
| **INFRA-02** | Spring Boot Init | George | P0 | 2 | Scaffold API. Add dependencies: Web, JPA, PostgreSQL, Security, Validation, JavaMailSender. |
| **INFRA-03** | JavaFX Maven Setup | George | P0 | 2 | Configure `pom.xml` for Jackson JSON parsing and Java 11 `HttpClient`. |
| **INFRA-04** | Global Exception Handler | Lukas | P1 | 3 | `@ControllerAdvice` to catch standard errors (404, 403, 500) and return them as structured JSON. |

### Epic 2: Authentication & User Management
| ID | Task | Assignee | Priority | Points | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUTH-01** | Register API | Lukas | P0 | 5 | `POST /api/auth/register`. Hash passwords using BCrypt. Validate email uniqueness. |
| **AUTH-02** | Login API | Lukas | P0 | 5 | `POST /api/auth/login`. Verify credentials, generate 24-hour JWT. |
| **AUTH-03** | JavaFX Auth Manager | Lukas | P0 | 3 | Singleton class to store JWT in memory and attach as `Bearer` header to API calls. |
| **AUTH-04** | Login/Reg Views | Miles | P0 | 5 | FXML views with client-side validation (passwords match, non-empty fields). |

### Epic 3: Navigation & Trip Setup
| ID | Task | Assignee | Priority | Points | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NAV-01** | Main App Shell | Miles | P0 | 3 | `Main.fxml` with static left Sidebar and dynamic center `StackPane` for view swapping. |
| **TRIP-01** | Create Trip API | George | P0 | 3 | `POST /api/trips`. Validates dates. Auto-assigns creator as Key User. Generates 6-char `join_code`. |
| **TRIP-02** | Email Invite Engine | George | P1 | 5 | `POST /api/trips/{id}/invite`. Accepts `List<String>`. Connects to SMTP. Sends HTML email with code. |
| **TRIP-03** | Join Trip Flow | Miles | P1 | 3 | `POST /api/trips/join`. Accepts `join_code` and maps user to `trip_participants`. |

### Epic 4: Participant Preferences
| ID | Task | Assignee | Priority | Points | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PREF-01** | Save Preferences API | George | P0 | 3 | `POST /api/trips/{id}/preferences`. Upserts `max_budget`, `dietary_tags`, `activity_tags`, and `open_notes`. |
| **PREF-02** | Preference UI View | Miles | P1 | 5 | FXML view with Budget slider, standard checkboxes, and Text Area for custom requests. |
| **PREF-03** | Readiness Tracker | George | P2 | 2 | API endpoint verifying all invited users submitted preferences before enabling LLM generation. |

### Epic 5: Gemini Flash Itinerary Engine
| ID | Task | Assignee | Priority | Points | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AI-01** | Constraint Aggregator | Ethan | P0 | 3 | Backend service combining all user budgets/tags into a single contextual JSON map. |
| **AI-02** | Gemini API Client | Ethan | P0 | 5 | Constructs System Prompt demanding strict JSON. Calls Gemini via REST over HTTP. |
| **AI-03** | JSON Deserialization | Ethan | P0 | 5 | Maps LLM response to Java objects. Saves exactly 3 options per time-block per day to DB. |
| **AI-04** | Error & Retry Handling | Ethan | P1 | 5 | Catches `JsonParseException` on hallucinated formatting, auto-retries twice before throwing 500. |

### Epic 6: Consensus Voting & Finalization
| ID | Task | Assignee | Priority | Points | Acceptance Criteria / Technical Details |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **VOTE-01** | Fetch Candidate Options | George | P1 | 2 | `GET /api/trips/{id}/options`. Returns activities grouped by Day and Time block. |
| **VOTE-02** | Submit Votes API | George | P1 | 3 | `POST /api/trips/{id}/votes`. Validates user hasn't voted. Saves rank points (1st=3, 2nd=2, 3rd=1). |
| **VOTE-03** | Tally & Tie-Breaker | Lukas | P1 | 5 | Calculates `SUM(points)`. Tie-breaker selects the lowest `estimated_cost` option. Sets `is_final = true`. |
| **FINAL-01** | Final Itinerary UI | Ethan | P1 | 5 | Fetches winning options. Renders chronological timeline. Displays Total Cost vs. Group Budget. |
| **VOTE-04** | Voting Component UI | Miles | P2 | 5 | Drag-and-drop or dropdown UI to allow assigning 1st, 2nd, and 3rd rank points to generated options. |

---

## 4. Detailed User Stories & Estimations

### User Story 1: Hybrid Preference Intake (Epic 4)
**As a Secondary User**, I want to select common travel preferences via checkboxes but also have a text box to write specific desires, **so that** the AI can account for both standard logistics (e.g., "Vegan") and nuanced requests (e.g., "I really want to visit a vintage camera shop").

**Acceptance Criteria:**
*   JavaFX UI provides a slider for `Max Personal Budget`.
*   JavaFX UI provides standard checkboxes ("Foodie", "Nightlife", "Accessibility Needed").
*   JavaFX UI provides a text area for "Special Requests".
*   Spring Boot backend stores all three data types to the user's profile for that specific trip.

**Story Beats & Cost Estimation (Total: 8 Points / ~18 Developer Hours):**
1.  **Database Expansion (Miles - 1 point):** Update PostgreSQL schema to include `dietary_tags` (String Array), `activity_tags` (String Array), and `open_notes` (VARCHAR 500) in the preferences table.
2.  **Spring Boot DTOs & Controller (George - 3 points):** Create the `PreferenceRequest` Java class to handle the JSON payload, and write the `POST /api/trips/{id}/preferences` endpoint to validate and persist the data.
3.  **JavaFX Form Layout (Miles - 2 points):** Use Scene Builder to design the FXML layout, ensuring the UI cleanly separates numeric inputs from the open-ended text box.
4.  **JavaFX HTTP Client Implementation (Miles - 2 points):** Write the asynchronous networking code to serialize the user's inputs into JSON and transmit them to the backend API without blocking the UI thread.

### User Story 2: Borda Count Voting Engine (Epic 6)
**As a trip participant**, I want to rank my top 3 choices for a daily activity, **so that** if my absolute favorite isn't picked, my second choice still has a high mathematical chance of winning.

**Acceptance Criteria:**
*   UI groups competing itinerary options by day and time block (e.g., "Day 2 - Afternoon").
*   User assigns Rank 1, Rank 2, and Rank 3 to the options.
*   Backend translates Rank 1 → 3 points, Rank 2 → 2 points, Rank 3 → 1 point.
*   Once all users have voted, the backend tallies the points.
*   If Option A and Option B tie, the backend automatically selects the option with the lower `estimatedCost`.

**Story Beats & Cost Estimation (Total: 13 Points / ~30 Developer Hours):**
1.  **Rankings Data Model (Miles - 2 points):** Create the `votes` table linking `user_id`, `option_id`, and a `point_value` integer.
2.  **Vote Submission Endpoint (George - 3 points):** Build `POST /api/trips/{id}/votes`. Add business logic to ensure a user cannot submit multiple 1st-place votes for the same time block.
3.  **JavaFX Ranking UI (Miles - 5 points):** Build a complex interactive UI component allowing users to assign ranks (via dropdowns or drag-and-drop lists) to the 3 LLM-generated options per time block.
4.  **Tally Engine & Tie-Breaker Service (Lukas - 3 points):** Write the backend SQL query/JPA logic to group votes by `option_id`, sum the points, apply the `estimatedCost` tie-breaker logic, and lock the winning options into the final itinerary state.
