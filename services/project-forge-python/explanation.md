# ProjectForge (Python) :8083

**Owns:** initiatives, follows, clubs, memberships, club deadlines, user profiles (contacts, photo, notification settings) and the demo grade and notification feed.
Standard library only (`http.server`, `sqlite3`), so no framework, as AGENTS.md requires.

## Endpoints (`/api/v1`)
| Method + path | Notes |
|---|---|
| `GET /initiatives?q=&category=` | search with the caller's `following` flag |
| `POST /initiatives` | `{title, category, summary, contact}`; category is Project, Event, Party or Research |
| `POST /initiatives/follow` | `{id}` toggles follow, returns the updated initiative |
| `GET /recommendations?strategy=popularity\|recent\|category` | top 3, strategy chosen per request |
| `GET /clubs`, `POST /clubs`, `POST /clubs/toggle` | membership and nearest deadline; creating a club needs an AI-approved document and makes the caller its owner |
| `GET/PUT /clubs/manage` | club event page (photo, name, dates, description); PUT is owner only |
| `GET/POST/PUT /surveys`, `POST /surveys/delete` | minimal Google-Forms-style surveys (short answer, document upload, choice, checkbox); create, edit, delete are owner only |
| `POST /surveys/respond`, `GET /surveys/results`, `GET /surveys/file` | any user answers once (re-submitting overwrites); results and uploaded files (PDF, image, TXT, up to 5 MB) are owner only |
| `GET /profile`, `PUT /profile` | contacts, photo (max ~1 MB), four notification switches; the feed only contains enabled kinds |

## Pattern: Strategy (recommendations)
`RecommendationStrategy` is implemented by `PopularityStrategy`, `RecentActivityStrategy` and `CategoryBasedStrategy` (boosts categories the user already follows). `StrategyRegistry` selects one at runtime; a new strategy is one class plus `register()`. This is an extra, beyond the three patterns required for the assignment.

## Layers
`presentation/server.py` (HTTP, auth, error mapping) → `application/services.py` (rules, validation) → `domain/` (models, strategies, errors) ← `infrastructure/` (SQLite repositories, token verification). `main.py` is the composition root.

## Data
`data/social.db`, see [docs/db-uml.md](docs/db-uml.md).

## Known simplification
Grades and the notification feed are simulated demo data created on a user's first profile visit. Real grades would come from a university system, which is outside this project.
