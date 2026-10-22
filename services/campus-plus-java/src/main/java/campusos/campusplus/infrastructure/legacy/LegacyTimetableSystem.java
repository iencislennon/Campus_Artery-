package campusos.campusplus.infrastructure.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simulated external SDU timetable component. Its interface is deliberately incompatible with the
 * application's TimetableProvider: it exports pipe-separated text rows with a numeric weekday
 * (1 = Monday) and HHMM integers. It is never modified to fit CampusPlus; the adapter does the conversion.
 */
public final class LegacyTimetableSystem {
    private static final int[][] SLOTS = {{900, 950}, {1000, 1050}, {1100, 1150}, {1200, 1250},
            {1300, 1350}, {1400, 1450}, {1500, 1550}, {1600, 1650}};
    private final List<String> roomCodes;

    public LegacyTimetableSystem(List<String> roomCodes) { this.roomCodes = roomCodes; }

    /** Row format: ROOM|WEEKDAY|HHMM_FROM|HHMM_TO|COURSE */
    public List<String> exportScheduleRows() {
        List<String> rows = new ArrayList<>();
        roomCodes.forEach(room -> addRoomRows(rows, room));
        return rows;
    }

    private void addRoomRows(List<String> rows, String room) {
        int busyEvery = room.length() == 3 && room.charAt(1) == '0' ? 2 : 3;
        for (int weekday = 1; weekday <= 5; weekday++) {
            for (int slot = 0; slot < SLOTS.length; slot++) {
                if (Math.floorMod(Objects.hash(room, weekday, slot), busyEvery) == 0) {
                    rows.add(room + "|" + weekday + "|" + SLOTS[slot][0] + "|" + SLOTS[slot][1] + "|COURSE-" + (100 + slot));
                }
            }
        }
    }
}
