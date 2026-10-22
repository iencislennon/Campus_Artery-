package campusos.campusplus.application;

import campusos.campusplus.domain.FreeInterval;
import campusos.campusplus.domain.Room;
import campusos.campusplus.domain.TimetableProvider;
import campusos.common.ApiException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public final class RoomService {
    public static final LocalTime DAY_OPENS = LocalTime.of(8, 30);
    public static final LocalTime DAY_CLOSES = LocalTime.of(18, 0);

    public record FreeRoom(Room room, LocalTime freeUntil) {}

    private final RoomRepository rooms;
    private final TimetableProvider timetable;

    public RoomService(RoomRepository rooms, TimetableProvider timetable) {
        this.rooms = rooms;
        this.timetable = timetable;
    }

    public List<campusos.campusplus.domain.Building> buildings() { return rooms.buildings(); }

    public Room get(String roomId) {
        return rooms.find(roomId).orElseThrow(() -> ApiException.notFound("Room " + roomId + " does not exist."));
    }

    /** Rooms with no lesson at {@code at}. Outside teaching hours the check uses opening time so the list is never empty. */
    public List<FreeRoom> freeRooms(RoomFilter filter, DayOfWeek day, LocalTime at) {
        LocalTime moment = at.isBefore(DAY_OPENS) || !at.isBefore(DAY_CLOSES) ? DAY_OPENS : at;
        return rooms.search(filter).stream()
                .map(room -> freeUntil(room, day, moment))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<FreeRoom> freeUntil(Room room, DayOfWeek day, LocalTime moment) {
        return timetable.freeIntervals(room.id(), day).stream()
                .filter(interval -> interval.covers(moment))
                .findFirst()
                .map(FreeInterval::end)
                .map(end -> new FreeRoom(room, end));
    }
}
