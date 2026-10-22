package campusos.campusplus.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Takes the first slot in the teacher's weekly order (Mon to Fri, earliest start). */
public final class FirstAvailableStrategy implements SchedulingStrategy {
    @Override public String name() { return "first"; }

    @Override
    public Optional<OfficeHour> choose(List<OfficeHour> openSlots, SchedulingRequest request) {
        return openSlots.stream().min(Comparator.comparing(OfficeHour::day).thenComparing(OfficeHour::start));
    }
}
