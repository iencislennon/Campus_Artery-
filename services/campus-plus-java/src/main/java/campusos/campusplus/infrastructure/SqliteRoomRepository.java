package campusos.campusplus.infrastructure;

import campusos.campusplus.application.RoomFilter;
import campusos.campusplus.application.RoomRepository;
import campusos.campusplus.domain.Building;
import campusos.campusplus.domain.Room;
import campusos.common.Database;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteRoomRepository implements RoomRepository {
    private final Database db;

    public SqliteRoomRepository(Database db) { this.db = db; }

    @Override
    public List<Building> buildings() {
        return db.query("SELECT id, name, color, ink FROM buildings ORDER BY id",
                r -> new Building(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
    }

    @Override
    public List<Room> search(RoomFilter f) {
        StringBuilder sql = new StringBuilder("SELECT id, building_id, floor, kind, capacity FROM rooms WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (!f.building().isEmpty()) { sql.append(" AND building_id = ?"); params.add(f.building()); }
        if (f.floor() != null) { sql.append(" AND floor = ?"); params.add(f.floor()); }
        if (!f.kind().isEmpty()) { sql.append(" AND kind = ?"); params.add(f.kind()); }
        if (f.minCapacity() != null) { sql.append(" AND capacity >= ?"); params.add(f.minCapacity()); }
        if (!f.codeContains().isEmpty()) { sql.append(" AND id LIKE ?"); params.add("%" + f.codeContains().toUpperCase() + "%"); }
        sql.append(" ORDER BY kind, id");
        return db.query(sql.toString(), this::map, params.toArray());
    }

    @Override
    public Optional<Room> find(String roomId) {
        return db.query("SELECT id, building_id, floor, kind, capacity FROM rooms WHERE id = ?", this::map, roomId).stream().findFirst();
    }

    private Room map(java.sql.ResultSet r) throws java.sql.SQLException {
        return new Room(r.getString(1), r.getString(2), r.getInt(3), r.getString(4), r.getInt(5));
    }
}
