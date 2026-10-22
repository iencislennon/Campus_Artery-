package campusos.fixmycampus.application;

import campusos.common.ApiException;
import campusos.common.AuthUser;
import campusos.fixmycampus.application.SupportPorts.AuditRepository;
import campusos.fixmycampus.application.SupportPorts.CategoryRepository;
import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import campusos.fixmycampus.infrastructure.AiValidationClient;
import java.util.Map;
import campusos.fixmycampus.application.SupportPorts.RoomDirectory;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportProcessor;
import campusos.fixmycampus.domain.ReportStatus;
import java.util.List;

public final class ReportService {
    private final ReportProcessor pipeline;
    private final ReportRepository reports;
    private final CategoryRepository categories;
    private final RoomDirectory rooms;
    private final AuditRepository audit;
    private final NotificationRepository notifications;
    private final AiValidationClient ai;

    public ReportService(ReportProcessor pipeline, ReportRepository reports, CategoryRepository categories,
                         RoomDirectory rooms, AuditRepository audit, NotificationRepository notifications,
                         AiValidationClient ai) {
        this.pipeline = pipeline;
        this.reports = reports;
        this.categories = categories;
        this.rooms = rooms;
        this.audit = audit;
        this.notifications = notifications;
        this.ai = ai;
    }

    public List<String> categories() { return categories.names(); }

    public List<Report> list(AuthUser user, boolean everyone) {
        return everyone && user.isTeacher() ? reports.listAll() : reports.listByReporter(user.id());
    }

    public List<String> notifications(AuthUser user) { return notifications.forUser(user.id()); }

    /** Service-boundary validation (the room must exist in CampusPlus) stays mandatory whatever decorators are configured. */
    public Report submit(AuthUser user, String bearer, String roomId, String category, String description,
                         Map<String, Object> attachment) {
        if (roomId == null || roomId.isBlank()) throw ApiException.invalid("Choose a room.");
        if (!rooms.exists(roomId, bearer)) throw ApiException.invalid("Room " + roomId + " does not exist.");
        String safeCategory = category == null ? "" : category;
        String safeDescription = description == null ? "" : description;
        ai.validateIssue(roomId, safeCategory, safeDescription, attachment);
        return pipeline.process(Report.draft(roomId, safeCategory, safeDescription, user.id()));
    }

    public Report updateStatus(AuthUser user, String reportId, ReportStatus status) {
        if (!user.isTeacher()) throw ApiException.forbidden("Only teachers can review reports.");
        Report report = reports.find(reportId).orElseThrow(() -> ApiException.notFound("Report not found."));
        reports.updateStatus(reportId, status, status == ReportStatus.OPEN ? null : user.id());
        audit.record(reportId, user.id(), "STATUS_" + status, "was " + report.status());
        notifications.add(report.reporterId(), "Your report " + reportId + " is now " + status.name().replace('_', ' ').toLowerCase() + ".");
        return reports.find(reportId).orElseThrow();
    }
}
