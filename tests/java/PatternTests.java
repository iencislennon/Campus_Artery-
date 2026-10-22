import campusos.campusplus.application.RoomService;
import campusos.campusplus.application.StrategyRegistry;
import campusos.campusplus.domain.*;
import campusos.campusplus.infrastructure.legacy.LegacyTimetableAdapter;
import campusos.campusplus.infrastructure.legacy.LegacyTimetableSystem;
import campusos.common.ApiException;
import campusos.fixmycampus.application.ReportRepository;
import campusos.fixmycampus.application.SupportPorts.AuditRepository;
import campusos.fixmycampus.application.SupportPorts.CategoryRepository;
import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import campusos.fixmycampus.application.processing.*;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportProcessor;
import campusos.fixmycampus.domain.ReportStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Dependency-free unit tests for the Strategy, Adapter and Decorator patterns. Run: tests/run-java-tests.sh */
public final class PatternTests {
    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("strategy: first picks earliest weekly slot", PatternTests::strategyFirst);
        run("strategy: deadline skips slots after the deadline", PatternTests::strategyDeadline);
        run("strategy: preferred picks closest start time", PatternTests::strategyPreferred);
        run("strategy: preferred without a time yields nothing", PatternTests::strategyPreferredMissingTime);
        run("strategy: registry switches at runtime and rejects unknown names", PatternTests::strategyRegistry);
        run("strategy: a new strategy plugs in without changing existing code", PatternTests::strategyExtensible);
        run("adapter: legacy rows become free intervals", PatternTests::adapterConvertsRows);
        run("adapter: room without lessons is free all day", PatternTests::adapterEmptyRoom);
        run("adapter: used through the TimetableProvider interface only", PatternTests::adapterViaInterface);
        run("decorator: base processor only stores", PatternTests::decoratorBase);
        run("decorator: validation rejects bad category/description and audits it", PatternTests::decoratorValidation);
        run("decorator: duplicate links to the open report and alerts its author", PatternTests::decoratorDuplicate);
        run("decorator: notification tells the reporter", PatternTests::decoratorNotification);
        run("decorator: full stack combines all three", PatternTests::decoratorStack);
        run("decorator: order and subset are configurable", PatternTests::decoratorOrderAndSubset);
        run("decorator: unknown decorator name is rejected", PatternTests::decoratorUnknown);
        run("decorator: a new decorator wraps the chain without modifying it", PatternTests::decoratorExtensible);

        System.out.println("\n" + passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("FAILED: " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ---------- Strategy ----------
    private static OfficeHour slot(String id, DayOfWeek day, String start) {
        LocalTime s = LocalTime.parse(start);
        return new OfficeHour(id, "t1", "Teacher", day, s, s.plusHours(1), 5, 0, false);
    }

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19); // a Monday

    private static void strategyFirst() {
        var slots = List.of(slot("c", DayOfWeek.WEDNESDAY, "09:00"), slot("b", DayOfWeek.MONDAY, "14:00"), slot("a", DayOfWeek.MONDAY, "10:00"));
        check("a".equals(new FirstAvailableStrategy().choose(slots, new SchedulingRequest(MONDAY, null, null)).orElseThrow().id()), "expected Monday 10:00");
    }

    private static void strategyDeadline() {
        var slots = List.of(slot("fri", DayOfWeek.FRIDAY, "09:00"), slot("tue", DayOfWeek.TUESDAY, "09:00"));
        var strategy = new EarliestDeadlineStrategy();
        check("tue".equals(strategy.choose(slots, new SchedulingRequest(MONDAY, MONDAY.plusDays(4), null)).orElseThrow().id()), "soonest slot expected");
        check(strategy.choose(slots, new SchedulingRequest(MONDAY, MONDAY.minusDays(1), null)).isEmpty(), "nothing before an expired deadline");
    }

    private static void strategyPreferred() {
        var slots = List.of(slot("morning", DayOfWeek.MONDAY, "09:00"), slot("afternoon", DayOfWeek.MONDAY, "15:00"));
        var chosen = new PreferredTimeStrategy().choose(slots, new SchedulingRequest(MONDAY, null, LocalTime.of(14, 30)));
        check("afternoon".equals(chosen.orElseThrow().id()), "15:00 is closest to 14:30");
    }

    private static void strategyPreferredMissingTime() {
        check(new PreferredTimeStrategy().choose(List.of(slot("a", DayOfWeek.MONDAY, "09:00")), new SchedulingRequest(MONDAY, null, null)).isEmpty(), "no preferred time -> no choice");
    }

    private static StrategyRegistry registry() {
        return new StrategyRegistry().register(new FirstAvailableStrategy()).register(new EarliestDeadlineStrategy()).register(new PreferredTimeStrategy());
    }

    private static void strategyRegistry() {
        var registry = registry();
        var slots = List.of(slot("early", DayOfWeek.MONDAY, "09:00"), slot("late", DayOfWeek.MONDAY, "16:00"));
        var request = new SchedulingRequest(MONDAY, null, LocalTime.of(16, 0));
        // same caller code, different behaviour depending on the name chosen at runtime
        check("early".equals(registry.get("first").choose(slots, request).orElseThrow().id()), "first");
        check("late".equals(registry.get("preferred").choose(slots, request).orElseThrow().id()), "preferred");
        expectError(422, () -> registry.get("random"));
    }

    private static void strategyExtensible() {
        SchedulingStrategy lastSlot = new SchedulingStrategy() {
            @Override public String name() { return "last"; }
            @Override public Optional<OfficeHour> choose(List<OfficeHour> open, SchedulingRequest r) {
                return open.stream().max(Comparator.comparing(OfficeHour::start));
            }
        };
        var registry = registry().register(lastSlot);
        var slots = List.of(slot("early", DayOfWeek.MONDAY, "09:00"), slot("late", DayOfWeek.MONDAY, "16:00"));
        check("late".equals(registry.get("last").choose(slots, new SchedulingRequest(MONDAY, null, null)).orElseThrow().id()), "new strategy used");
        check(registry.names().containsAll(Set.of("first", "deadline", "preferred", "last")), "old strategies untouched");
    }

    // ---------- Adapter ----------
    private static void adapterConvertsRows() {
        var legacy = new LegacyTimetableSystem(List.of("D101"));
        var rows = legacy.exportScheduleRows();
        check(!rows.isEmpty() && rows.get(0).contains("|"), "legacy format is pipe-separated text");
        String[] cols = rows.get(0).split("\\|");
        DayOfWeek day = DayOfWeek.of(Integer.parseInt(cols[1]));
        int from = Integer.parseInt(cols[2]);
        LocalTime lessonStart = LocalTime.of(from / 100, from % 100);
        var free = new LegacyTimetableAdapter(legacy).freeIntervals("D101", day);
        check(free.stream().noneMatch(i -> i.covers(lessonStart)), "room is busy during a legacy lesson");
    }

    private static void adapterEmptyRoom() {
        var free = new LegacyTimetableAdapter(new LegacyTimetableSystem(List.of("D101"))).freeIntervals("Z999", DayOfWeek.MONDAY);
        check(free.size() == 1 && free.get(0).start().equals(RoomService.DAY_OPENS) && free.get(0).end().equals(RoomService.DAY_CLOSES), "unknown room has no lessons");
    }

    private static void adapterViaInterface() {
        TimetableProvider provider = new LegacyTimetableAdapter(new LegacyTimetableSystem(List.of("E101")));
        check(!provider.freeIntervals("E101", DayOfWeek.TUESDAY).isEmpty(), "application sees only TimetableProvider");
    }

    // ---------- Decorator ----------
    private static final class FakeReports implements ReportRepository {
        final List<Report> stored = new ArrayList<>();
        public Report insert(Report r) { stored.add(r); return r; }
        public List<Report> listByReporter(String id) { return stored.stream().filter(r -> r.reporterId().equals(id)).toList(); }
        public List<Report> listAll() { return stored; }
        public Optional<Report> find(String id) { return stored.stream().filter(r -> r.id().equals(id)).findFirst(); }
        public Optional<Report> findOpenDuplicate(String room, String cat) {
            return stored.stream().filter(r -> r.roomId().equals(room) && r.category().equals(cat) && r.status() == ReportStatus.OPEN).findFirst();
        }
        public void updateStatus(String id, ReportStatus s, String a) {}
    }

    private static final class Fixture {
        final FakeReports reports = new FakeReports();
        final List<String> audit = new ArrayList<>();
        final Map<String, List<String>> inbox = new HashMap<>();
        final ReportPipelineFactory factory = new ReportPipelineFactory(reports, () -> List.of("Electrical", "Plumbing"),
                (reportId, actor, action, detail) -> audit.add(action),
                new NotificationRepository() {
                    public void add(String user, String msg) { inbox.computeIfAbsent(user, k -> new ArrayList<>()).add(msg); }
                    public List<String> forUser(String user) { return inbox.getOrDefault(user, List.of()); }
                });
    }

    private static Report draft(String room, String category, String text, String user) { return Report.draft(room, category, text, user); }

    private static void decoratorBase() {
        var f = new Fixture();
        f.factory.build(List.of()).process(draft("D101", "Electrical", "Lamp is broken here", "u1"));
        check(f.reports.stored.size() == 1 && f.audit.isEmpty() && f.inbox.isEmpty(), "no side effects without decorators");
    }

    private static void decoratorValidation() {
        var f = new Fixture();
        var processor = f.factory.build(List.of("validation"));
        expectError(422, () -> processor.process(draft("D101", "Magic", "Lamp is broken here", "u1")));
        expectError(422, () -> processor.process(draft("D101", "Electrical", "short", "u1")));
        check(f.reports.stored.isEmpty() && f.audit.equals(List.of("REJECTED", "REJECTED")), "rejected reports are audited, not stored");
        processor.process(draft("D101", "Electrical", "Lamp is broken here", "u1"));
        check(f.audit.get(2).equals("SUBMITTED"), "accepted report audited");
    }

    private static void decoratorDuplicate() {
        var f = new Fixture();
        var processor = f.factory.build(List.of("duplicate"));
        Report first = processor.process(draft("D101", "Electrical", "Lamp is broken here", "u1"));
        Report second = processor.process(draft("D101", "Electrical", "Same lamp is dead", "u2"));
        check(first.id().equals(second.duplicateOf()), "second report points to the first");
        check(f.inbox.get("u1").size() == 1, "original reporter alerted");
    }

    private static void decoratorNotification() {
        var f = new Fixture();
        f.factory.build(List.of("notification")).process(draft("D101", "Plumbing", "Tap leaks all day", "u1"));
        check(f.inbox.get("u1").size() == 1, "reporter notified");
    }

    private static void decoratorStack() {
        var f = new Fixture();
        var processor = f.factory.build(List.of("validation", "duplicate", "notification"));
        processor.process(draft("D101", "Electrical", "Lamp is broken here", "u1"));
        processor.process(draft("D101", "Electrical", "Same lamp is dead", "u2"));
        check(f.reports.stored.size() == 2 && f.audit.equals(List.of("SUBMITTED", "SUBMITTED")), "validation + storage");
        check(f.inbox.get("u1").size() == 2 && f.inbox.get("u2").size() == 1, "duplicate alert + receipts");
    }

    private static void decoratorOrderAndSubset() {
        var f = new Fixture();
        f.factory.build(List.of("notification", "validation")).process(draft("D101", "Electrical", "Lamp is broken here", "u1"));
        check(f.inbox.containsKey("u1") && f.audit.contains("SUBMITTED") && f.reports.stored.size() == 1, "any order works");
    }

    private static void decoratorUnknown() {
        try { new Fixture().factory.build(List.of("magic")); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* ok */ }
    }

    private static void decoratorExtensible() {
        var f = new Fixture();
        List<String> trace = new ArrayList<>();
        ReportProcessor custom = new ReportProcessor() { // new decorator: nothing existing is edited
            final ReportProcessor next = f.factory.build(List.of("notification"));
            public Report process(Report r) { trace.add("before"); Report saved = next.process(r); trace.add("after"); return saved; }
        };
        custom.process(draft("D101", "Electrical", "Lamp is broken here", "u1"));
        check(trace.equals(List.of("before", "after")) && f.inbox.containsKey("u1"), "custom decorator composes with existing ones");
    }

    // ---------- helpers ----------
    private static void run(String name, Runnable test) {
        try { test.run(); passed++; System.out.println("ok      " + name); }
        catch (Throwable t) { failures.add(name + " -> " + t); System.out.println("FAILED  " + name + " -> " + t); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void fail(String message) { throw new AssertionError(message); }
    private static void expectError(int status, Runnable action) {
        try { action.run(); } catch (ApiException e) { check(e.status() == status, "status " + e.status() + " != " + status); return; }
        fail("expected ApiException " + status);
    }
}
