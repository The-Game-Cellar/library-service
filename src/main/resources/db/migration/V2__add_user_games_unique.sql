-- Adds the UNIQUE (user_id, igdb_game_id) constraint that the UserGame entity declares
-- (`@Table(uniqueConstraints = @UniqueConstraint(...))`) but the live DB has been missing
-- ever since Flyway was first removed 2026-04-24 (the V2__rename_rawg_to_igdb migration that
-- introduced uk_user_game was deleted along with the rest of Flyway, and Hibernate
-- `ddl-auto: validate` only validates columns + types, not constraints, so the gap was
-- silent until the INFRA32 schema-drift audit surfaced it.
--
-- Service-layer pre-insert existence check + 409 mapping (LibraryService.addGame ->
-- GameAlreadyInCollectionException) catches the common case but is not race-safe under
-- concurrent addGame calls from the same user. This migration restores the DB-level
-- guarantee.
--
-- Pre-flight check verified zero current duplicates:
--   SELECT user_id, igdb_game_id, COUNT(*) FROM user_games
--   GROUP BY user_id, igdb_game_id HAVING COUNT(*) > 1;  -- 0 rows
-- so this ALTER applies cleanly without backfill.

ALTER TABLE user_games
    ADD CONSTRAINT uk_user_game UNIQUE (user_id, igdb_game_id);
