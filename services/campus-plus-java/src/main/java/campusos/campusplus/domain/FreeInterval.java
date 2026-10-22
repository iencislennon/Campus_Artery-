package campusos.campusplus.domain;

import java.time.LocalTime;

/** A stretch of the day in which a room has no lesson. */
public record FreeInterval(LocalTime start, LocalTime end) {
    public boolean covers(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
