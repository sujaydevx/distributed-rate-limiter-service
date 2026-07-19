# Simple Distributed Rate Limiter

## The problem this solves

An API with no rate limiting can't stop a single client from sending unlimited
requests. If the API runs on more than one server (which any real production
deployment does), an in-memory counter on each server can't help — each server
only knows about the requests it personally received. A client can multiply
their real limit just by getting routed to different servers by the load
balancer.

The fix: keep the counter somewhere every server instance can see and update
together — Redis — and make sure the check-and-update happens as a single
atomic step, so two requests arriving at the same instant can't both slip
through a race condition.

## How a client is identified (no login system needed)

Rate limiting needs an *identifier*, not *authentication* — it just needs
something consistent to count requests against, not proof of who someone is.
This project identifies clients by IP address (handling `X-Forwarded-For` so
it still works correctly behind a load balancer). For local testing, an
optional `X-Client-Id` header can override the IP, purely so you can simulate
multiple "users" from one machine with Postman or k6 — this header is **not**
authentication, since anyone can put anything in it.

Known, honest limitation: multiple real users behind the same IP (office
network, shared Wi-Fi) share one bucket. A production system would rate-limit
by authenticated user ID once a login system exists, falling back to IP only
for anonymous traffic. That's future scope, not built here.

## Request flow

1. Every request hits `RateLimitFilter` before it reaches any controller.
2. The filter resolves a client id (IP, or the testing header).
3. It asks `RateLimiterSelector` for whichever algorithm is configured
   (`TOKEN_BUCKET` or `FIXED_WINDOW` in `application.yml`).
4. That algorithm checks Redis and returns allow/deny + remaining + retry-after.
5. Allowed → the real controller method runs.
   Denied → the filter returns `429 Too Many Requests` directly with a
   `Retry-After` header, and the controller never runs.
6. Every decision is recorded as a Prometheus counter
   (`ratelimiter_decisions_total{algorithm, decision}`).

## The two algorithms

**Fixed Window** — count requests in a time bucket (e.g. "this 60s window").
Simple, and Redis's `INCR` command is already atomic on its own — no Lua
needed. Known trade-off: a client can burst their full limit at the very end
of one window and again at the very start of the next — two limits' worth of
requests in a short span ("boundary burst").

**Token Bucket** — each client has a bucket of tokens that refill continuously
over time, up to a capacity. Allows legitimate bursts (e.g. a page firing 5
API calls at once) while still enforcing a steady average rate — no window
boundary to exploit. This one genuinely needs a Lua script, because it has to
read two values, do math, and write them back as one atomic step — a
read-modify-write that a single Redis command can't do alone.

## Redis failure handling (current state)

If Redis is unreachable, each algorithm catches the exception and **fails
open** — lets the request through rather than blocking every user because
the rate limiter's own dependency had a problem. This is a deliberate
availability-over-strict-enforcement choice, logged when it happens.

**Planned next step, not built yet:** a circuit breaker, so that after several
consecutive Redis failures, the app stops even attempting to reach Redis for
a cooldown period (fail fast) instead of every request individually waiting
on a connection attempt first.

## Running it

```bash
# Start Redis + Prometheus + Grafana
cd infra
docker-compose up -d

# Run the app (from the project root)
./gradlew bootRun
```

Try it:
```bash
curl http://localhost:8080/api/hello -H "X-Client-Id: alice"
```
Send it more than `token-bucket.capacity` times quickly and you'll get a 429.

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (login: admin/admin) — Prometheus datasource
  is pre-configured; try querying `ratelimiter_decisions_total` in Explore.

Load test:
```bash
k6 run infra/k6/load-test.js
```

## Tests

```bash
./gradlew test
```
Both rate limiter implementations are unit tested with Redis mocked — no
real Redis needed to run the tests.

## Deliberately not included (and why)

- **No AOP / annotations for rate limiting** — a plain servlet `Filter` does
  the same job (intercept before the controller runs) using a standard,
  widely-known Spring MVC mechanism instead of an AOP proxy.
- **No user accounts, JWT, or tiers** — this is a public API with one limit
  for everyone; see "How a client is identified" above.
- **No circuit breaker yet** — see "Redis failure handling" above.
- **No Testcontainers** — plain Mockito is enough to prove the algorithm
  logic is correct; a real-Redis integration test is a reasonable next step,
  not a requirement for this scope.
