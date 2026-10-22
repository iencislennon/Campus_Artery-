package campusos.fixmycampus.domain;

/** Decorator component: every step of report handling, wrapped or wrapping, speaks this interface. */
public interface ReportProcessor {
    Report process(Report report);
}
