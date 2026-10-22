package campusos.campusplus.infrastructure;

import campusos.common.Database;

/** DDL and demo seed for campus.db. See ../../../../../../../docs/db-uml.md for the diagram. */
public final class CampusSchema {
    private CampusSchema() {}

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS buildings (
              id TEXT PRIMARY KEY, name TEXT NOT NULL, color TEXT NOT NULL, ink TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS rooms (
              id TEXT PRIMARY KEY, building_id TEXT NOT NULL REFERENCES buildings(id),
              floor INTEGER NOT NULL, kind TEXT NOT NULL CHECK (kind IN ('Classroom','Barrel')),
              capacity INTEGER NOT NULL CHECK (capacity > 0));
            CREATE TABLE IF NOT EXISTS teacher_offices (
              user_id TEXT PRIMARY KEY, name TEXT NOT NULL, department TEXT NOT NULL,
              office_room_id TEXT NOT NULL REFERENCES rooms(id));
            CREATE TABLE IF NOT EXISTS office_hours (
              id TEXT PRIMARY KEY, teacher_id TEXT NOT NULL REFERENCES teacher_offices(user_id),
              day INTEGER NOT NULL CHECK (day BETWEEN 1 AND 5),
              start_time TEXT NOT NULL, end_time TEXT NOT NULL,
              capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 20));
            CREATE TABLE IF NOT EXISTS office_hour_bookings (
              id TEXT PRIMARY KEY, office_hour_id TEXT NOT NULL REFERENCES office_hours(id),
              student_id TEXT NOT NULL, created_at TEXT NOT NULL,
              UNIQUE (office_hour_id, student_id))
            """;

    public static void init(Database db) {
        db.executeScript(DDL);
        boolean empty = db.query("SELECT COUNT(*) FROM buildings", row -> row.getInt(1)).get(0) == 0;
        if (empty) seed(db);
    }

    private static void seed(Database db) {
        String[][] blocks = {{"D", "#7B3FA0", "#fff"}, {"E", "#8B5A2B", "#fff"}, {"F", "#1E3A8A", "#fff"},
                {"G", "#B5D99C", "#1A1A1A"}, {"H", "#8FD0F2", "#1A1A1A"}, {"I", "#14532D", "#fff"}};
        String[] barrels = {"H01", "H02", "H03", "D117", "D218", "D116", "D217", "D113", "D214", "E117", "E221"};
        db.transaction(tx -> {
            for (int b = 0; b < blocks.length; b++) {
                tx.update("INSERT INTO buildings VALUES (?,?,?,?)", blocks[b][0], "Block " + blocks[b][0], blocks[b][1], blocks[b][2]);
                insertClassrooms(tx, blocks[b][0], b);
            }
            for (int i = 0; i < barrels.length; i++) {
                tx.update("INSERT INTO rooms VALUES (?,?,?,?,?)", barrels[i], barrels[i].substring(0, 1),
                        barrels[i].length() == 3 ? 0 : barrels[i].charAt(1) - '0', "Barrel", 80 + (i % 3) * 20);
            }
            seedPeople(tx);
            return null;
        });
    }

    private static void insertClassrooms(Database.Tx tx, String block, int blockIndex) throws java.sql.SQLException {
        for (int floor = 1; floor <= 3; floor++) {
            for (int n = 1; n <= 5; n++) {
                tx.update("INSERT INTO rooms VALUES (?,?,?,?,?)", block + floor + "0" + n, block, floor, "Classroom",
                        24 + ((blockIndex + floor + n) % 4) * 6);
            }
        }
    }

    private static void seedPeople(Database.Tx tx) throws java.sql.SQLException {
        tx.update("INSERT INTO teacher_offices VALUES ('u-teacher','Dr. Aigerim Nurlanova','Computer Science','E301')");
        tx.update("INSERT INTO teacher_offices VALUES ('t-sadykov','Prof. Marat Sadykov','Mathematics','D302')");
        tx.update("INSERT INTO teacher_offices VALUES ('t-kassymova','Dr. Dina Kassymova','Software Engineering','F305')");
        String[][] slots = {{"oh1", "u-teacher", "1", "14:00", "15:00", "4"}, {"oh2", "u-teacher", "3", "10:00", "11:30", "3"},
                {"oh3", "t-sadykov", "2", "16:00", "17:00", "5"}, {"oh4", "t-kassymova", "4", "12:00", "13:00", "4"}};
        for (String[] s : slots) tx.update("INSERT INTO office_hours VALUES (?,?,?,?,?,?)", s[0], s[1], Integer.parseInt(s[2]), s[3], s[4], Integer.parseInt(s[5]));
        for (int i = 1; i <= 3; i++) tx.update("INSERT INTO office_hour_bookings VALUES (?,?,?,datetime('now'))", "seed-b" + i, "oh2", "seed-student-" + i);
        tx.update("INSERT INTO office_hour_bookings VALUES ('seed-b4','oh1','seed-student-1',datetime('now'))");
    }
}
