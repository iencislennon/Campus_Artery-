# SDU Bağdar

Smart campus platform for SDU University: **CampusPlus** (free rooms, office hours), **FixMyCampus** (facility reports), **ProjectForge** (initiatives and clubs).

- Architecture, patterns and decisions: [docs/architecture.md](docs/architecture.md)
- Every service folder in `services/` has an `explanation.md` and a `docs/db-uml.md` (UML of its database)
- Full project report (requirements, UML, database design, tests, user guide): [docs/SDU_Bagdar_Report.pdf](docs/SDU_Bagdar_Report.pdf) (source: `docs/report/report.html`)
- Development plan: [plan.md](plan.md)

## Quick start
Requires JDK 17+ (tested on 25), Python 3.11+, `curl`, `openssl`.
```bash
scripts/start-all.sh
open http://localhost:5173
```
Verification codes are e-mailed through the Mailer service. Without SMTP settings in `.env`, set `MAIL_DELIVERY=console` and read the code with `grep "\[auth\]" build/logs/gateway.log | tail -1`.
Logs are in `build/logs/`. Stop with `scripts/stop-all.sh`. Frontend-only mock mode: set `window.CAMPUSOS_CONFIG = { useMock: true }` before `js/app.js`.

## Tests
```bash
tests/run-java-tests.sh                       # Strategy / Adapter / Decorator unit tests (Java)
python3 tests/e2e_test.py                     # end-to-end through the gateway (stack must be running)
python3 -m unittest discover -s services/project-forge-python -t services/project-forge-python
```
