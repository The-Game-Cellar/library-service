-- Every platform a library entry is owned on belongs to the user's own platforms, which the
-- service keeps on every add and platform change. This adds the ones entries already carry that
-- the user's platforms lack, as non-primary like the service does; a platform already there keeps
-- its primary flag.

INSERT INTO user_platforms (user_id, platform_name, is_primary)
SELECT DISTINCT ug.user_id, ugp.platform, false
FROM user_games ug
JOIN user_game_platforms ugp ON ugp.user_game_id = ug.id
ON CONFLICT (user_id, platform_name) DO NOTHING;
