package campusos.campusplus.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/** Student's constraints for automatic slot selection; deadline and preferredTime may be null. */
public record SchedulingRequest(LocalDate today, LocalDate deadline, LocalTime preferredTime) {}
