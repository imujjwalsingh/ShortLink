# url-service

Service 1 of the **ShortLink** project — core CRUD + redirect, backed by MongoDB.
No Redis or Kafka yet; those get layered on in the next build steps without
changing this service's public API.

## Endpoints

| Method | Path                  | Purpose                                      |
|--------|-----------------------|-----------------------------------------------|
| POST   | `/api/urls`           | Create a short URL. Body: `{originalUrl, customAlias?, expiresAt?}` |
| GET    | `/api/urls/{code}`    | Fetch metadata for a short code               |
| DELETE | `/api/urls/{code}`    | Deactivate (soft-delete) a short code         |
| GET    | `/{code}`             | **Redirect** — 302 to the original URL        |

Example:

```bash
curl -X POST localhost:8081/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/very/long/path"}'
# -> {"shortCode":"1","shortUrl":"http://localhost:8081/1", ...}

curl -i localhost:8081/1
# -> HTTP/1.1 302 Found
#    Location: https://example.com/some/very/long/path
```

## Running locally

**Option A — Docker Compose (Mongo + Redis + service), from the repo root:**
```bash
cd ..   # if you're inside url-service/, go up to the repo root first
docker compose up --build
```

**Option B — Mongo + Redis in Docker, service via Maven (from url-service/):**
```bash
docker run -d -p 27017:27017 --name mongo mongo:7
docker run -d -p 6379:6379 --name redis redis:7-alpine
mvn spring-boot:run
```

**Option C — Mongo/Redis installed locally (Windows services etc.):**
From `url-service/`, just run `mvn spring-boot:run` — defaults point at
`localhost:27017` and `localhost:6379`, matching a local install of either.

## Running tests

```bash
mvn test
```

`UrlServiceTest` unit-tests the service layer with the repositories mocked
via Mockito — no real or embedded Mongo needed, no Spring context booted, so
it's fast and has zero infra dependency. (An earlier draft of this used
`de.flapdoodle.embed.mongo` for an embedded Mongo in a `@SpringBootTest`;
dropped it because Spring Boot removed its embedded-Mongo auto-configuration
back in 2.6+, so the dependency alone doesn't wire anything and the context
just tries — and fails — to reach a real Mongo at `localhost:27017`.
Testcontainers is the standard replacement if a true integration test against
a real Mongo becomes worth adding later.)

To manually verify the redirect/CRUD flow end-to-end against a real Mongo,
use `docker compose up` and the `curl` examples above.

## Design decisions (interview talking points)

**Base62-over-counter vs. hash-based short codes.**
Short codes are `base62(N)` where `N` comes from an atomically-incremented
MongoDB counter document (`findAndModify`, `upsert: true` — see
`CounterRepository`). This guarantees no collisions by construction, so there's
no retry loop on the write path. The trade-off: codes are roughly sequential,
which leaks approximate creation order/volume — a hash-based approach (e.g.
MD5 truncate) avoids that but needs a collision-retry loop and doesn't shorten
as predictably. For this project the counter approach is the cleaner story.

**Custom alias vs. generated code.**
If `customAlias` is supplied it's used verbatim (after a uniqueness check);
otherwise a code is generated from the counter. Both paths write through the
same unique index on `shortCode`, so uniqueness is enforced at the DB level
either way — the `existsByShortCode` check is just for a friendlier 409
instead of a raw duplicate-key error.

**Redis cache-aside (Step 2 — done).**
`UrlService.resolve()` checks `UrlCacheService` first; on a miss it hits
Mongo, then populates the cache before returning. `UrlCacheService` is
deliberately fail-open: any Redis error (connection refused, timeout,
serialization failure) is caught and logged, never propagated — a Redis
outage degrades the service to "every redirect hits Mongo", not "redirects
break". This is the main thing worth defending in an interview: a cache
should never be able to take down the path it's supposed to speed up.

Cache entries carry a TTL (`app.cache.ttl-seconds`, default 1 hour) as a
self-healing backstop on top of explicit invalidation — `deactivate()` calls
`cacheService.evict()` immediately, but if an invalidation path were ever
missed elsewhere, a stale entry still expires on its own rather than serving
a bad redirect indefinitely. This "explicit invalidation + TTL backstop"
combination is standard cache-aside practice and worth naming as such.

Only a lightweight `CachedUrlMapping` record (shortCode, originalUrl,
active, expiresAt) is cached — not the full Mongo document — since that's
all the redirect path needs to make its active/expired decision without a
second Mongo round trip.

**Where Kafka goes next (Step 3).**
The redirect handler in `UrlController` has a comment marking exactly where
the `LinkClicked` event gets published — after the redirect response is
built, fire-and-forget with an async producer callback that only logs
failures. The redirect must never block on, or fail because of, the Kafka
publish. This is also the point to discuss idempotency later: if Kafka
redelivers a message, does Analytics Service double-count a click? (Answer to
build toward: yes, unless the click event carries an idempotency key — e.g. a
hash of short_code+timestamp+client fingerprint — that Analytics dedupes on
before indexing into Elasticsearch.)

**Ownership / Auth.**
`ownerId` is nullable and hardcoded to `null` at the call site for now — Auth
Service + JWT wiring is step 7 in the build order. When that lands, swap the
`null` in `UrlController.createShortUrl` for the authenticated principal, and
add an ownership check to `deactivate`/edit endpoints.

## What's deliberately not here yet

- Kafka producer for `LinkClicked` (Step 3)
- Auth / ownership enforcement (Step 7)
- Edit endpoint (PUT) — add once ownership checks exist, otherwise anyone
  could edit anyone's link
- Cache stampede protection (e.g. a short-lived lock or request coalescing
  around a Mongo lookup on a very hot missing/expired key) — worth
  mentioning as a known gap if asked, not worth building for this scope
