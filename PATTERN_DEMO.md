# Pattern demo output (plain prints)

Simple run of the three patterns with ordinary `println` output, no test framework.

- Code: [tests/java/PatternDemo.java](tests/java/PatternDemo.java)
- Run: `tests/run-pattern-demo.sh`
- Formal unit tests (17 pass/fail checks) are separate: [TEST_REPORT.md](TEST_REPORT.md), `tests/run-java-tests.sh`

## What to look for
- **Strategy:** the same slots and request, but each named strategy (`first`, `deadline`, `preferred`) picks a different result, switched at runtime through `StrategyRegistry`.
- **Adapter:** the legacy system emits pipe-separated text rows; `LegacyTimetableAdapter` turns them into `FreeInterval` objects through the `TimetableProvider` interface.
- **Decorator:** one `ReportProcessor` chain `validation -> duplicate -> notification -> base`. Output order shows each wrapper acting around the core save; a shorter chain skips steps.

## Output

```
=== STRATEGY (SchedulingStrategy) ===
open slots: [mon-10, tue-15, fri-09]
registered strategies: [first, deadline, preferred]
  strategy 'first' -> mon-10
  strategy 'deadline' -> mon-10
  strategy 'preferred' -> tue-15

=== ADAPTER (TimetableProvider <- LegacyTimetableAdapter) ===
legacy rows (incompatible format ROOM|WEEKDAY|HHMM_FROM|HHMM_TO|COURSE):
  D101|1|900|950|COURSE-100
  D101|1|1200|1250|COURSE-103
  D101|1|1500|1550|COURSE-106
adapter output via TimetableProvider, D101 Monday free intervals:
  FreeInterval[start=08:30, end=09:00]
  FreeInterval[start=09:50, end=12:00]
  FreeInterval[start=12:50, end=15:00]
  FreeInterval[start=15:50, end=18:00]

=== DECORATOR (ReportProcessor chain) ===
chain: validation -> duplicate -> notification -> base
report 1 (valid):
  [Base] saved report in D101
  [Notification] to u1: Report rep-d8b5ded6 received: Electrical in D101.
  [Validation/Audit] audit entry: SUBMITTED
report 2 (same room+category, other user -> duplicate):
  [Base] saved report in D101
  [Notification] to u2: Report rep-492cccca received: Electrical in D101.
  [Notification] to u1: Someone else reported the same Electrical problem in D101.
  [Validation/Audit] audit entry: SUBMITTED
report 3 (description too short -> rejected by validation):
  [Validation/Audit] audit entry: REJECTED
  rejected: Describe the problem in at least 10 characters.
chain with only 'notification':
  [Base] saved report in E202
  [Notification] to u4: Report rep-aca15677 received: Electrical in E202.
```
