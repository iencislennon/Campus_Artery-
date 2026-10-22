package campusos.campusplus.application;

import campusos.campusplus.domain.OfficeHour;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface OfficeHourRepository {
    List<OfficeHour> listAll(String viewerId);

    List<OfficeHour> listByTeacher(String teacherId, String viewerId);

    Optional<OfficeHour> find(String id, String viewerId);

    OfficeHour create(String teacherId, DayOfWeek day, LocalTime start, LocalTime end, int capacity);

    /** Atomically books a seat; throws a conflict if the slot is full or already booked by this student. */
    void book(String officeHourId, String studentId);
}
