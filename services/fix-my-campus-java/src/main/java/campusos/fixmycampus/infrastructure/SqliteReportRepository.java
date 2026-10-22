package campusos.fixmycampus.infrastructure;

import campusos.common.Database;
import campusos.fixmycampus.application.ReportRepository;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SqliteReportRepository implements ReportRepository {
    private static final String COLUMNS = "SELECT id, room_id, category, description, status, reporter_id, created_at, duplicate_of, assignee_id FROM reports";
    private final Database db;

    public SqliteReportRepository(Database db) { this.db = db; }

    @Override
    public Report insert(Report r) {
        db.update("INSERT INTO reports VALUES (?,?,?,?,?,?,?,?,?)", r.id(), r.roomId(), r.category(), r.description(),
                r.status().name(), r.reporterId(), r.createdAt(), r.duplicateOf(), r.assigneeId());
        return r;
    }

    @Override
    public List<Report> listByReporter(String reporterId) {
        return db.query(COLUMNS + " WHERE reporter_id = ? ORDER BY created_at DESC, rowid DESC", this::map, reporterId);
    }

    @Override
    public List<Report> listAll() {
        return db.query(COLUMNS + " ORDER BY created_at DESC, rowid DESC", this::map);
    }

    @Override
    public Optional<Report> find(String id) {
        return db.query(COLUMNS + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<Report> findOpenDuplicate(String roomId, String category) {
        return db.query(COLUMNS + " WHERE room_id = ? AND category = ? AND status <> 'RESOLVED' AND duplicate_of IS NULL ORDER BY created_at LIMIT 1",
                this::map, roomId, category).stream().findFirst();
    }

    @Override
    public void updateStatus(String id, ReportStatus status, String assigneeId) {
        db.update("UPDATE reports SET status = ?, assignee_id = ? WHERE id = ?", status.name(), assigneeId, id);
    }

    private Report map(ResultSet r) throws SQLException {
        return new Report(r.getString(1), r.getString(2), r.getString(3), r.getString(4), ReportStatus.valueOf(r.getString(5)),
                r.getString(6), r.getString(7), r.getString(8), r.getString(9));
    }
}
