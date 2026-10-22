package campusos.fixmycampus.application;

import java.util.List;

/** Small ports used by the processing decorators and the service. */
public final class SupportPorts {
    private SupportPorts() {}

    public interface CategoryRepository { List<String> names(); }

    public interface AuditRepository { void record(String reportId, String actor, String action, String detail); }

    public interface NotificationRepository {
        void add(String userId, String message);

        List<String> forUser(String userId);
    }

    /** Asks the owning service (CampusPlus) whether a room exists; never touches its database. */
    public interface RoomDirectory { boolean exists(String roomId, String bearer); }
}
