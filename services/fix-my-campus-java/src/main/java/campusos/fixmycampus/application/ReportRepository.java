package campusos.fixmycampus.application;

import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportStatus;
import java.util.List;
import java.util.Optional;

public interface ReportRepository {
    Report insert(Report report);

    List<Report> listByReporter(String reporterId);

    List<Report> listAll();

    Optional<Report> find(String id);

    Optional<Report> findOpenDuplicate(String roomId, String category);

    void updateStatus(String id, ReportStatus status, String assigneeId);
}
