package campusos.campusplus.domain;

import java.time.DayOfWeek;
import java.util.List;

/** Port the application depends on; how the university timetable is fetched is an infrastructure detail. */
public interface TimetableProvider {
    List<FreeInterval> freeIntervals(String roomId, DayOfWeek day);
}
