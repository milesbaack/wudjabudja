# WudjaBudja Database Reference Guide (PostgreSQL)

Welcome to the WudjaBudja database! This guide is designed for the whole team. If you have never worked with PostgreSQL before, don't worry—this doc covers how the database is structured, how to run it, and practical query examples for each of our sprint tasks.

---

## 1. Quick Architecture & Table Overview

Our database is built around **6 core tables** defined in [`Code/database/schema.sql`](schema.sql):

```
       +-------------------+
       |       users       |
       +-------------------+
                 |
                 | 1:N (creator)
                 v
       +-------------------+               +---------------------+
       |       trips       |<------------->|  trip_participants  |
       +-------------------+      M:N      +---------------------+
                 |                                    |
                 | 1:N                                | 1:1 (per trip)
                 v                                    v
       +-------------------+               +---------------------+
       | itinerary_options |               |  user_preferences   |
       +-------------------+               +---------------------+
                 |
                 | 1:N
                 v
       +-------------------+
       |       votes       |
       +-------------------+
```

| Table | Purpose | Key Columns |
| :--- | :--- | :--- |
| **`users`** | Registered user accounts & auth | `id`, `email`, `password_hash`, `full_name` |
| **`trips`** | Trip metadata, join codes, and AI briefing | `id`, `creator_id`, `destination`, `start_date`, `end_date`, `total_budget`, `trip_type`, `join_code`, `briefing_data` |
| **`trip_participants`** | Junction table connecting users to trips | `trip_id`, `user_id`, `role` (`'leader'` or `'member'`) |
| **`user_preferences`** | Granular user constraints for the AI | `trip_id`, `user_id`, `wake_time`, `experience_level`, `max_radius_miles`, `categorical_budgets`, `interests` |
| **`itinerary_options`** | AI activity candidates (3 per block) | `trip_id`, `date`, `time_block`, `subgroup_participants`, `title`, `estimated_cost_usd`, `is_final` |
| **`votes`** | Borda Count ranked votes (1st, 2nd, 3rd) | `option_id`, `user_id`, `rank`, `points` |

---

## 2. Setting Up & Running the Database

Per the project spec, our database is PostgreSQL (either hosted on **Supabase / Neon** or run locally).

### Option A: Using Hosted Database (Supabase or Neon) — Recommended
1. Create a free PostgreSQL project on [Supabase](https://supabase.com) or [Neon](https://neon.tech).
2. Copy your connection URL (looks like `postgresql://postgres:[PASSWORD]@[HOST]:5432/[DB]`).
3. Open the **SQL Editor** tab in the web dashboard.
4. Copy the entire contents of [`schema.sql`](schema.sql), paste it into the editor, and click **Run**.

### Option B: Running Locally (via `psql` or Docker)
If you have PostgreSQL installed on your machine:
```bash
# Create the database
createdb wudjabudja

# Run our schema script to create all tables and indexes
psql -d wudjabudja -f Code/database/schema.sql
```

### Option C: Spring Boot Automatic Initialization (George)
In `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/wudjabudja
spring.datasource.username=postgres
spring.datasource.password=your_password

# Automatically runs schema.sql on startup
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema.sql
```

---

## 3. Important PostgreSQL Concepts to Know

### 1. Auto-Incrementing IDs (`BIGSERIAL`)
- Whenever you insert a new row, **do NOT provide an `id`**. PostgreSQL generates it automatically (`1, 2, 3...`).
```sql
-- Correct:
INSERT INTO users (email, password_hash, full_name) VALUES ('miles@example.com', 'hashed_pw', 'Miles Baack');

-- Wrong (do not manually specify id):
INSERT INTO users (id, email, ...) VALUES (1, 'miles@example.com', ...);
```

### 2. Cascading Deletes (`ON DELETE CASCADE`)
- All foreign keys are configured with `ON DELETE CASCADE`. If a trip is deleted, PostgreSQL automatically deletes all its participants, preferences, itinerary options, and votes. You don't have to manually clean up child rows.

### 3. Native JSON (`JSONB`)
- We use `JSONB` for flexible data structures (`briefing_data`, `categorical_budgets`, `interests`, `subgroup_participants`).
- In SQL, you can query inside JSON:
```sql
-- Extract a text field inside JSON with ->>
SELECT briefing_data->'season_advisory'->>'crowd_level' FROM trips WHERE id = 1;

-- Check if an array contains a value with ?
SELECT * FROM user_preferences WHERE interests ? 'hiking';
```

---

## 4. Teammate Cheat Sheets

### For George (Spring Boot REST APIs)

#### Creating a Trip (`TRIP-01`):
```sql
INSERT INTO trips (creator_id, title, destination, start_date, end_date, total_budget, trip_type, join_code)
VALUES (1, 'Guys Surf Weekend', 'San Diego', '2027-03-12', '2027-03-14', 2400.00, 'guys_trip', 'WUDJA-9821')
RETURNING id;
```
*(Tip: `RETURNING id` returns the newly generated ID directly in JDBC/JPA without a second query!)*

#### Adding a Participant on Join Code (`TRIP-03`):
```sql
-- Find trip by join code, then insert membership
INSERT INTO trip_participants (trip_id, user_id, role)
VALUES (
    (SELECT id FROM trips WHERE join_code = 'WUDJA-9821'),
    2,
    'member'
);
```

#### Saving Granular Preferences (`PREF-01` / Upsert):
```sql
INSERT INTO user_preferences (trip_id, user_id, wake_time, experience_level, max_radius_miles, categorical_budgets, per_block_cap_usd, interests)
VALUES (
    1, 
    2, 
    '08:30:00', 
    'intermediate', 
    15, 
    '{"lodging": 300, "transit": 60, "food": 150, "activities": 120}'::jsonb, 
    30.00, 
    '["craft beer", "baseball", "hiking"]'::jsonb
)
ON CONFLICT (trip_id, user_id) DO UPDATE SET
    wake_time = EXCLUDED.wake_time,
    experience_level = EXCLUDED.experience_level,
    max_radius_miles = EXCLUDED.max_radius_miles,
    categorical_budgets = EXCLUDED.categorical_budgets,
    per_block_cap_usd = EXCLUDED.per_block_cap_usd,
    interests = EXCLUDED.interests,
    updated_at = CURRENT_TIMESTAMP;
```

---

### For Ethan (AI Pipeline & Itinerary Finalization)

#### Aggregating Constraints for Gemini (`AI-01`):
To build your `trip_context.json` from the database:
```sql
-- Get trip details
SELECT id, destination, start_date, end_date, total_budget, trip_type 
FROM trips 
WHERE id = 1;

-- Get all participant preferences for this trip
SELECT 
    p.user_id,
    tp.role,
    p.wake_time,
    p.experience_level,
    p.max_radius_miles,
    p.categorical_budgets,
    p.per_block_cap_usd,
    p.interests
FROM user_preferences p
JOIN trip_participants tp ON p.trip_id = tp.trip_id AND p.user_id = tp.user_id
WHERE p.trip_id = 1;
```

#### Saving Gemini's Candidate Options (`AI-03`):
```sql
INSERT INTO itinerary_options (
    trip_id, date, time_block, subgroup_participants, split_reason, 
    title, description, category, neighborhood, start_time, end_time, 
    estimated_cost_usd, why_it_fits
) VALUES (
    1, '2027-03-12', 'morning', '["p1"]'::jsonb, 'Early wake up for hiking',
    'Torrey Pines State Reserve Hike', 'Scenic ocean cliff hiking', 'outdoors', 'La Jolla',
    '08:00:00', '11:00:00', 0.00, 'Fits intermediate hiker profile and early wake time'
);
```

#### Saving the AI Trip Briefing (`BRIEF-01`):
```sql
UPDATE trips 
SET briefing_data = '{"customs": [...], "packing_list": [...]}'::jsonb 
WHERE id = 1;
```

---

### For Lukas (Security, Auth & Borda Voting)

#### User Registration & Login (`AUTH-01`, `AUTH-02`):
```sql
-- Check if email exists
SELECT id, email, password_hash, full_name FROM users WHERE email = 'miles@example.com';

-- Register new user (store BCrypt hash in password_hash!)
INSERT INTO users (email, password_hash, full_name) 
VALUES ('miles@example.com', '$2a$12$e8n...BCryptHashHere...', 'Miles Baack');
```

#### Submitting Ranked Votes (`VOTE-02`):
Rank 1 = 3 points, Rank 2 = 2 points, Rank 3 = 1 point:
```sql
INSERT INTO votes (option_id, user_id, rank, points) VALUES (10, 2, 1, 3);
INSERT INTO votes (option_id, user_id, rank, points) VALUES (11, 2, 2, 2);
INSERT INTO votes (option_id, user_id, rank, points) VALUES (12, 2, 3, 1);
```

#### Borda Count Tally & Tie-Breaker (`VOTE-03`):
Calculate total points per candidate option, breaking ties with the lowest cost:
```sql
SELECT 
    io.id AS option_id,
    io.title,
    io.estimated_cost_usd,
    COALESCE(SUM(v.points), 0) AS total_points
FROM itinerary_options io
LEFT JOIN votes v ON io.id = v.option_id
WHERE io.trip_id = 1 AND io.date = '2027-03-12' AND io.time_block = 'morning'
GROUP BY io.id, io.title, io.estimated_cost_usd
ORDER BY total_points DESC, io.estimated_cost_usd ASC
LIMIT 1; -- The winner!
```

#### Finalizing the Winner:
```sql
UPDATE itinerary_options SET is_final = TRUE WHERE id = :winning_option_id;
```

---

## 5. Need Help or Schema Changes?
If your task requires an adjustment to column types or new constraints, talk with Miles (UI & Data Architect) before altering `schema.sql`.
