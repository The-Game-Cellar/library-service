-- A rating is 0.5 to 10 in steps of 0.5, so the column takes one decimal. The whole-number
-- ratings already stored cast as they are (7 becomes 7.0); the step and the range are enforced
-- on the request, not here.

ALTER TABLE user_games ALTER COLUMN rating TYPE NUMERIC(3,1);
