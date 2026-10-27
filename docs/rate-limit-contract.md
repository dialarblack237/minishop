# Rate limit contract (client ↔ server)

Reference for Lessons 8.2.2 and 8.2.3. What the client must do to cooperate with MiniShop's rate limiter.

## Server guarantees

Every refused request returns:

- **HTTP 429 Too Many Requests**
- **`Retry-After` header** — seconds until the next token is available. Computed from the token bucket (not a constant), so it is honest.
- **`X-RateLimit-Limit`** — total requests allowed in the current window.
- **`X-RateLimit-Remaining`** — tokens left (always `0` on a refusal).
- **`X-RateLimit-Reset`** — ISO timestamp when the full bucket is refilled.
- **`Content-Type: application/problem+json`** — RFC 7807 problem details body.
- **Body** — `{type, title, status, detail, instance, traceId}`. `traceId` matches the `X-Trace-Id` response header and the `traceId` in every app log line for that request.

## Client responsibilities

The client MUST:

1. **Read `Retry-After`** from the 429 response. Do not default to a constant — the server computed it from the bucket.
2. **Wait** that many seconds before retrying. A retry before the window is consumed by the rate limiter and extends the backoff.
3. **Retry with the SAME `Idempotency-Key`** as the original request. This is what makes a retry that actually succeeded idempotent-safe — the server refuses to create a second order (Lesson 6.4.1).
4. **Give up after 3 retries** (client-side policy — not the server's). If the rate limit hasn't lifted by then, the client returns an error to the user rather than spamming.
5. **Show the `traceId`** to the user on the final error (e.g., "Contact support with reference: dbffa6de"). This lets support find the exact request in logs.

## Client pseudo-code

```javascript
async function requestWithRetry(url, options, idempotencyKey) {
  let wait = 0;
  for (let attempt = 0; attempt < 3; attempt++) {
    if (wait) await sleep(wait * 1000);
    const res = await fetch(url, {
      ...options,
      headers: { ...options.headers, 'Idempotency-Key': idempotencyKey },
    });
    if (res.status === 429) {
      wait = parseInt(res.headers.get('Retry-After') || '5', 10);
      continue;
    }
    return res;
  }
  throw new Error('Rate limited — try again later');
}