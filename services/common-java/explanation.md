# common-java

Shared plumbing compiled into every Java service. It has no business logic.

| Class | Purpose |
|---|---|
| `Json` | Small JSON parser/serializer (no external library, per the no-framework rule) |
| `Tokens`, `AuthUser` | HS256 bearer tokens. The gateway issues them; **every** service verifies them (signature + expiry) and takes the user's id and role from the token, never from the request body |
| `HttpApp` | Routing over the JDK `HttpServer`: path params, auth, role checks, JSON errors, CORS, request log, `/health` |
| `Database` | SQLite access: one connection per call, `PRAGMA foreign_keys=ON`, transactions, `UNIQUE` violations mapped to HTTP 409 |
| `ServiceClient` | Service-to-service HTTP with a timeout; failures become HTTP 503 instead of hanging |
| `ApiException` | Error type that maps to a status code and `{message, code}` |
| `Env` | Reads configuration from environment variables only (`.env` is loaded by `scripts/start-all.sh`) |

Third-party libraries used by the whole project: `sqlite-jdbc` (+ `slf4j-api`, `slf4j-nop`) in `lib/`. Nothing else.
