-- When the current status was set. updated_at moves on any edit (rating, notes, platform), so it
-- cannot say how long a game has been PLAYING or when it went DUSTY; this column only moves on a
-- status change, and the DUSTY job reads it instead of updated_at so an edit no longer rescues a
-- game from dust. Backfilled from updated_at as the closest truth on hand, date_added for rows
-- that predate the timestamp columns.

ALTER TABLE user_games ADD COLUMN status_changed_at TIMESTAMP(6);

UPDATE user_games SET status_changed_at = COALESCE(updated_at, date_added, CURRENT_TIMESTAMP);

ALTER TABLE user_games ALTER COLUMN status_changed_at SET NOT NULL;
