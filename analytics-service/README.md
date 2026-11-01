# analytics-service

Consumes `link-clicks` from Kafka, indexes each click into Elasticsearch,
serves per-link statistics, and emits `link-milestones` events. Port 8082;
reached through the gateway at `/api/analytics/**`.

| Method | Path | Auth | |
|---|---|---|---|
| GET | `/api/analytics/links/{code}?days=30` | JWT | `totalClicks`, `uniqueVisitors`, `clicksPerDay` (last N days, UTC, zero-filled), top `referrers` / `browsers` / `operatingSystems` / `devices` |
| GET | `/api/analytics/links?limit=10` | JWT | caller's total clicks and most-clicked links |

Every query filters on `ownerId` = the token subject, so asking about
someone else's link returns zeros, indistinguishable from an unclicked link.

## Design decisions (interview talking points)

**Exactly-once counting on an at-least-once pipe.** Kafka can redeliver.
Each click is indexed with `op_type=create` and `_id = eventId` (a UUID
minted per click by url-service), so a redelivered event fails with 409 and
is skipped: the click is counted once by construction, with no separate
dedupe store.

**Milestones need an exact counter, not a search count.** Elasticsearch
counts are near-real-time (refresh every ~1 s), so "count == 100" could be
skipped or seen twice. A per-link counter document is incremented with an
atomic scripted upsert (only for genuinely new clicks), and the milestone
fires when the returned value lands exactly on 1, 10, 100, …. If the
counter update fails after the click was stored, the retry sees a duplicate
and skips it, so the counter (used only for milestones) can drift one low.
The stats API counts the click documents themselves, which stay exact.

**Strict mappings, installed as index templates.** Keyword fields for
filters and terms aggregations, and a real `date` for the histogram.
Indexing into a missing index auto-creates it, and with dynamic mapping it
would get `text` fields that break term filters, so the mapping also lives
in an index template that applies even to auto-created indices.

**Error handling.** Malformed or incomplete events go straight to
`link-clicks.DLT`. Anything else (typically Elasticsearch down) retries in
place with exponential backoff (1 s → 30 s, up to 2 min), so a short outage
loses nothing and keeps per-partition order, then dead-letters for replay.
The HTTP API maps an unreachable Elasticsearch to 503, not 500.

**Only derived fields are stored**: referrer host, browser / OS / device
family (a small ordered rule set; Edge's UA contains "Chrome", iPhone's
contains "Mac OS X"), and the already-hashed visitor id. No raw user agents
or IPs. `uniqueVisitors` uses the cardinality aggregation (HyperLogLog++):
exact up to ~3000 distinct visitors, then within a few percent.
