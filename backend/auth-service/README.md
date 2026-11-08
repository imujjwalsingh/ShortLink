# auth-service

Registration, login and JWT issuance. Port 8083; reached through the
gateway at `/api/auth/**`.

| Method | Path | Auth | |
|---|---|---|---|
| POST | `/api/auth/register` | — | `{email, password}` → 201 `{id, email, createdAt}` |
| POST | `/api/auth/login` | — | → `{accessToken, tokenType: "Bearer", expiresIn}` |
| GET | `/api/auth/me` | JWT | current account |

Emails are normalized (trimmed, lower-cased) and unique; passwords are 8–72
characters (bcrypt ignores everything past 72 bytes, so longer passwords are
rejected rather than silently truncated).

## Design decisions (interview talking points)

**Tokens: HS256, validated locally everywhere.** Claims are `iss`, `sub`
(user id; the only claim other services rely on), `email`, `iat`, `exp`
(1 h) and `jti`. url-service, analytics-service and this service each
validate signature, issuer and expiry themselves, so nothing trusts a
header the gateway set, and logins being down doesn't break requests with
an existing token. Trade-off: the shared secret lets any validating service
mint tokens; RS256 + JWKS is the upgrade path. The service refuses to start
without a secret of at least 32 bytes, and none is committed to the repo.

**No user enumeration.** Unknown email and wrong password return the same
401 and message, and an unknown email still runs a bcrypt comparison
against a dummy hash, so response time doesn't reveal which emails have
accounts. Registration's 409 does reveal an email exists, which is the
usual sign-up trade-off; the gateway's strict rate limit on `/api/auth/**`
(2 req/s per IP) limits both that and password guessing.

**A transactional outbox with no transactions.** The user document carries
`registrationEventPublished=false` from the moment it's inserted: one
document, so the user and its "event still to publish" marker are written
atomically without needing a replica set. `UserOutboxRelay` polls every 2 s,
publishes pending events in sign-up order, and flips the flag only after
Kafka acknowledges. Kafka down during sign-up means a late welcome email,
not a lost one (verified: registrations succeed with Kafka down and wait in
the outbox). Delivery is at-least-once; the deterministic
`eventId = user-registered:{userId}` lets notification-service drop
duplicates.
