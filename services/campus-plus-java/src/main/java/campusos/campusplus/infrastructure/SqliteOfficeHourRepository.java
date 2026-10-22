package campusos.campusplus.infrastructure;

import campusos.campusplus.application.OfficeHourRepository;
import campusos.campusplus.domain.OfficeHour;
import campusos.common.ApiException;
import campusos.common.Database;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteOfficeHourRepository implements OfficeHourRepository {
    private static final String SELECT = """
            SELECT oh.id, oh.teacher_id, t.name, oh.day, oh.start_time, oh.end_time, oh.capacity,
                   (SELECT COUNT(*) FROM office_hour_bookings b WHERE b.office_hour_id = oh.id),
                   EXISTS (SELECT 1 FROM office_hour_bookings b WHERE b.office_hour_id = oh.id AND b.student_id = ?)
            FROM office_hours oh JOIN teacher_offices t ON t.user_id = oh.teacher_id
            """;
    private static final String ORDER = " ORDER BY oh.day, oh.start_time";

    private final Database db;

    public SqliteOfficeHourRepository(Database db) { this.db = db; }

    @Override
    public List<OfficeHour> listAll(String viewerId) {
        return db.query(SELECT + ORDER, this::map, viewerId);
    }

    @Override
    public List<OfficeHour> listByTeacher(String teacherId, String viewerId) {
        return db.query(SELECT + " WHERE oh.teacher_id = ?" + ORDER, this::map, viewerId, teacherId);
    }

    @Override
    public Optional<OfficeHour> find(String id, String viewerId) {
        return db.query(SELECT + " WHERE oh.id = ?", this::map, viewerId, id).stream().findFirst();
    }

    @Override
    public OfficeHour create(String teacherId, DayOfWeek day, LocalTime start, LocalTime end, int capacity) {
        String id = "oh-" + UUID.randomUUID().toString().substring(0, 8);
        db.update("INSERT INTO office_hours VALUES (?,?,?,?,?,?)", id, teacherId, day.getValue(), start.toString(), end.toString(), capacity);
        return find(id, teacherId).orElseThrow();
    }

    @Override
    public void book(String officeHourId, String studentId) {
        db.transaction(tx -> {
            int capacity = tx.query("SELECT capacity FROM office_hours WHERE id = ?", r -> r.getInt(1), officeHourId).get(0);
            int booked = tx.query("SELECT COUNT(*) FROM office_hour_bookings WHERE office_hour_id = ?", r -> r.getInt(1), officeHourId).get(0);
            boolean mine = !tx.query("SELECT 1 FROM office_hour_bookings WHERE office_hour_id = ? AND student_id = ?", r -> 1, officeHourId, studentId).isEmpty();
            if (mine) throw ApiException.conflict("You already booked this office hour.");
            if (booked >= capacity) throw ApiException.conflict("This office hour is full.");
            tx.update("INSERT INTO office_hour_bookings VALUES (?,?,?,datetime('now'))", UUID.randomUUID().toString(), officeHourId, studentId);
            return null;
        });
    }

    private OfficeHour map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new OfficeHour(r.getString(1), r.getString(2), r.getString(3), DayOfWeek.of(r.getInt(4)),
                LocalTime.parse(r.getString(5)), LocalTime.parse(r.getString(6)), r.getInt(7), r.getInt(8), r.getInt(9) == 1);
    }
}
