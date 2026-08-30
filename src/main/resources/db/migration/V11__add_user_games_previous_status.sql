-- The status a game had before its current one, written on every transition next to
-- status_changed_at. Lets the UI say what a DUSTY game was before it gathered dust, and lets
-- "pick it up again" restore that status instead of assuming PLAYING. NULL means no transition
-- has been recorded since the column arrived; there is nothing to backfill it from.

ALTER TABLE user_games ADD COLUMN previous_status VARCHAR(255);
