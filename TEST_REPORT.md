# Test Report: SDU Bağdar

**Date of run:** 2026-10-23
**Environment:** macOS (Darwin 24.5), Java 25.0.1 LTS, Python 3.14.6, SQLite via `sqlite-jdbc` 3.46.1.3
**Scope:** the three design patterns required by the assignment (Strategy, Adapter, Decorator), plus the end-to-end and service-level suites.

## 1. Summary

| Suite | Tests | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| **Design-pattern unit tests (Java)** | 17 | 17 | 0 | 0 |
| End-to-end through the gateway | 14 | 13 | 0 | 1 * |
| ProjectForge unit tests (Strategy, Python) | 2 | 2 | 0 | 0 |
| AI validation unit tests | 8 | 8 | 0 | 0 |
| Mailer unit tests | 4 | 4 | 0 | 0 |
| **Total** | **45** | **44** | **0** | **1** |

\* `test_teacher_registration_needs_invite_code` skips itself when `SDU_BAGDAR_SKIP_TEACHER_INVITE=true`, which is the development default in `.env`. Set it to `false` to run this test.

**Result: all executed tests passed.**

## 2. How to reproduce

```bash
tests/run-java-tests.sh                                  # section 3, needs no running services
scripts/start-all.sh                                     # for section 4
python3 tests/e2e_test.py -v
python3 -m unittest discover -s services/project-forge-python -t services/project-forge-python -v
python3 -m unittest discover -s services/ai-validation-python -t services/ai-validation-python -v
python3 -m unittest discover -s services/mailer-python -t services/mailer-python -v
scripts/stop-all.sh
```

The Java tests are in [tests/java/PatternTests.java](tests/java/PatternTests.java). They use no test framework and no database: repositories and the notification and audit ports are replaced by in-memory fakes, so each test runs in milliseconds.

## 3. Design-pattern unit tests (Java)

Command: `tests/run-java-tests.sh`

### 3.1 Strategy: `SchedulingStrategy` (CampusPlus)

Classes under test: `FirstAvailableStrategy`, `EarliestDeadlineStrategy`, `PreferredTimeStrategy`, `StrategyRegistry`.

| ID | Test | Input | Expected | Result |
|---|---|---|---|---|
| S1 | First picks earliest weekly slot | Wed 09:00, Mon 14:00, Mon 10:00 | Mon 10:00 | PASS |
| S2 | Deadline strategy respects the deadline | Fri 09:00, Tue 09:00; deadline Fri, then a deadline already past | Tue slot; then no slot | PASS |
| S3 | Preferred picks closest start time | Mon 09:00, Mon 15:00; preferred 14:30 | Mon 15:00 | PASS |
| S4 | Preferred with no time given | one slot, no preferred time | empty result | PASS |
| S5 | Registry switches at runtime | look up each name; look up an unknown name | strategy returned per name; unknown rejected | PASS |
| S6 | New strategy plugs in | register a fourth strategy | works without editing existing classes | PASS |

### 3.2 Adapter: `TimetableProvider` (CampusPlus)

Classes under test: `LegacyTimetableAdapter` wrapping the unmodified `LegacyTimetableSystem` (pipe-separated rows, weekday numbers, `HHMM` integers).

| ID | Test | Input | Expected | Result |
|---|---|---|---|---|
| A1 | Legacy rows become free intervals | legacy lessons for a room | correct `FreeInterval`s between the lessons | PASS |
| A2 | Room without lessons | room with no legacy rows | free all day | PASS |
| A3 | Used only through the interface | adapter held as `TimetableProvider` | the caller never sees the legacy format | PASS |

### 3.3 Decorator: `ReportProcessor` (FixMyCampus)

Classes under test: `BaseReportProcessor`, `ValidationAuditDecorator`, `DuplicateAlertDecorator`, `NotificationDecorator`, `ReportPipelineFactory`.

| ID | Test | Input | Expected | Result |
|---|---|---|---|---|
| D1 | Base processor only stores | empty decorator list | one stored report, no audit, no notifications | PASS |
| D2 | Validation decorator | unknown category; too-short description; then a valid report | two `422` rejections, both audited, nothing stored; the valid report is audited `SUBMITTED` | PASS |
| D3 | Duplicate decorator | two reports, same room and category, different users | second links to the first (`duplicateOf`); first reporter is alerted | PASS |
| D4 | Notification decorator | one valid report | reporter receives one notification | PASS |
| D5 | All three combined | `validation, duplicate, notification` and two similar reports | 2 stored; 2 `SUBMITTED` audits; u1 has 2 messages, u2 has 1 | PASS |
| D6 | Order and subset are configurable | `notification, validation` | still stores, audits and notifies | PASS |
| D7 | Unknown decorator name | `magic` | `IllegalArgumentException` | PASS |
| D8 | New decorator added without edits | custom wrapper around the chain | runs before and after the chain; existing decorators unchanged | PASS |

### 3.4 Actual output

```
ok      strategy: first picks earliest weekly slot
ok      strategy: deadline skips slots after the deadline
ok      strategy: preferred picks closest start time
ok      strategy: preferred without a time yields nothing
ok      strategy: registry switches at runtime and rejects unknown names
ok      strategy: a new strategy plugs in without changing existing code
ok      adapter: legacy rows become free intervals
ok      adapter: room without lessons is free all day
ok      adapter: used through the TimetableProvider interface only
ok      decorator: base processor only stores
ok      decorator: validation rejects bad category/description and audits it
ok      decorator: duplicate links to the open report and alerts its author
ok      decorator: notification tells the reporter
ok      decorator: full stack combines all three
ok      decorator: order and subset are configurable
ok      decorator: unknown decorator name is rejected
ok      decorator: a new decorator wraps the chain without modifying it

17 passed, 0 failed
```

## 4. End-to-end tests (full stack through the gateway)

Command: `python3 tests/e2e_test.py -v`. Runs against the real services and SQLite databases.

| Test | Area | Result |
|---|---|---|
| `test_all_services_healthy` | Health of all services | PASS |
| `test_login_rejects_wrong_password_and_protects_routes` | Auth, `401` without token | PASS |
| `test_teacher_registration_needs_invite_code` | Teacher invite code | SKIPPED (invite check disabled in `.env`) |
| `test_campus_has_only_blocks_d_to_i` | CampusPlus data | PASS |
| `test_free_rooms_come_through_the_timetable_adapter` | **Adapter** | PASS |
| `test_room_filters_and_barrels` | Room filters | PASS |
| `test_office_hours_rules` | Office hours, overlap `409`, role check | PASS |
| `test_strategy_pattern_selects_slots_at_runtime` | **Strategy** | PASS |
| `test_decorator_pipeline_validates_alerts_and_notifies` | **Decorator** | PASS |
| `test_validation_and_service_boundary` | Input validation, `422`, unknown room | PASS |
| `test_only_teachers_review` | Authorization, `403` for students | PASS |
| `test_initiative_lifecycle` | Create, search, follow | PASS |
| `test_recommendation_strategies_switch_at_runtime` | **Strategy** (ProjectForge) | PASS |
| `test_clubs_and_profile` | Clubs, profile | PASS |

```
Ran 14 tests in 0.479s

OK (skipped=1)
```

## 5. Python service unit tests

| Suite | Tests | Cases covered | Result |
|---|---:|---|---|
| ProjectForge `test_recommendation.py` | 2 | each strategy orders differently; registry selects at runtime and is extensible | PASS |
| AI validation `test_validators.py` | 8 | accepts plain text; rejects fake PDF, unsupported type, corrupt base64; rejects short description; accepts plausible issue; rejects bad evidence; marks local engine and rejects unknown kind | PASS |
| Mailer `test_mailer.py` | 4 | builds mail with code; rejects non-SDU address and header injection; rejects malformed code; hands mail to the sender | PASS |

## 6. Requirement coverage

| Requirement | Evidence |
|---|---|
| Strategy pattern, with runtime switching | S1 to S6, e2e strategy tests, ProjectForge unit tests |
| Adapter pattern, external component unmodified | A1 to A3, e2e adapter test |
| Decorator pattern, combinable decorators | D1 to D8, e2e decorator test |
| Input validation and error responses | D2, e2e validation test |
| Authorization by role | e2e `only_teachers_review`, `office_hours_rules` |
| Persistence | e2e tests write to SQLite through the real services |
| Service-failure handling (`503`) | Gateway and `ServiceClient` return `503` instead of hanging by design; not covered by an automated test (see below) |

## 7. Known gaps

- The teacher invite-code test is skipped in the default configuration (see the note under the summary table).
- A service being down (`503`) is handled by design (`ServiceClient` timeouts) but not covered by an automated test.
- Persistence across a service restart is not yet an automated test.
- No browser (UI) tests.
