# api-gateway

The only service exposed to clients (port 8080). Spring Cloud Gateway
(reactive) with Redis-backed rate limiting.

| Route | Target | Rate limit per client IP |
|---|---|---|
| `/api/auth/**` | auth-service | 2/s, burst 10 |
| `/api/urls`, `/api/urls/**` | url-service | 10/s, burst 30 |
| `/api/analytics/**` | analytics-service | 5/s, burst 20 |
| `GET /{code}` (`[A-Za-z0-9_-]+`) | url-service | 50/s, burst 100 |

Anything else (`/favicon.ico`, multi-segment paths, `POST /abc`) is a 404 at
the edge. Over the limit: 429.

## Design decisions (interview talking points)

**Routing only, no token validation.** Each downstream service validates
JWTs itself, so the gateway isn't a single point of trust. A request that
reaches a service some other way is still authenticated.

**Rate-limit buckets are keyed `{routeId}:{TCP peer address}`.** The route id
matters: `RedisRateLimiter` stores the bucket under the resolved key only,
while rate and burst come from whichever route is called, so an IP-only key
makes every route share one bucket. In docker-compose that meant 12 login
attempts got the next 10 redirects rejected with 429 (the auth route's burst
of 10 capped the shared bucket); `RateLimitConfigTest` guards the fix. The
address is the TCP peer, not `X-Forwarded-For` (anyone can send that header
and get a fresh bucket per request) and not the JWT subject (unverified
here, so forgeable). Behind a load balancer, switch to
`XForwardedRemoteAddressResolver.maxTrustedIndex(n)`.

**Client-supplied forwarding headers are stripped** (`X-Forwarded-For`,
`-Host`, `-Proto`, `-Port`, `-Prefix` and `Forwarded`), and the gateway adds
its own real values. url-service feeds the first XFF hop into the
unique-visitor hash and builds its `Location` headers from the forwarded
host, so without this a client could inflate unique-visitor counts or
inject a host into the URLs it gets back.

**Fail-open, and fail fast, when Redis is down.** The rate limiter allows
requests if Redis is unreachable, consistent with url-service's cache, and
readiness excludes Redis for the same reason. Lettuce is set to
`REJECT_COMMANDS` while disconnected: by default it queues commands during
reconnects, and in docker-compose that added ~350 ms to every request once
Redis went away after the gateway had connected (now ~12 ms, same as with
Redis up). Covered by `GatewayRoutingTest` (real gateway, stub downstream,
no Redis) and `RedisConfigTest`.

**Timeouts.** 2 s connect and 10 s response timeouts on proxied calls, so a
hung downstream gets a 504 instead of tying up the gateway.
