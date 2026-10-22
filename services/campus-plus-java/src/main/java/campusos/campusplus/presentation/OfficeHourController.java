package campusos.campusplus.presentation;

import campusos.campusplus.application.OfficeHourService;
import campusos.campusplus.application.TeacherRepository;
import campusos.campusplus.application.TeacherRepository.TeacherOffice;
import campusos.campusplus.application.RoomRepository;
import campusos.campusplus.domain.OfficeHour;
import campusos.campusplus.domain.SchedulingRequest;
import campusos.common.ApiException;
import campusos.common.Request;
import campusos.common.Response;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class OfficeHourController {
    private final OfficeHourService service;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final Clock clock;

    public OfficeHourController(OfficeHourService service, TeacherRepository teachers, RoomRepository rooms, Clock clock) {
        this.service = service;
        this.teachers = teachers;
        this.rooms = rooms;
        this.clock = clock;
    }

    public Response teachers(Request request) {
        return Response.ok(teachers.search(request.queryParam("q")).stream()
                .map(t -> Map.of("id", t.userId(), "name", t.name(), "department", t.department(), "office", t.office())).toList());
    }

    public Response registerOffice(Request request) {
        String office = request.requiredBody("office").toUpperCase();
        if (rooms.find(office).isEmpty()) throw ApiException.invalid("Room " + office + " does not exist.");
        TeacherOffice saved = new TeacherOffice(request.user().id(), request.user().name(), request.requiredBody("department"), office);
        teachers.save(saved);
        return Response.ok(Map.of("id", saved.userId(), "name", saved.name(), "department", saved.department(), "office", saved.office()));
    }

    public Response list(Request request) {
        return Response.ok(service.list(request.user()).stream().map(OfficeHourController::view).toList());
    }

    public Response create(Request request) {
        int capacity = parseCapacity(request.requiredBody("capacity"));
        OfficeHour created = service.create(request.user(), parseDay(request.requiredBody("day")),
                parseTime(request.requiredBody("start")), parseTime(request.requiredBody("end")), capacity);
        return Response.created(view(created));
    }

    /** Body is either {"id": ...} for a manual booking or {"teacherId", "strategy", "deadline"?, "preferredTime"?}. */
    public Response book(Request request) {
        String id = request.bodyString("id");
        if (id != null && !id.isEmpty()) return Response.ok(view(service.book(request.user(), id)));
        SchedulingRequest scheduling = new SchedulingRequest(LocalDate.now(clock), optionalDate(request.bodyString("deadline")),
                optionalTime(request.bodyString("preferredTime")));
        return Response.ok(view(service.autoBook(request.user(), request.requiredBody("teacherId"), request.requiredBody("strategy"), scheduling)));
    }

    public Response strategies(Request request) {
        return Response.ok(service.strategyNames());
    }

    private static Map<String, Object> view(OfficeHour h) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", h.id());
        view.put("teacherId", h.teacherId());
        view.put("teacher", h.teacherName());
        view.put("day", h.dayLabel());
        view.put("start", h.start().toString());
        view.put("end", h.end().toString());
        view.put("capacity", h.capacity());
        view.put("booked", h.booked());
        view.put("bookedByMe", h.bookedByMe());
        return view;
    }

    private static DayOfWeek parseDay(String label) {
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(label) && day.getValue() <= 5) return day;
        }
        throw ApiException.invalid("Day must be Mon, Tue, Wed, Thu or Fri.");
    }

    private static LocalTime parseTime(String raw) { return RoomController.parseTime(raw); }

    private static LocalTime optionalTime(String raw) { return raw == null || raw.isEmpty() ? null : parseTime(raw); }

    private static LocalDate optionalDate(String raw) {
        try {
            return raw == null || raw.isEmpty() ? null : LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("Deadline must look like 2026-11-05.");
        }
    }

    private static int parseCapacity(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw ApiException.invalid("Capacity must be a whole number.");
        }
    }
}
