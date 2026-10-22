package campusos.campusplus.application;

import campusos.campusplus.domain.Building;
import campusos.campusplus.domain.Room;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {
    List<Building> buildings();

    List<Room> search(RoomFilter filter);

    Optional<Room> find(String roomId);
}
