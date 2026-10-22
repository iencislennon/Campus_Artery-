package campusos.campusplus.domain;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Slot whose start time is closest to the time of day the student prefers. */
public final class PreferredTimeStrategy implements SchedulingStrategy {
    @Override public String name() { return "preferred"; }

    @Override
    public Optional<OfficeHour> choose(List<OfficeHour> openSlots, SchedulingRequest request) {
        if (request.preferredTime() == null) return Optional.empty();
        return openSlots.stream().min(Comparator
                .comparingLong((OfficeHour slot) -> Math.abs(Duration.between(request.preferredTime(), slot.start()).toMinutes()))
                .thenComparing(OfficeHour::day));
    }
}
