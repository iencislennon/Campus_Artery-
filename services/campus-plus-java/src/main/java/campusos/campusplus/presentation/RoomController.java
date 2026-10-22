package campusos.campusplus.presentation;

import campusos.campusplus.application.RoomFilter;
import campusos.campusplus.application.RoomService;
import campusos.campusplus.domain.Room;
import campusos.common.ApiException;
import campusos.common.Request;
import campusos.common.Response;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RoomController {
    private final RoomService rooms;
    private final Clock clock;

    public RoomController(RoomService rooms, Clock clock) {
        this.rooms = rooms;
        this.clock = clock;
    }

    public Response buildings(Request request) {
        return Response.ok(rooms.buildings().stream().map(b -> Map.of("id", b.id(), "name", b.name(), "color", b.color(), "ink", b.ink())).toList());
    }

    public Response list(Request request) {
        LocalDateTime now = LocalDateTime.now(clock);
        RoomFilter filter = new RoomFilter(request.queryParam("building"), optionalInt(request, "floor"),
                request.queryParam("kind"), optionalInt(request, "minCapacity"), request.queryParam("q"));
        DayOfWeek day = request.queryParam("day").isEmpty() ? now.getDayOfWeek() : DayOfWeek.of(optionalInt(request, "day"));
        LocalTime at = request.queryParam("at").isEmpty() ? now.toLocalTime() : parseTime(request.queryParam("at"));
        List<Map<String, Object>> body = rooms.freeRooms(filter, day, at).stream().map(free -> {
            Map<String, Object> view = roomView(free.room());
            view.put("freeUntil", free.freeUntil().toString());
            return view;
        }).toList();
        return Response.ok(body);
    }

    public Response get(Request request) {
        return Response.ok(roomView(rooms.get(request.param("id"))));
    }

    private static Map<String, Object> roomView(Room room) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", room.id());
        view.put("building", room.building());
        view.put("floor", room.floor());
        view.put("kind", room.kind());
        view.put("capacity", room.capacity());
        return view;
    }

    private static Integer optionalInt(Request request, String key) {
        String raw = request.queryParam(key);
        if (raw.isEmpty()) return null;
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            throw ApiException.invalid("'" + key + "' must be a whole number.");
        }
    }

    static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException e) {
            throw ApiException.invalid("Time must look like 14:30.");
        }
    }
}
