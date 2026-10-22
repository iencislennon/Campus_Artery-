package campusos.fixmycampus.application.processing;

import campusos.fixmycampus.application.ReportRepository;
import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportProcessor;

/** Links a new report to an open one for the same room and category, and alerts the original reporter. */
public final class DuplicateAlertDecorator implements ReportProcessor {
    private final ReportProcessor next;
    private final ReportRepository reports;
    private final NotificationRepository notifications;

    public DuplicateAlertDecorator(ReportProcessor next, ReportRepository reports, NotificationRepository notifications) {
        this.next = next;
        this.reports = reports;
        this.notifications = notifications;
    }

    @Override
    public Report process(Report report) {
        var duplicate = reports.findOpenDuplicate(report.roomId(), report.category());
        Report saved = next.process(duplicate.map(original -> report.withDuplicateOf(original.id())).orElse(report));
        duplicate.filter(original -> !original.reporterId().equals(report.reporterId()))
                .ifPresent(original -> notifications.add(original.reporterId(),
                        "Someone else reported the same " + original.category() + " problem in " + original.roomId() + "."));
        return saved;
    }
}
