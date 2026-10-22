package campusos.fixmycampus.application.processing;

import campusos.fixmycampus.application.ReportRepository;
import campusos.fixmycampus.application.SupportPorts.AuditRepository;
import campusos.fixmycampus.application.SupportPorts.CategoryRepository;
import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import campusos.fixmycampus.domain.ReportProcessor;
import java.util.List;

/** Stacks decorators in any combination. {@code names} lists them outermost first, e.g. validation,duplicate,notification. */
public final class ReportPipelineFactory {
    private final ReportRepository reports;
    private final CategoryRepository categories;
    private final AuditRepository audit;
    private final NotificationRepository notifications;

    public ReportPipelineFactory(ReportRepository reports, CategoryRepository categories, AuditRepository audit, NotificationRepository notifications) {
        this.reports = reports;
        this.categories = categories;
        this.audit = audit;
        this.notifications = notifications;
    }

    public ReportProcessor build(List<String> names) {
        ReportProcessor processor = new BaseReportProcessor(reports);
        for (String name : names.reversed()) processor = wrap(name.trim(), processor);
        return processor;
    }

    private ReportProcessor wrap(String name, ReportProcessor inner) {
        return switch (name) {
            case "validation" -> new ValidationAuditDecorator(inner, categories, audit);
            case "duplicate" -> new DuplicateAlertDecorator(inner, reports, notifications);
            case "notification" -> new NotificationDecorator(inner, notifications);
            default -> throw new IllegalArgumentException("Unknown report decorator '" + name + "'");
        };
    }
}
