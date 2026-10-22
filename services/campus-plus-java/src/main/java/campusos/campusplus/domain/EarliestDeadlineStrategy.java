package campusos.campusplus.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Soonest upcoming slot that still happens on or before the student's deadline. */
public final class EarliestDeadlineStrategy implements SchedulingStrategy {
    @Override public String name() { return "deadline"; }

    @Override
    public Optional<OfficeHour> choose(List<OfficeHour> openSlots, SchedulingRequest request) {
        return openSlots.stream()
                .filter(slot -> request.deadline() == null || !slot.nextOccurrence(request.today()).isAfter(request.deadline()))
                .min(Comparator.comparing((OfficeHour slot) -> slot.nextOccurrence(request.today())).thenComparing(OfficeHour::start));
    }
}
