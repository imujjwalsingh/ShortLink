# ShortLink

A URL shortener built as event-driven microservices: Spring Boot 3, MongoDB,
Redis, Kafka and Elasticsearch, behind a Spring Cloud Gateway. Built as a
portfolio project; each service's README has the design decisions and
interview talking points for that service.

**Design docs:** [High-Level Design](docs/HLD.md) (architecture,
requirements, capacity, trade-offs) · [Low-Level Design](docs/LLD.md)
(classes, schemas, APIs, algorithms, flow and sequence diagrams).

**Web app:** a React frontend in [`frontend/`](frontend/README.md)
(dashboard, link management, per-link analytics charts). Like any other
client, it goes through the gateway.

## Repository layout

```
ShortLink/
├── backend/              Spring Boot microservices (Maven multi-module)
│   ├── pom.xml           parent POM: versions, plugins, module list
│   ├── Dockerfile        one image recipe for every service
│   ├── api-gateway/
│   ├── auth-service/
│   ├── url-service/
│   ├── analytics-service/
│   └── notification-service/
├── frontend/             React + TypeScript web app (Vite, npm)
├── docs/                 HLD and LLD
├── scripts/              smoke-test.sh: end-to-end checks against the running stack
├── docker-compose.yml    infrastructure + the backend services
└── .env.example          overrides for docker-compose (secrets, host ports)
```

The two halves build independently: Maven in `backend/`, npm in
`frontend/`. The files that run or test the whole system stay at the root.

## Architecture

```
                         ┌──────────────────────────── api-gateway :8080 ───────────────────────────┐
  client ───HTTP────────▶│ routing · per-IP rate limits (Redis, fail-open) · CORS · XFF sanitising  │
                         └──────┬───────────────────────┬──────────────────────────┬────────────────┘
                                │ /api/auth/**          │ /api/urls/**, /{code}    │ /api/analytics/**
                                ▼                       ▼                          ▼
                         auth-service :8083      url-service :8081          analytics-service :8082
                         register · login        CRUD · redirect            stats API
                         issues JWT (HS256)      Redis cache-aside          (Elasticsearch aggs)
                           │   Mongo               │   Mongo · Redis             ▲   │
                           │                       │                             │   │
             user-registered (outbox)        link-clicks (fire-and-forget)       │   │ link-milestones
                           │                       └──────────── Kafka ──────────┘   │
                           ▼                                                         ▼
                         notification-service :8084  ◀───────────────────────────────┘
                         contact table (Mongo) · idempotent email (SMTP → Mailpit)
```

## Services

| Service | Port | Owns | Does |
|---|---|---|---|
| [api-gateway](backend/api-gateway/README.md) | 8080 | — | Single entry point: routes, rate-limits per client IP, CORS |
| [url-service](backend/url-service/README.md) | 8081 | `shortlink` DB | Short-code generation, link CRUD, the redirect, Redis cache, click events |
| [analytics-service](backend/analytics-service/README.md) | 8082 | `link-clicks` index | Consumes clicks into Elasticsearch, serves per-link stats, emits milestones |
| [auth-service](backend/auth-service/README.md) | 8083 | `shortlink_auth` DB | Registration, login, JWT issuance, `UserRegistered` events |
| [notification-service](backend/notification-service/README.md) | 8084 | `shortlink_notifications` DB | Welcome and click-milestone emails |
| [frontend](frontend/README.md) | 3000 | — | React web app (Vite dev server); calls only the gateway |

Each service owns its data; nobody reads another service's database. They
communicate only over HTTP (via the gateway) and Kafka, and there are no
Maven dependencies between them.

## Running it

```bash
docker compose up --build -d          # first build takes a few minutes
./scripts/smoke-test.sh                # 35 end-to-end checks through the gateway
MAILPIT=http://localhost:8025 ./scripts/smoke-test.sh   # ...plus email delivery
```

For the web app, with the stack up:
`cd frontend && npm install && npm run dev`, then open
http://localhost:3000 (needs Node.js 22.22+).

Open http://localhost:8025 to see the emails. Secrets fall back to
dev-only values; copy `.env.example` to `.env` to set real ones. If a port
is already taken on your machine (typically 27017 by a local MongoDB),
move it with the matching variable, e.g.
`MONGO_HOST_PORT=27018 docker compose up --build -d`; all of them are
listed in `.env.example`. `docker compose down` stops everything (add `-v`
to also delete the data volumes).

Running one service from your IDE: open `backend/` (its `pom.xml`) as the
Maven project, start the infra with
`docker compose up -d mongodb redis kafka elasticsearch mailpit`, export
`JWT_SECRET` (any string of 32+ bytes, the same for every service), and run
the module's `*Application` class. Defaults point at `localhost`.

Building and testing, from the repo root (or `cd` into the folder and drop
the `-f` / `--prefix`):

```bash
mvn -f backend/pom.xml clean install   # every service + 153 unit/slice tests, no Docker needed
npm --prefix frontend test             # frontend unit/component tests
```

## API walkthrough

```bash
B=http://localhost:8080

curl -X POST $B/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"correct-horse-battery"}'

TOKEN=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"correct-horse-battery"}' | sed 's/.*"accessToken":"\([^"]*\)".*/\1/')

curl -X POST $B/api/urls -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"originalUrl":"https://example.com/a/very/long/path","customAlias":"my-link"}'

curl -i $B/my-link                                    # 302 → https://example.com/a/very/long/path
curl $B/api/urls -H "Authorization: Bearer $TOKEN"    # your links, newest first
curl $B/api/analytics/links/my-link -H "Authorization: Bearer $TOKEN"   # clicks, uniques, referrers, devices
```

| Method | Path | Auth | |
|---|---|---|---|
| POST | `/api/auth/register` | — | `{email, password}` → 201 |
| POST | `/api/auth/login` | — | → `{accessToken, tokenType, expiresIn}` |
| GET | `/api/auth/me` | JWT | current account |
| POST | `/api/urls` | JWT | `{originalUrl, customAlias?, expiresAt?}` → 201 |
| GET | `/api/urls?page&size` | JWT | your links, paginated |
| GET | `/api/urls/{code}` | JWT, owner | link metadata |
| PUT | `/api/urls/{code}` | JWT, owner | `{originalUrl, expiresAt?}` |
| DELETE | `/api/urls/{code}` | JWT, owner | deactivate (idempotent) |
| GET | `/{code}` | — | 302 redirect (404 unknown/deactivated, 410 expired) |
| GET | `/api/analytics/links/{code}?days=30` | JWT, owner | stats for one link |
| GET | `/api/analytics/links?limit=10` | JWT | your most-clicked links |

Errors everywhere are RFC 9457 `application/problem+json`. Someone else's
link returns the same 404 as a link that doesn't exist.

## Event contracts (Kafka)

All payloads are JSON strings; every consumer ignores unknown fields, so
producers can add fields without a coordinated release.

| Topic (key) | Producer → consumer | Payload | Idempotency key |
|---|---|---|---|
| `link-clicks` (shortCode) | url-service → analytics | `eventId, shortCode, ownerId, occurredAt, referrer, userAgent, visitorHash` | random `eventId` per click = Elasticsearch `_id` |
| `user-registered` (userId) | auth-service → notification | `eventId, userId, email, occurredAt` | `user-registered:{userId}` |
| `link-milestones` (shortCode) | analytics → notification | `eventId, shortCode, ownerId, milestone, occurredAt` | `milestone:{shortCode}:{n}` |

Each consumer has a `<topic>.DLT` dead-letter topic: malformed events go
there immediately; transient failures retry with exponential backoff for
up to 2 minutes first.

## What happens when something breaks

| Down | Effect |
|---|---|
| Redis | Redirects keep working from Mongo (circuit breaker skips Redis after 5 failures; measured ~10 ms per redirect with Redis down). Gateway rate limiting fails open. Readiness stays UP. |
| Kafka | Redirects unaffected (click events go through a bounded in-memory queue and are dropped, and counted, if it fills). Registrations are kept in the outbox and published when Kafka returns. |
| Elasticsearch | Stats API returns 503; clicks wait in Kafka (retry, then DLT) and nothing else is affected. |
| SMTP | Emails retry, then dead-letter for replay; nothing else is affected. |
| auth-service | No new logins; existing tokens keep working, because every service validates JWTs locally. |

## Known limitations / next steps

- **Shared HS256 secret.** Every validating service could also mint tokens.
  Move to RS256 with a JWKS endpoint once services have separate owners.
- **No refresh tokens or revocation.** Access tokens live 1 hour.
- **Click delivery is best-effort** (bounded queue, no outbox) by design, so
  the redirect path never waits on a database write for analytics.
- **Sequential short codes** leak creation order and let anyone walk the
  redirect space; a keyed permutation of the counter would hide it.
- **Single-node, no-TLS infrastructure** in compose (Kafka RF=1,
  Elasticsearch security off). Fine for a laptop, not for production.
- **Spring Boot 3.3.4** is past its OSS support window; upgrading to the
  current 3.x line is the next maintenance task.
- **No integration tests against real infrastructure in the Maven build.**
  `scripts/smoke-test.sh` covers that against the compose stack;
  Testcontainers-based tests would bring it into CI.
