# frontend

The ShortLink web app: sign in, shorten links, and see who clicks them.
React 19 + TypeScript, built with Vite. It talks only to the API gateway.

| Page | What it does |
|---|---|
| `/login`, `/register` | Sign in, or create an account (signs you straight in) |
| `/` | Shorten a link (optional custom alias and expiry), list your links with click counts, edit or deactivate them, most-clicked summary |
| `/links/:code` | One link: copy/open/edit/deactivate, plus analytics for the last 7, 30 or 90 days (clicks per day, unique visitors, referrers, browsers, OS, devices) |

Light and dark themes follow the OS setting, with a toggle in the header.

## Running it

Needs Node.js 22.22 or newer, and the backend running
(`docker compose up -d` from the repo root).

```bash
cd frontend
npm install        # first time only
npm run dev        # http://localhost:3000
```

| Script | Does |
|---|---|
| `npm run dev` | Dev server with hot reload on port 3000 |
| `npm run build` | Type-check, then a production build into `dist/` |
| `npm run preview` | Serve the production build on port 3000 |
| `npm test` | Unit and component tests (Vitest, jsdom) |
| `npm run lint` | Lint (oxlint) |

**Port 3000 is deliberate.** The browser calls the gateway at
`http://localhost:8080` directly, and the gateway's CORS policy allows only
the origin in `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`). The
dev server uses `strictPort`, so if 3000 is taken it stops with an error
instead of moving to 3001, where every API call would fail CORS.

**Pointing at another gateway:** copy `.env.example` to `.env.local` and set
`VITE_API_BASE_URL`. It's read at build time, so restart `npm run dev` or
rebuild after changing it. Then add the frontend's origin to the gateway's
`CORS_ALLOWED_ORIGINS`.

## Structure

```
src/
  api/          fetch client (problem+json -> ApiError), endpoints, DTO types, TanStack Query hooks
  auth/         session store, AuthProvider, route guards
  components/   layout, UI primitives (ui/), charts (charts/)
  features/     link create / list / edit / deactivate
  pages/        one component per route
  lib/          formatting, theme, query client
```

## Design decisions

**Calls the gateway directly, not through a dev proxy.** The gateway already
had a CORS policy for `localhost:3000`, so the dev server and a production
build make identical requests. A proxy (Vite's, or nginx in production)
would also make every user's API traffic come from the proxy's IP, so all
of them would share one rate-limit bucket at the gateway.

**Server state lives in TanStack Query.** Every API read is a query with a
key (`['links', page]`, `['stats', code, days]`). Mutations update or
invalidate exactly the keys they affect: editing a link writes the response
into its cache entry and refetches the list. No global store is needed.
4xx responses aren't retried (asking again won't help); network errors and
5xx are retried twice. Changing the page or date range keeps the previous
data on screen, dimmed, instead of flashing a spinner.

**One error shape.** Every backend service returns RFC 9457 problem+json.
The client turns any failure into an `ApiError` with a readable message and
a `fieldErrors` map built from the `errors` array, which forms show under
the matching input. The gateway's 429 has no body, and an unreachable
backend has no response at all; both get their own message.

**Session: a JWT in localStorage, ended on expiry.** The token's lifetime
comes from the login response's `expiresIn` rather than from decoding the
JWT, so the browser's clock doesn't have to agree with the server's. The
session ends ten seconds before expiry, on any 401 to a request that carried
a token, or when another tab signs out. The query cache is cleared on
sign-out, so the next account never sees the previous one's links. The
trade-off: localStorage is readable by any script on the page, so an XSS bug
would expose the token. An httpOnly cookie set by a backend-for-frontend
removes that risk; it's out of scope while tokens live one hour and there
are no refresh tokens.

**Charts follow a fixed visual spec.** Clicks per day is one series, so it
has no legend (the card title names it). It's drawn as a 2px straight line
over a 10% wash with a hairline grid and a crosshair tooltip. Smoothing is
off: a curve would start rising before the day the clicks happened. Days
are labeled in UTC, as analytics buckets them; local formatting would shift
every label by a day for anyone west of UTC. The chart has a table view, so
no value is only visible on hover. Breakdowns (referrers, browsers, OS,
devices) are ranked bars in one color with the value printed at the end of
each bar. Colors are CSS variables, so charts switch with the theme without
re-rendering.

**The dashboard degrades without analytics.** Click counts come from one
"top links" call (limit 100) rather than one call per row, which would hit
the analytics rate limit (5/s, burst 20) on a single page. A link missing
from a list shorter than 100 has zero clicks; missing from a full list, its
count shows as "–" rather than a wrong 0. If analytics-service is down, the
dashboard still works and the counts show as "–".

**The charts load only when needed.** The link page, which carries the
charting library, is a lazy route, so signing in and the dashboard download
about half the JavaScript.

## Tests

`npm test` runs 37 tests in a time zone behind UTC (`America/Los_Angeles`),
where date bugs show up. They cover:

- formatting
- click-count lookup
- the API client's error handling (problem+json, 429, network failure, 401
  ending the session)
- session persistence and expiry
- the sign-in flow through the real router and guards (redirect to sign-in,
  server error messages, return to the page you came from, "session
  expired" notice)
