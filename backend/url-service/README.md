# url-service

Short-code generation, link CRUD, the public redirect, the Redis cache in
front of it, and `LinkClicked` events to Kafka. Port 8081; reached through
the gateway at `:8080`.

## Endpoints

| Method | Path | Auth | |
|---|---|---|---|
| POST | `/api/urls` | JWT | `{originalUrl, customAlias?, expiresAt?}` → 201 + `Location` |
| GET | `/api/urls?page=0&size=20` | JWT | caller's links, newest first (size ≤ 100) |
| GET | `/api/urls/{code}` | JWT, owner | metadata |
| PUT | `/api/urls/{code}` | JWT, owner | replace `originalUrl` / `expiresAt` |
| DELETE | `/api/urls/{code}` | JWT, owner | deactivate (soft delete, idempotent) |
| GET | `/{code}` | public | 302 (404 unknown or deactivated, 410 expired) |

Validation: `originalUrl` must parse as an absolute http(s) URL with a host
(max 2048 chars) and must not point back at the shortener; `customAlias` is
3–32 of `[A-Za-z0-9_-]` and not a reserved word (`api`, `actuator`,
`error`, …); `expiresAt` must be in the future.

## Running locally

```bash
docker compose up -d mongodb redis kafka        # from the repo root
export JWT_SECRET=any-string-of-at-least-32-bytes-shared-with-auth
mvn spring-boot:run                             # from backend/url-service
```
Set `ANALYTICS_ENABLED=false` to run without Kafka.

## Design decisions (interview talking points)

**Base62 over a Mongo counter, with the unique index as the real guarantee.**
Generated codes are `base62(N)`, where N comes from an atomic `findAndModify`
counter, so two generated codes never collide. Custom aliases share that
code space, though: alias `100` is exactly `base62(3844)`. So uniqueness
comes from the **unique index on `shortCode`**, not from the counter or an
`exists()` pre-check (check-then-insert lets two concurrent requests for the
same alias both pass). Every create is a plain insert:
- duplicate on a custom alias → 409;
- duplicate on a generated code → skip to the next counter value (bounded retries).

Spring Data MongoDB 3+ does **not** create `@Indexed` indexes unless
`spring.data.mongodb.auto-index-creation=true`. Before that was set, the
unique index silently didn't exist, so every lookup was a collection scan
and nothing actually prevented duplicates.

**Cache-aside that fails open *and* fast.** A Redis outage must make
redirects slower, not broken, and ideally not even slower. Three layers:
250 ms command/connect timeouts; Lettuce `REJECT_COMMANDS` while
disconnected (its default queues commands until they time out); and a small
circuit breaker that skips Redis for 30 s after 5 consecutive failures.
Measured with Redis down: redirects average ~10 ms. Readiness
(`/actuator/health/readiness`) deliberately excludes Redis so an orchestrator
never pulls the service for a cache outage.

**Version-checked cache writes fix the cache-aside race.** Classic race: a
reader loads a link from Mongo, a writer deactivates it and evicts the
cache, then the reader writes its stale copy back, and the dead link
redirects for a full TTL. Here every cache write (fill on miss, or
write-through after a mutation) runs a Lua script that stores the entry only
if its version is **strictly newer** than what Redis holds. The version is
Mongo's `@Version` optimistic-locking field, which also turns concurrent
edits into a 409 instead of a lost update. Unknown codes are negatively
cached (version −1, 60 s TTL), so a later create (version 0) replaces them
immediately.

**The redirect never waits on Kafka.** `KafkaTemplate.send()` is only async
once the producer has metadata; with the broker down it blocks for up to
`max.block.ms`. So the redirect thread just enqueues onto a 2-thread
executor with a bounded queue. If Kafka is down long enough to fill the
queue, events are dropped and counted (`shortlink.clicks.dropped`) rather
than exhausting the heap. Analytics are best-effort; redirects are not. An
outbox would make delivery guaranteed at the cost of a write per redirect.

**Privacy in click events.** The raw client IP never leaves this service:
events carry `visitorHash = HMAC(key, ip | user-agent)`. HMAC rather than a
plain hash, because the IPv4 space is small enough to brute-force a plain
SHA-256 back to the IP.

**Ownership.** `ownerId` is the JWT subject. Non-owners get the same 404 as
for a missing code, so the API never confirms a code is in use by someone
else. Links created before auth existed have no owner: they still redirect
but can't be managed.
