package campusos.campusplus.infrastructure;

import campusos.campusplus.application.TeacherRepository;
import campusos.common.Database;
import java.util.List;
import java.util.Optional;

public final class SqliteTeacherRepository implements TeacherRepository {
    private final Database db;

    public SqliteTeacherRepository(Database db) { this.db = db; }

    @Override
    public List<TeacherOffice> search(String nameContains) {
        return db.query("SELECT user_id, name, department, office_room_id FROM teacher_offices WHERE name LIKE ? ORDER BY name",
                r -> new TeacherOffice(r.getString(1), r.getString(2), r.getString(3), r.getString(4)), "%" + nameContains + "%");
    }

    @Override
    public Optional<TeacherOffice> find(String userId) {
        return db.query("SELECT user_id, name, department, office_room_id FROM teacher_offices WHERE user_id = ?",
                r -> new TeacherOffice(r.getString(1), r.getString(2), r.getString(3), r.getString(4)), userId).stream().findFirst();
    }

    @Override
    public void save(TeacherOffice o) {
        db.update("INSERT INTO teacher_offices (user_id, name, department, office_room_id) VALUES (?,?,?,?) "
                + "ON CONFLICT(user_id) DO UPDATE SET name = excluded.name, department = excluded.department, office_room_id = excluded.office_room_id",
                o.userId(), o.name(), o.department(), o.office());
    }
}
