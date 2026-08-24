# infra-audit

Centralized request/response audit logging for API calls, wired in via a Spring MVC interceptor.

## What it does (beginner)

`AuditConfiguration` plugs a class called `ApiLoggingInterceptor` into every request that hits the API, so it runs automatically without any controller having to ask for it.

Think of it like a **security checkpoint at a building entrance**. Every visitor heading to `/api/**` has to pass through the checkpoint first. Two doors are exempt — `/api/health` and `/api/metrics` — because those are just monitoring tools poking the server to see if it's alive, and logging that would be pure noise.

The checkpoint itself doesn't decide what to record — that logic lives in `ApiLoggingInterceptor`. `AuditConfiguration` only wires it in.

For every request, the interceptor does two things:

- **Before** the controller runs — notes the start time, attaches a **correlation ID** (a tracking number: if the caller didn't send one, it makes one up), and logs the method, URL, IP, and client info.
- **After** the request finishes — logs how long it took and the response status, and if something went wrong, logs the exception too.

Net effect: every API call gets a paper trail — who called it, what they sent, what came back, how long it took, and one shared ID to trace it end to end — without a single controller having to write that logging code itself.

## How it's wired in (advanced)

`AuditConfiguration implements WebMvcConfigurer` — Spring's extension hook for MVC config. Rather than replacing Spring's whole configuration, it overrides one method (`addInterceptors`) and Spring merges it into the default setup.

```java
registry.addInterceptor(apiLoggingInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/health", "/api/metrics");
```

This registers the bean into Spring's `HandlerInterceptor` chain, walked by `DispatcherServlet` around every matched request:

```
Filter → DispatcherServlet → preHandle → Controller → postHandle → View render → afterCompletion
```

`ApiLoggingInterceptor` hooks `preHandle` and `afterCompletion` (not `postHandle`), which matters: `afterCompletion` always runs, even if the controller threw — so the response/duration is logged whether or not the request succeeded.

Notes on the implementation:

- **MDC** (`MDC.put("correlationId", ...)`) stores the correlation ID in a thread-local map that the logging framework injects into every log line automatically — no need to pass it through every method signature. It's cleared in `afterCompletion`'s `finally`, which matters because servlet containers reuse threads across requests; skip the clear and the ID leaks into the next unrelated request's logs.
- **Request/response bodies** are normally single-read streams. Logging them while still letting the controller read them requires `ContentCachingRequestWrapper` / `ResponseWrapper` — this class checks for that wrapping via `instanceof`, but the actual wrapping has to happen earlier, in a `Filter` (filters run before `DispatcherServlet`; interceptors run after).
- **Echoing the correlation ID** back on the response header (`X-Correlation-ID`) lets a gateway or downstream service reuse it — a lightweight, log-based stand-in for full distributed tracing (OpenTelemetry, Zipkin).

## Is this the same thing as AOP?

Related, but not the same tool. Both let you run code "around" other code without touching it — the difference is where they're allowed to stand.

`HandlerInterceptor` only guards the **front door** — it knows about HTTP and can only wrap controller methods reachable by a URL. **AOP** is a floor manager who can stop *any* method call, whether or not it has anything to do with a visitor walking in.

| Aspect | HandlerInterceptor | Spring AOP |
|---|---|---|
| Layer | Web/servlet layer only | Any Spring-managed bean method |
| Mechanism | Registered in `InterceptorRegistry`, called directly by `DispatcherServlet` | Dynamic proxies (JDK or CGLIB) wrapping the method call |
| Scope | Only requests matching your path patterns | Any method matching a pointcut — HTTP call, scheduled job, message listener… |
| HTTP access | Direct — has `request`/`response` | None natively; needs `RequestContextHolder` |
| Typical use | Logging, auth checks, correlation IDs, CORS | `@Transactional`, `@Cacheable`, custom `@LogExecutionTime` |

Could this logging be done with AOP instead? Technically — an `@Around` advice on `@RestController` classes, pulling the request from `RequestContextHolder`. But it's the wrong tool: the interceptor gets the request/response for free and is the idiomatic Spring MVC pattern for this job. Both ultimately rest on the same idea — wrap and delegate — Spring's chain calls interceptors manually in sequence, while AOP builds a proxy that does the wrapping for you.

## Finding: PII / sensitive-data masking is incomplete

**Short answer:** partially, and only on one side of the exchange. It catches two field names in JSON request bodies — nothing else is masked.

`sanitizeRequestBody()` runs a regex looking for literal `"password"` and `"token"` keys and replaces the value with `***`. It's applied only to the logged request body, at debug level, for POST/PUT calls.

**Covered:**
- `password` field in a JSON request body
- `token` field in a JSON request body

**Not covered:**
- The **response body** — logged raw whenever status ≥ 400, unmasked
- **Query strings** — logged as-is (API keys, emails often ride here)
- Other PII fields: **email, ssn, apiKey, secret, phone, card number**
- **Non-JSON** bodies — form-urlencoded, multipart, XML skip the regex entirely

There's also a bug in the same method:

```java
if (body.toLowerCase().contains("password")) {
    body = body.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
}
```

The *check* is case-insensitive (`toLowerCase()`), but the *regex* that does the replacing is case-sensitive and matches only lowercase `"password"`. A body containing `"Password"` or `"PASSWORD"` passes the check, skips the replacement, and gets logged in full.

**If this needs to hold up under a real compliance or security review**, treat the current masking as a starting sketch, not a control: extend it to cover the response body and query string, widen the field list, fix the case mismatch, and handle non-JSON payloads — or move to a dedicated masking layer rather than hand-rolled regex per field name.
