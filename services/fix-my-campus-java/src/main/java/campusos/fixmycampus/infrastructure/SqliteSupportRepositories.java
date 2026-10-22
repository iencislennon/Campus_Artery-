package campusos.fixmycampus.infrastructure;

import campusos.common.Database;
import campusos.fixmycampus.application.SupportPorts.AuditRepository;
import campusos.fixmycampus.application.SupportPorts.CategoryRepository;
import campusos.fixmycampus.application.SupportPorts.NotificationRepository;
import java.util.List;

/** One SQLite-backed class for the three small ports (categories, audit trail, notifications). */
public final class SqliteSupportRepositories implements CategoryRepository, AuditRepository, NotificationRepository {
    private final Database db;

    public SqliteSupportRepositories(Database db) { this.db = db; }

    @Override
    public List<String> names() {
        return db.query("SELECT name FROM issue_categories ORDER BY sort_order", r -> r.getString(1));
    }

    @Override
    public void record(String reportId, String actor, String action, String detail) {
        db.update("INSERT INTO report_audit (report_id, actor, action, detail) VALUES (?,?,?,?)", reportId, actor, action, detail);
    }

    @Override
    public void add(String userId, String message) {
        db.update("INSERT INTO notifications (user_id, message) VALUES (?,?)", userId, message);
    }

    @Override
    public List<String> forUser(String userId) {
        return db.query("SELECT message FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 20", r -> r.getString(1), userId);
    }
}
