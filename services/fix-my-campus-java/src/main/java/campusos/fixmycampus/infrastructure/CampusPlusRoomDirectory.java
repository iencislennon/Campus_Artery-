package campusos.fixmycampus.infrastructure;

import campusos.common.ApiException;
import campusos.common.ServiceClient;
import campusos.fixmycampus.application.SupportPorts.RoomDirectory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Validates room ids through CampusPlus's public API (service-to-service HTTP, with timeout). */
public final class CampusPlusRoomDirectory implements RoomDirectory {
    private final ServiceClient campusPlus;

    public CampusPlusRoomDirectory(ServiceClient campusPlus) { this.campusPlus = campusPlus; }

    @Override
    public boolean exists(String roomId, String bearer) {
        int status = campusPlus.send("GET", "/rooms/" + URLEncoder.encode(roomId, StandardCharsets.UTF_8), bearer, null).statusCode();
        if (status == 200) return true;
        if (status == 404) return false;
        throw ApiException.unavailable("Room check failed (CampusPlus answered " + status + ").");
    }
}
