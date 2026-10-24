-- A note is free text the app lets a user write up to 10 000 characters of; VARCHAR(255) refused
-- anything longer at the database. Postgres widens VARCHAR to TEXT in place, keeping every row.
ALTER TABLE user_games ALTER COLUMN notes TYPE TEXT;
