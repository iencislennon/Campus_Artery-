package campusos.campusplus.domain;

import java.util.List;
import java.util.Optional;

/** Strategy: one policy for picking an office-hour slot among those with free seats. */
public interface SchedulingStrategy {
    String name();

    Optional<OfficeHour> choose(List<OfficeHour> openSlots, SchedulingRequest request);
}
