package campusos.fixmycampus.domain;

import java.util.UUID;

public record Report(String id, String roomId, String category, String description, ReportStatus status,
                     String reporterId, String createdAt, String duplicateOf, String assigneeId) {

    public static Report draft(String roomId, String category, String description, String reporterId) {
        return new Report("rep-" + UUID.randomUUID().toString().substring(0, 8), roomId, category, description,
                ReportStatus.OPEN, reporterId, java.time.LocalDate.now().toString(), null, null);
    }

    public Report withDuplicateOf(String originalId) {
        return new Report(id, roomId, category, description, status, reporterId, createdAt, originalId, assigneeId);
    }
}
