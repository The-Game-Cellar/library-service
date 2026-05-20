-- Cache release date on each user_games row so the recommendation-service can blend declared
-- release-era preferences with rating-evidence accumulated from rated games' actual eras.
-- Without this column the per-row release date would require an N+1 fan-out to Game Service
-- every time a user's profile is rebuilt. Stored as VARCHAR(20) holding either an ISO date
-- string (e.g. "2024-03-15"), the empty-string sentinel "" meaning "synced from Game Service,
-- no date upstream", or NULL meaning "never synced" so the existing lazy-heal path in
-- LibraryService.healStaleMetadata picks the row up on next read. Mirrors the genres / themes
-- / tags CSV columns' null vs empty-string convention.

ALTER TABLE user_games ADD COLUMN released VARCHAR(20);
