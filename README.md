# SDU Bağdar

Smart campus platform for SDU University: **CampusPlus** (free rooms, office hours), **FixMyCampus** (facility reports), **ProjectForge** (initiatives and clubs).

- Architecture, patterns and decisions: [docs/architecture.md](docs/architecture.md)
- Every service folder in `services/` has an `explanation.md` and a `docs/db-uml.md` (UML of its database)
- Development plan: [plan.md](plan.md), working rules: [agetns.md](agetns.md)

## Quick start
Requires JDK 17+ (tested on 25), Python 3.11+, `curl`, `openssl`.
```bash
scripts/start-all.sh
open http://localhost:5173
```
Logs are in `build/logs/`. Stop with `scripts/stop-all.sh`. Frontend-only mock mode: set `window.CAMPUSOS_CONFIG = { useMock: true }` before `js/app.js`.

## Tests
```bash
python3 tests/e2e_test.py
python3 -m unittest discover -s services/project-forge-python -t services/project-forge-python
```
