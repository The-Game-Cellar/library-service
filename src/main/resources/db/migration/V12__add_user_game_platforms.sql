-- A library entry can be owned on several platforms. The single user_games.platform column
-- becomes an ordered element collection in the V7 shape: one row per platform, position keeping
-- the order the client sent, so the row at position 0 is the entry's main platform (what the old
-- single field showed). Today's value is copied to position 0 and the column is dropped, so the
-- table is the one source of truth. Duplicates within an entry are kept out by the service, not
-- by a constraint: Hibernate rewrites the rows of an ordered list on change, and a unique
-- (user_game_id, platform) would fire in the middle of a reorder.

CREATE TABLE user_game_platforms (
    user_game_id BIGINT NOT NULL,
    platform VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT user_game_platforms_pkey PRIMARY KEY (user_game_id, position),
    CONSTRAINT fk_user_game_platforms_user_game
        FOREIGN KEY (user_game_id) REFERENCES user_games(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_game_platforms_value ON user_game_platforms (platform);

INSERT INTO user_game_platforms (user_game_id, platform, position)
SELECT id, TRIM(platform), 0
FROM user_games
WHERE platform IS NOT NULL AND TRIM(platform) <> '';

ALTER TABLE user_games DROP COLUMN platform;
