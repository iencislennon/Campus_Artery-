package campusos.fixmycampus.application.processing;

import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportProcessor;

/** Tells the reporter their report was received. */
public final class NotificationDecorator implements ReportProcessor {
    private final ReportProcessor next;
    private final NotificationRepository notifications;

    public NotificationDecorator(ReportProcessor next, NotificationRepository notifications) {
        this.next = next;
        this.notifications = notifications;
    }

    @Override
    public Report process(Report report) {
        Report saved = next.process(report);
        notifications.add(saved.reporterId(), "Report " + saved.id() + " received: " + saved.category() + " in " + saved.roomId() + ".");
        return saved;
    }
}
