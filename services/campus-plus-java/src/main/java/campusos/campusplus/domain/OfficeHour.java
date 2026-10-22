package campusos.campusplus.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.Locale;

public record OfficeHour(String id, String teacherId, String teacherName, DayOfWeek day,
                         LocalTime start, LocalTime end, int capacity, int booked, boolean bookedByMe) {

    public boolean hasFreeSeat() { return booked < capacity; }

    public String dayLabel() { return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH); }

    public boolean overlaps(DayOfWeek otherDay, LocalTime otherStart, LocalTime otherEnd) {
        return day == otherDay && start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    /** First calendar date on or after {@code from} on which this weekly slot takes place. */
    public LocalDate nextOccurrence(LocalDate from) {
        int ahead = (day.getValue() - from.getDayOfWeek().getValue() + 7) % 7;
        return from.plusDays(ahead);
    }
}
