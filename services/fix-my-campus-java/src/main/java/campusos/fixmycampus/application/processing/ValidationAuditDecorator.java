package campusos.fixmycampus.application.processing;

import campusos.common.ApiException;
import campusos.fixmycampus.application.SupportPorts.AuditRepository;
import campusos.fixmycampus.application.SupportPorts.CategoryRepository;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportProcessor;

/** Checks report content and writes an audit trail entry for both rejection and acceptance. */
public final class ValidationAuditDecorator implements ReportProcessor {
    static final int MIN_DESCRIPTION = 10;
    static final int MAX_DESCRIPTION = 1000;

    private final ReportProcessor next;
    private final CategoryRepository categories;
    private final AuditRepository audit;

    public ValidationAuditDecorator(ReportProcessor next, CategoryRepository categories, AuditRepository audit) {
        this.next = next;
        this.categories = categories;
        this.audit = audit;
    }

    @Override
    public Report process(Report report) {
        String problem = findProblem(report);
        if (problem != null) {
            audit.record(null, report.reporterId(), "REJECTED", problem);
            throw ApiException.invalid(problem);
        }
        Report saved = next.process(report);
        audit.record(saved.id(), saved.reporterId(), "SUBMITTED", saved.category() + " in " + saved.roomId());
        return saved;
    }

    private String findProblem(Report report) {
        if (!categories.names().contains(report.category())) return "Unknown category '" + report.category() + "'.";
        int length = report.description().trim().length();
        if (length < MIN_DESCRIPTION) return "Describe the problem in at least " + MIN_DESCRIPTION + " characters.";
        if (length > MAX_DESCRIPTION) return "Description is limited to " + MAX_DESCRIPTION + " characters.";
        return null;
    }
}
