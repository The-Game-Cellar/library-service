-- Records that a user has been through onboarding, so the route can be closed to
-- anyone who already has. Deliberately a row of its own rather than derived from
-- user_platforms: a user who owns no platforms would otherwise be sent back to
-- onboarding on every login, which is the behaviour this table exists to stop.
-- Same VARCHAR stand-in for the Keycloak UUID as every other table here, no FK.

CREATE TABLE user_onboarding (
    user_id VARCHAR(255) NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_onboarding_pkey PRIMARY KEY (user_id)
);

-- Backfill: an account that already has platforms or declared genres has plainly been
-- through the flow. Without this every existing account would be treated as new and
-- pushed back into onboarding the first time it loads a protected route.
INSERT INTO user_onboarding (user_id, completed_at)
SELECT user_id, CURRENT_TIMESTAMP FROM user_platforms
UNION
SELECT user_id, CURRENT_TIMESTAMP FROM user_genre_preferences
ON CONFLICT (user_id) DO NOTHING;
