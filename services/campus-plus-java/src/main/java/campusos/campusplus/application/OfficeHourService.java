package campusos.campusplus.application;

import campusos.campusplus.domain.OfficeHour;
import campusos.campusplus.domain.SchedulingRequest;
import campusos.campusplus.domain.SchedulingStrategy;
import campusos.common.ApiException;
import campusos.common.AuthUser;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public final class OfficeHourService {
    private final OfficeHourRepository slots;
    private final TeacherRepository teachers;
    private final StrategyRegistry strategies;

    public OfficeHourService(OfficeHourRepository slots, TeacherRepository teachers, StrategyRegistry strategies) {
        this.slots = slots;
        this.teachers = teachers;
        this.strategies = strategies;
    }

    public List<OfficeHour> list(AuthUser viewer) { return slots.listAll(viewer.id()); }

    public OfficeHour create(AuthUser teacher, DayOfWeek day, LocalTime start, LocalTime end, int capacity) {
        if (!teacher.isTeacher()) throw ApiException.forbidden("Only teachers can create office hours.");
        if (teachers.find(teacher.id()).isEmpty()) throw ApiException.invalid("Register your office before adding office hours.");
        if (!start.isBefore(end)) throw ApiException.invalid("End time must be after start time.");
        if (capacity < 1 || capacity > 20) throw ApiException.invalid("Capacity must be between 1 and 20.");
        boolean clash = slots.listByTeacher(teacher.id(), teacher.id()).stream().anyMatch(s -> s.overlaps(day, start, end));
        if (clash) throw ApiException.conflict("This slot overlaps one of your existing office hours.");
        return slots.create(teacher.id(), day, start, end, capacity);
    }

    public OfficeHour book(AuthUser student, String officeHourId) {
        OfficeHour slot = slots.find(officeHourId, student.id()).orElseThrow(() -> ApiException.notFound("Office hour not found."));
        if (slot.teacherId().equals(student.id())) throw ApiException.forbidden("You cannot book your own office hour.");
        slots.book(slot.id(), student.id());
        return slots.find(officeHourId, student.id()).orElseThrow();
    }

    /** Picks a slot of {@code teacherId} with the chosen strategy, then books it. */
    public OfficeHour autoBook(AuthUser student, String teacherId, String strategyName, SchedulingRequest request) {
        SchedulingStrategy strategy = strategies.get(strategyName);
        List<OfficeHour> open = slots.listByTeacher(teacherId, student.id()).stream()
                .filter(OfficeHour::hasFreeSeat)
                .filter(slot -> !slot.bookedByMe())
                .toList();
        OfficeHour chosen = strategy.choose(open, request)
                .orElseThrow(() -> ApiException.conflict("No office hour matches the '" + strategyName + "' policy."));
        return book(student, chosen.id());
    }

    public java.util.Set<String> strategyNames() { return strategies.names(); }

}
