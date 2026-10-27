import campusos.campusplus.application.StrategyRegistry;
import campusos.campusplus.domain.*;
import campusos.campusplus.infrastructure.legacy.LegacyTimetableAdapter;
import campusos.campusplus.infrastructure.legacy.LegacyTimetableSystem;
import campusos.fixmycampus.application.ReportRepository;
import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import campusos.fixmycampus.application.processing.ReportPipelineFactory;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportStatus;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Plain-print demo of the three patterns. Run: tests/run-pattern-demo.sh */
public final class PatternDemo {
    private static OfficeHour slot(String id, DayOfWeek day, String start) {
        LocalTime s = LocalTime.parse(start);
        return new OfficeHour(id, "t1", "Teacher", day, s, s.plusHours(1), 5, 0, false);
    }

    public static void main(String[] args) {
        strategy();
        adapter();
        decorator();
    }

    private static void strategy() {
        System.out.println("=== STRATEGY (SchedulingStrategy) ===");
        LocalDate monday = LocalDate.of(2026, 10, 19);
        List<OfficeHour> slots = List.of(slot("mon-10", DayOfWeek.MONDAY, "10:00"),
                slot("tue-15", DayOfWeek.TUESDAY, "15:00"), slot("fri-09", DayOfWeek.FRIDAY, "09:00"));
        System.out.println("open slots: " + slots.stream().map(OfficeHour::id).toList());
        var registry = new StrategyRegistry().register(new FirstAvailableStrategy())
                .register(new EarliestDeadlineStrategy()).register(new PreferredTimeStrategy());
        System.out.println("registered strategies: " + registry.names());
        var request = new SchedulingRequest(monday, monday.plusDays(2), LocalTime.of(15, 10));
        for (String name : registry.names()) {
            var chosen = registry.get(name).choose(slots, request);
            System.out.println("  strategy '" + name + "' -> " + chosen.map(OfficeHour::id).orElse("(nothing)"));
        }
        System.out.println();
    }

    private static void adapter() {
        System.out.println("=== ADAPTER (TimetableProvider <- LegacyTimetableAdapter) ===");
        var legacy = new LegacyTimetableSystem(List.of("D101"));
        System.out.println("legacy rows (incompatible format ROOM|WEEKDAY|HHMM_FROM|HHMM_TO|COURSE):");
        legacy.exportScheduleRows().stream().filter(r -> r.startsWith("D101|1|")).forEach(r -> System.out.println("  " + r));
        TimetableProvider provider = new LegacyTimetableAdapter(legacy);
        System.out.println("adapter output via TimetableProvider, D101 Monday free intervals:");
        provider.freeIntervals("D101", DayOfWeek.MONDAY).forEach(i -> System.out.println("  " + i));
        System.out.println();
    }

    private static void decorator() {
        System.out.println("=== DECORATOR (ReportProcessor chain) ===");
        List<Report> stored = new ArrayList<>();
        ReportRepository repo = new ReportRepository() {
            public Report insert(Report r) { System.out.println("  [Base] saved report in " + r.roomId()); stored.add(r); return r; }
            public List<Report> listByReporter(String id) { return stored; }
            public List<Report> listAll() { return stored; }
            public Optional<Report> find(String id) { return Optional.empty(); }
            public Optional<Report> findOpenDuplicate(String room, String cat) {
                return stored.stream().filter(r -> r.roomId().equals(room) && r.category().equals(cat) && r.status() == ReportStatus.OPEN).findFirst();
            }
            public void updateStatus(String id, ReportStatus s, String a) {}
        };
        var factory = new ReportPipelineFactory(repo, () -> List.of("Electrical"),
                (reportId, actor, action, detail) -> System.out.println("  [Validation/Audit] audit entry: " + action),
                new NotificationRepository() {
                    public void add(String user, String msg) { System.out.println("  [Notification] to " + user + ": " + msg); }
                    public List<String> forUser(String user) { return List.of(); }
                });
        var chain = factory.build(List.of("validation", "duplicate", "notification"));
        System.out.println("chain: validation -> duplicate -> notification -> base");
        System.out.println("report 1 (valid):");
        chain.process(Report.draft("D101", "Electrical", "Lamp is broken here", "u1"));
        System.out.println("report 2 (same room+category, other user -> duplicate):");
        chain.process(Report.draft("D101", "Electrical", "Lamp still not working", "u2"));
        System.out.println("report 3 (description too short -> rejected by validation):");
        try { chain.process(Report.draft("D101", "Electrical", "bad", "u3")); }
        catch (RuntimeException e) { System.out.println("  rejected: " + e.getMessage()); }
        System.out.println("chain with only 'notification':");
        factory.build(List.of("notification")).process(Report.draft("E202", "Electrical", "Socket is sparking", "u4"));
    }
}
