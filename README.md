# Smart Market Watchlist

A watchlist that doesn't just show prices. It tells you what **actually deserves your attention** since you last looked, by scoring every move against *that stock's own* volatility. Java 17 / Spring Boot, zero-build dashboard.

## What's inside

- **Adaptive signal.** Each stock's normal daily move (sigma over the previous 60 sessions, seeded from real history so it works on the first request and survives restarts). A move is "meaningful" at `1.5 x sigma x sqrt(trading days away)`: the longer you've been away, the more drift is expected.
- **Divergence Radar + correlation map.** Pairs that normally move together but have decoupled recently, aligned by exchange-calendar **date**, not array position.
- **Signal Lab (backtest).** Replays the rule over a year with **no look-ahead** (each day is judged on the 30 sessions before it) and reports flag rate and next-day follow-through vs the all-days baseline.
- **Market Pulse.** A one-glance digest: breadth, leader/laggard, who is bucking the trend, what's near a 52-week high.
- **UI.** Animated dark/light dashboard, live-updating cards with price flashes, sparklines, interactive price chart (SMA 20/50, alert-day markers, crosshair), search-as-you-type, sort/filter with FLIP animation, list/card views, undo on remove, keyboard shortcuts (`/` search, `R` refresh, `Esc` close). Cached last dashboard renders instantly on reload.

## Accounts

Sign up / log in is built in (no extra dependencies): each account keeps its own saved watchlist across devices.

- Passwords: PBKDF2-HMAC-SHA256, 210,000 iterations, random salt, constant-time compare. Plaintext is never stored.
- Sessions: random 256-bit token in an `HttpOnly; SameSite=Lax` cookie (`Secure` over HTTPS); only its SHA-256 is stored; 30-day expiry.
- Brute force: 8 failed logins per email per 15 minutes, then HTTP 429. Errors never reveal whether an email exists.
- Guests still work with no account. Signing up moves the guest list into the new account; logging in shows the account's saved list.
- On Render use PostgreSQL (`JDBC_DATABASE_URL`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD`) so accounts survive redeploys; the default file H2 database is wiped on free instances. Neon or Supabase give a longer-lived free Postgres.
- Not included yet: password reset by email, delete account, signup rate limiting.

## Why it's fast

- One upstream call per symbol returns the quote **and** a year of closes (was three calls).
- Batch fetches run in parallel on a dedicated pool; duplicate in-flight requests are coalesced.
- Shared 45 s cache, kept warm by a background job; the dashboard is a **single** API call, usually served from memory.
- Circuit breaker: after an upstream failure we stop waiting on timeouts for 45 s and serve last-known (flagged *delayed*) or demo data.
- gzip, hand-rolled SVG charts (no libraries), no build step.

## Run

```bash
mvn spring-boot:run        # http://localhost:8080
mvn test
```

No API keys. Data: Yahoo Finance's public chart endpoint. If it is unreachable (some cloud hosts get blocked) the app falls back to **clearly labelled demo data** (`DEMO DATA` badge); set `DEMO_FALLBACK=false` to disable.

## Deploy on Render (Docker)

1. Push the repo, then **New > Blueprint** on Render (it reads `render.yaml`), or create a Docker web service manually.
2. Health check path: `/actuator/health`.
3. Optional persistence: free Render disks are ephemeral, so for durable watchlists add a Postgres database and set
   `JDBC_DATABASE_URL=jdbc:postgresql://HOST:5432/DB`, `JDBC_DATABASE_USERNAME`, `JDBC_DATABASE_PASSWORD`.

> Streamlit can't host a Java app (it's Python-only). Render, Railway, Fly.io or any Docker host work.

## API

| Method | Path | |
|---|---|---|
| GET | `/api/dashboard` | everything for the page in one response |
| GET | `/api/stocks/{symbol}` | detail: history, SMAs, indicators, backtest |
| GET | `/api/search?q=` | symbol/name search |
| POST | `/api/watchlist` `{symbol}` | add (validated against the provider) |
| POST | `/api/watchlist/bulk` | add many |
| DELETE | `/api/watchlist/{symbol}` | remove |
| POST | `/api/watchlist/seen` | reset the "since you last looked" baseline |

Identity is a client-generated UUID in `X-User-Id` (no accounts, a deliberate scope choice).

## Design decisions & limits

- The "last seen" baseline moves only when you leave the tab or press **Mark all seen**, never on background refreshes.
- Previous close is derived from the bar before today's session (Yahoo's `chartPreviousClose` is the close *before the range*, which would corrupt the day change).
- 30-session trailing windows and 10-day recent correlation are small samples; the UI says so when counts are low.
- Yahoo's endpoint is unofficial; the `YahooProvider` is isolated so another provider (Finnhub, Twelve Data) can be swapped in.
- Not investment advice.
