package campusos.fixmycampus.application.processing;

import campusos.fixmycampus.application.ReportRepository;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportProcessor;

/** The undecorated core: persist the report. */
public final class BaseReportProcessor implements ReportProcessor {
    private final ReportRepository reports;

    public BaseReportProcessor(ReportRepository reports) { this.reports = reports; }

    @Override
    public Report process(Report report) { return reports.insert(report); }
}
