# Library Service

> Owns the user's game collection: statuses, ratings, platforms, and declared preferences (genre, tag, release-year). Runs the daily DUSTY sweep that auto-archives stale entries.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)
![License](https://img.shields.io/badge/License-MIT-blue)

**Port:** `8082` &nbsp;|&nbsp; **Database:** `library_db` (port `5433`)

## Responsibilities

- CRUD for the user's game collection (`user_games`).
- Status tracking: `PLAYING`, `BACKLOG`, `COMPLETED`, `DROPPED`, `WISHLIST`, `DUSTY`.
- Ratings (1–10), platforms, notes, playtime, dates.
- Declared preferences: genre, tag, release-year (replace-all PUT endpoints).
- Search + filtering (status, platform, search, genre).
- Daily DUSTY transition for games untouched for 90+ days.
- Statistics endpoint feeding the frontend `/profile/statistics` page.
- GDPR data-export and account-delete paths.

## Position in the System

```
Frontend ----via GW----> Library Service (8082) ----> library_db (5433)
Recommendation Svc -+         |
(forwards JWT)      ----> Game Service (8081, enrichment on addGame)
```

Library Service is the source of truth for user-owned data. Recommendation Service calls in to read rated games and declared preferences; Game Service is called only during `addGame` to cache the game's name, background image, and genres on the row.

## Tech Stack

- Java 17, Spring Boot 4.0
- Spring Data JPA with Hibernate
- PostgreSQL 17 with Flyway-managed migrations
- Spring Security OAuth 2 Resource Server for JWT validation
- `@Scheduled` cron for the DUSTY sweep (03:00 daily)

## Game Statuses

| Status      | Meaning                                                                      |
|-------------|------------------------------------------------------------------------------|
| `PLAYING`   | Currently playing. Setting this auto-sets `lastPlayed = now()`.              |
| `BACKLOG`   | Want to play.                                                                |
| `COMPLETED` | Finished.                                                                    |
| `DROPPED`   | Gave up.                                                                     |
| `WISHLIST`  | Want to buy.                                                                 |
| `DUSTY`     | Auto-assigned by `DustyTransitionScheduler` after 90 days of no updates. Cannot be set manually (`PUT` returns 400). |

Any update to a row resets the 90-day DUSTY clock.

## Database Schema

Schema managed by **Flyway** (`src/main/resources/db/migration/V*__*.sql`). Hibernate `ddl-auto: validate` in production.

```sql
user_games
  id, user_id (Keycloak UUID, VARCHAR, no FK),
  igdb_game_id (IGDB reference, no FK),
  game_name, background_image, released (cached from Game Service),
  status, rating (1-10), platform,
  date_added, last_played, playtime, notes,
  metadata_synced_at, created_at, updated_at

user_game_genres   user_game_themes   user_game_tags
  user_game_id FK (ON DELETE CASCADE), value VARCHAR(100), composite PK + reverse index

user_platforms
  id, user_id, platform_name, is_primary

user_genre_preferences   user_tag_preferences   user_release_year_preferences
  user_id + name                user_id + name             user_id + decade
```

`user_id` is the Keycloak UUID stored as `VARCHAR`. No cross-service foreign keys; the service stays independently deployable.

`game_name`, `background_image`, and `released` are denormalized onto `user_games` to avoid N+1 calls to Game Service when rendering the library. Multi-valued attributes (genres, themes, tags) live in dedicated `@ElementCollection` join tables with composite PK and a reverse index on the value column. Genre filtering runs as a JPQL `EXISTS` subquery with exact-match `LOWER(value) = :key`, no `LIKE` wildcards. `metadata_synced_at` is the single marker for the lazy-heal path; NULL means the row was added before Game Service responded.

## API Endpoints

All endpoints require JWT. `user_id` is always extracted from the `sub` claim, never from the request body.

### Collection

| Method | Path                              | Description                                                              |
|--------|-----------------------------------|--------------------------------------------------------------------------|
| GET    | `/api/v1/library/games`           | Filtered: `?status=`, `?platform=`, `?search=`, `?genre=`.               |
| POST   | `/api/v1/library/games`           | Add game. 409 on duplicate. Body validated (`@NotNull`, `@NotBlank`).    |
| PUT    | `/api/v1/library/games/{id}`      | Update status / rating / platform / etc. `DUSTY` rejected.               |
| DELETE | `/api/v1/library/games/{id}`      | Remove.                                                                  |

### Filtered views

`GET /api/v1/library/backlog` &nbsp;·&nbsp; `/playing` &nbsp;·&nbsp; `/completed` &nbsp;·&nbsp; `/wishlist` &nbsp;·&nbsp; `/dusty`

### Stats + metadata

| Method | Path                                | Description                                                                  |
|--------|-------------------------------------|------------------------------------------------------------------------------|
| GET    | `/api/v1/library/genres`            | Sorted distinct genres in the user's library.                                |
| GET    | `/api/v1/library/stats`             | Totals by status, average rating, distribution by genre + platform.          |
| GET    | `/api/v1/library/platforms`         | User's platforms.                                                            |
| POST   | `/api/v1/library/platforms`         | Add platform.                                                                |

### Declared preferences (cold-start signal)

| Method | Path                                            | Description                                       |
|--------|-------------------------------------------------|---------------------------------------------------|
| GET    | `/api/v1/library/genre-preferences`             | Read declared genres.                             |
| PUT    | `/api/v1/library/genre-preferences`             | Replace-all `{ "genres": [...] }`.                |
| GET    | `/api/v1/library/tag-preferences`               | Read declared tags.                               |
| PUT    | `/api/v1/library/tag-preferences`               | Replace-all `{ "tags": [...] }`.                  |
| GET    | `/api/v1/library/release-year-preferences`      | Read declared decade buckets.                     |
| PUT    | `/api/v1/library/release-year-preferences`      | Replace-all `{ "decades": [...] }`.               |

Replace-all writes use a bulk `DELETE` with `@Modifying(flushAutomatically, clearAutomatically)` so the transaction does not hit the `(user_id, name)` UNIQUE constraint on the subsequent insert.

### Internal service-to-service (no user JWT)

| Method | Path                                                            | Description                                              |
|--------|-----------------------------------------------------------------|----------------------------------------------------------|
| GET    | `/internal/library/users/{userId}/games`                        | Full games list (metadata-heal skipped).                 |
| GET    | `/internal/library/users/{userId}/platforms`                    | User platforms.                                          |
| GET    | `/internal/library/users/{userId}/preferences/genres`           | Declared genre preferences.                              |
| GET    | `/internal/library/users/{userId}/preferences/tags`             | Declared tag preferences.                                |
| GET    | `/internal/library/users/{userId}/preferences/release-years`    | Declared release-year-bucket preferences.                |

Used by the recommendation-service per-user worker, which runs scheduled without a user JWT. Protected by `InternalAuthFilter`: requires header `X-Internal-Token: {INTERNAL_SERVICE_TOKEN}` (constant-time compare, fail-closed when the env var is unset). The api-gateway has no route for `/internal/**`, so the paths are only reachable inside the docker network.

## Library-write event publishing

`LibraryWritePublisher` emits the changed `userId` (JWT-extracted) to the Redis `library-write` channel after every write that affects recommendations: `addGame`, `updateGame`, `removeGame`, and the three preference services (`GenrePreferenceService`, `TagPreferenceService`, `ReleaseYearPreferenceService`). Best-effort: a Redis outage is logged at WARN and the write still commits; recommendation-service's hourly stale-pool scan catches the user up later. Subscriber lives in recommendation-service (`LibraryWriteSubscriber`).

## Configuration

| Variable                  | Default                                          | Purpose                          |
|---------------------------|--------------------------------------------------|----------------------------------|
| `LIBRARY_SERVICE_PORT`    | `8082`                                           | Service port                     |
| `LIBRARY_DB_URL`          | `jdbc:postgresql://localhost:5433/library_db`    | Full JDBC URL (note port 5433!)  |
| `LIBRARY_DB_USERNAME`     | `postgres`                                       | DB user                          |
| `LIBRARY_DB_PASSWORD`     | _none_                                           | DB password                      |
| `DDL_AUTO`                | `validate`                                       | Hibernate DDL mode               |
| `KEYCLOAK_ISSUER_URI`     | `http://localhost:8080/realms/game-cellar`       | JWT issuer                       |
| `GAME_SERVICE_URL`        | `http://localhost:8081`                          | Used for enrichment on `addGame` |
| `INTERNAL_SERVICE_TOKEN`  | (required for /internal/** auth)                 | Shared secret accepted by `InternalAuthFilter` on `/internal/**`. Fail-closed when unset. |
| `REDIS_HOST`              | `localhost`                                      | Redis host (powers the library-write pub/sub channel). |
| `REDIS_PORT`              | `6379`                                           | Redis port. |
| `REDIS_PASSWORD`          | (required when Redis auth on)                    | Redis password. Library-write publisher silently no-ops if Redis is unreachable; rec-service hourly TTL scanner catches the user up later. |

## Run Locally

### Prerequisites

- Java 17+
- A running PostgreSQL 17 instance on port **5433** (not 5432) with a `library_db` database
- (Optional) A running Game Service if you want full enrichment on `addGame`

### Direct

```bash
./mvnw spring-boot:run
```

### Via Docker Compose

```bash
docker compose up library-service
```

## Tests

```bash
./mvnw test
```

Covers controllers, the DUSTY scheduler, the replace-all preference flow, and the stats aggregation.

## Security

- `user_id` is always extracted from the JWT `sub` claim via `JwtUtils.getUserId(authentication)`. Request body `user_id` is never trusted.
- Per-user data isolation is enforced at every read and write.

## License

[MIT](./LICENSE)
