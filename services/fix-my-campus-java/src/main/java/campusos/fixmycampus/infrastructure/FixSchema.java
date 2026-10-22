package campusos.fixmycampus.infrastructure;

import campusos.common.Database;

/** DDL and demo seed for issues.db. See docs/db-uml.md for the diagram. */
public final class FixSchema {
    private FixSchema() {}

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS issue_categories (name TEXT PRIMARY KEY, sort_order INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS reports (
              id TEXT PRIMARY KEY, room_id TEXT NOT NULL,
              category TEXT NOT NULL REFERENCES issue_categories(name),
              description TEXT NOT NULL,
              status TEXT NOT NULL CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
              reporter_id TEXT NOT NULL, created_at TEXT NOT NULL,
              duplicate_of TEXT REFERENCES reports(id), assignee_id TEXT);
            CREATE TABLE IF NOT EXISTS report_audit (
              id INTEGER PRIMARY KEY AUTOINCREMENT, report_id TEXT REFERENCES reports(id),
              actor TEXT NOT NULL, action TEXT NOT NULL, detail TEXT, at TEXT NOT NULL DEFAULT (datetime('now')));
            CREATE TABLE IF NOT EXISTS notifications (
              id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, message TEXT NOT NULL,
              created_at TEXT NOT NULL DEFAULT (datetime('now')))
            """;

    public static void init(Database db) {
        db.executeScript(DDL);
        if (db.query("SELECT COUNT(*) FROM issue_categories", r -> r.getInt(1)).get(0) > 0) return;
        String[] categories = {"Projector", "Air conditioning", "Furniture", "Network", "Lighting", "Other"};
        db.transaction(tx -> {
            for (int i = 0; i < categories.length; i++) tx.update("INSERT INTO issue_categories VALUES (?,?)", categories[i], i);
            tx.update("INSERT INTO reports VALUES ('i1','D204','Projector','Image flickers after ten minutes.','IN_PROGRESS','u-student','2026-10-18',NULL,'u-teacher')");
            tx.update("INSERT INTO reports VALUES ('i2','F103','Network','Four workstations lose the network.','OPEN','u-student','2026-10-20',NULL,NULL)");
            tx.update("INSERT INTO reports VALUES ('i3','E117','Lighting','Rear row lights are out.','RESOLVED','u-teacher','2026-10-09',NULL,'u-teacher')");
            return null;
        });
    }
}
