# Gateway (Java HTTP entry point) :8080

**Job:** login, permissions, API routing. It is the only address the frontend talks to.

## What it does
1. **Login and registration** (`POST /api/v1/auth/login`, `/auth/register`). Passwords are hashed with PBKDF2-HMAC-SHA256 (120k iterations, random salt). A successful call returns a signed bearer token and the user (`id`, `name`, `role`).
2. **Teacher registration is gated by an invite code** (`TEACHER_INVITE_CODE`), so nobody can grant themselves the TEACHER role by editing a request.
3. **Permissions** (`AccessPolicy`): rejects calls the role may not make (for example a student creating office hours) before they reach a service. Services repeat the check, because the gateway is not their only possible caller.
4. **Routing** (`ServiceRoutes` + `ProxyHandler`): the first path segment decides the owner.

| Path prefix | Service |
|---|---|
| `rooms`, `buildings`, `teachers`, `office-hours` | CampusPlus :8081 |
| `issues`, `issue-categories` | FixMyCampus :8082 |
| `initiatives`, `clubs`, `recommendations`, `profile` | ProjectForge :8083 |

5. **Safe failure:** if a service is down or slow the gateway answers `503` with a readable message. `GET /api/v1/health` reports each service as `ok`, `degraded` or `down`.

## Layers
`presentation/` (`GatewayMain`, `ProxyHandler`) → `application/` (`AuthService`, `AccessPolicy`, ports) → `infrastructure/` (`SqliteUserRepository`, `Pbkdf2PasswordHasher`). `GatewayMain` is the composition root.

## Data
Own SQLite file `data/auth.db` with a single `users` table, see [docs/db-uml.md](docs/db-uml.md).

> **Deviation from the diagram:** the diagram shows no database for the entry point. Login needs somewhere to keep accounts, and AGENTS.md asks for one identity authority, so the gateway owns this small user store. The three domain databases (campus, issues, social) are untouched.
