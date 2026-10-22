package campusos.campusplus.infrastructure.legacy;

import campusos.campusplus.application.RoomService;
import campusos.campusplus.domain.FreeInterval;
import campusos.campusplus.domain.TimetableProvider;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Adapter: presents the legacy text export as lesson-free room intervals, leaving the legacy class untouched. */
public final class LegacyTimetableAdapter implements TimetableProvider {
    private record Lesson(LocalTime start, LocalTime end) {}

    private final Map<String, List<Lesson>> lessonsByRoomAndDay;

    public LegacyTimetableAdapter(LegacyTimetableSystem legacy) {
        this.lessonsByRoomAndDay = legacy.exportScheduleRows().stream()
                .map(row -> row.split("\\|"))
                .collect(Collectors.groupingBy(
                        cols -> key(cols[0], DayOfWeek.of(Integer.parseInt(cols[1]))),
                        Collectors.mapping(cols -> new Lesson(parseHhmm(cols[2]), parseHhmm(cols[3])), Collectors.toList())));
    }

    @Override
    public List<FreeInterval> freeIntervals(String roomId, DayOfWeek day) {
        List<Lesson> lessons = lessonsByRoomAndDay.getOrDefault(key(roomId, day), List.of()).stream()
                .sorted(Comparator.comparing(Lesson::start)).toList();
        List<FreeInterval> free = new ArrayList<>();
        LocalTime cursor = RoomService.DAY_OPENS;
        for (Lesson lesson : lessons) {
            if (lesson.start().isAfter(cursor)) free.add(new FreeInterval(cursor, lesson.start()));
            if (lesson.end().isAfter(cursor)) cursor = lesson.end();
        }
        if (cursor.isBefore(RoomService.DAY_CLOSES)) free.add(new FreeInterval(cursor, RoomService.DAY_CLOSES));
        return free;
    }

    private static LocalTime parseHhmm(String hhmm) {
        int value = Integer.parseInt(hhmm);
        return LocalTime.of(value / 100, value % 100);
    }

    private static String key(String room, DayOfWeek day) { return room + "#" + day.getValue(); }
}
