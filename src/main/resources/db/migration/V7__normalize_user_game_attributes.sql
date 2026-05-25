-- Retire the denormalised user_games.genres / themes / tags CSV columns and replace them with
-- proper @ElementCollection join tables. Eliminates the substring-match bug in genre filtering
-- (LIKE '%rpg%' matched 'ARPG'), enables indexed lookups, and stops storing multi-valued
-- attributes as comma-separated text. Single source of truth from this migration on is the
-- per-dimension join table.
--
-- A single metadata_synced_at TIMESTAMP column on user_games replaces the trinary NULL vs
-- empty-string vs populated sentinel that the CSV columns carried (NULL = never synced,
-- empty = synced-but-none, populated = synced-with-values). Element collections always return
-- empty list, never null, so the heal-trigger needs a dedicated marker.

CREATE TABLE user_game_genres (
    user_game_id BIGINT NOT NULL,
    genres VARCHAR(100) NOT NULL,
    CONSTRAINT user_game_genres_pkey PRIMARY KEY (user_game_id, genres),
    CONSTRAINT fk_user_game_genres_user_game
        FOREIGN KEY (user_game_id) REFERENCES user_games(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_game_genres_value ON user_game_genres (genres);

CREATE TABLE user_game_themes (
    user_game_id BIGINT NOT NULL,
    themes VARCHAR(100) NOT NULL,
    CONSTRAINT user_game_themes_pkey PRIMARY KEY (user_game_id, themes),
    CONSTRAINT fk_user_game_themes_user_game
        FOREIGN KEY (user_game_id) REFERENCES user_games(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_game_themes_value ON user_game_themes (themes);

CREATE TABLE user_game_tags (
    user_game_id BIGINT NOT NULL,
    tags VARCHAR(100) NOT NULL,
    CONSTRAINT user_game_tags_pkey PRIMARY KEY (user_game_id, tags),
    CONSTRAINT fk_user_game_tags_user_game
        FOREIGN KEY (user_game_id) REFERENCES user_games(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_game_tags_value ON user_game_tags (tags);

INSERT INTO user_game_genres (user_game_id, genres)
SELECT id, TRIM(value)
FROM user_games, unnest(string_to_array(genres, ',')) AS value
WHERE genres IS NOT NULL AND genres <> '' AND TRIM(value) <> ''
ON CONFLICT DO NOTHING;

INSERT INTO user_game_themes (user_game_id, themes)
SELECT id, TRIM(value)
FROM user_games, unnest(string_to_array(themes, ',')) AS value
WHERE themes IS NOT NULL AND themes <> '' AND TRIM(value) <> ''
ON CONFLICT DO NOTHING;

INSERT INTO user_game_tags (user_game_id, tags)
SELECT id, TRIM(value)
FROM user_games, unnest(string_to_array(tags, ',')) AS value
WHERE tags IS NOT NULL AND tags <> '' AND TRIM(value) <> ''
ON CONFLICT DO NOTHING;

ALTER TABLE user_games ADD COLUMN metadata_synced_at TIMESTAMP;

UPDATE user_games
SET metadata_synced_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE genres IS NOT NULL OR themes IS NOT NULL OR tags IS NOT NULL;

ALTER TABLE user_games DROP COLUMN genres;
ALTER TABLE user_games DROP COLUMN themes;
ALTER TABLE user_games DROP COLUMN tags;
