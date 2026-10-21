-- =============================================================================
-- WudjaBudja Database Schema
-- Task: INFRA-01 (PostgreSQL Schema)
-- =============================================================================

-- 1. USERS
-- Stores registered user accounts with BCrypt-hashed credentials.
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. TRIPS
-- Core trip metadata, settings, invite join codes, and AI briefing data.
CREATE TABLE trips (
    id BIGSERIAL PRIMARY KEY,
    creator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_budget NUMERIC(10, 2) NOT NULL CHECK (total_budget >= 0),
    trip_type VARCHAR(50) NOT NULL,
    join_code VARCHAR(20) NOT NULL UNIQUE,
    briefing_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trip_dates CHECK (end_date >= start_date)
);

-- 3. TRIP PARTICIPANTS
-- Junction table mapping users to trips with roles (leader vs member).
CREATE TABLE trip_participants (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'member' CHECK (role IN ('leader', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trip_user UNIQUE (trip_id, user_id)
);

-- 4. USER PREFERENCES
-- Granular traveler constraints: wake time, travel radius, experience, and budgets.
CREATE TABLE user_preferences (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wake_time TIME NOT NULL DEFAULT '08:00:00',
    experience_level VARCHAR(30) NOT NULL DEFAULT 'intermediate' CHECK (experience_level IN ('novice', 'intermediate', 'expert')),
    max_radius_miles INT NOT NULL DEFAULT 10 CHECK (max_radius_miles > 0),
    categorical_budgets JSONB NOT NULL DEFAULT '{}'::jsonb,
    per_block_cap_usd NUMERIC(10, 2) CHECK (per_block_cap_usd >= 0),
    interests JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_trip_user_pref UNIQUE (trip_id, user_id)
);

-- 5. ITINERARY OPTIONS
-- AI-generated activity candidates for each time block and subgroup, voted on by travelers.
CREATE TABLE itinerary_options (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    time_block VARCHAR(20) NOT NULL CHECK (time_block IN ('morning', 'afternoon', 'evening')),
    subgroup_participants JSONB NOT NULL DEFAULT '[]'::jsonb,
    split_reason TEXT,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN (
        'food', 'outdoors', 'culture', 'nightlife', 'sports', 'shopping', 'relaxation', 'tour'
    )),
    neighborhood VARCHAR(100),
    start_time TIME,
    end_time TIME,
    estimated_cost_usd NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (estimated_cost_usd >= 0),
    why_it_fits TEXT,
    is_final BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. VOTES
-- Ranked-choice Borda count votes (1st=3pts, 2nd=2pts, 3rd=1pt) cast by travelers.
CREATE TABLE votes (
    id BIGSERIAL PRIMARY KEY,
    option_id BIGINT NOT NULL REFERENCES itinerary_options(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rank INT NOT NULL CHECK (rank BETWEEN 1 AND 3),
    points INT NOT NULL CHECK (points IN (1, 2, 3)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_option_vote UNIQUE (user_id, option_id)
);

-- =============================================================================
-- INDEXES
-- Performance optimizations for frequent JOINs and WHERE lookups.
-- Note: Primary keys and UNIQUE columns are indexed automatically by PostgreSQL.
-- =============================================================================

CREATE INDEX idx_trips_creator ON trips(creator_id);
CREATE INDEX idx_trip_participants_trip ON trip_participants(trip_id);
CREATE INDEX idx_trip_participants_user ON trip_participants(user_id);
CREATE INDEX idx_user_preferences_trip ON user_preferences(trip_id);
CREATE INDEX idx_itinerary_options_trip_date ON itinerary_options(trip_id, date);
CREATE INDEX idx_votes_option ON votes(option_id);
CREATE INDEX idx_votes_user ON votes(user_id);
