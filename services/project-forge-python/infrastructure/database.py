import sqlite3
from contextlib import contextmanager
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS initiatives (
  id TEXT PRIMARY KEY, title TEXT NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('Project','Event','Party','Research')),
  summary TEXT NOT NULL, organizer_id TEXT NOT NULL, organizer_name TEXT NOT NULL, contact TEXT NOT NULL,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS initiative_follows (
  initiative_id TEXT NOT NULL REFERENCES initiatives(id) ON DELETE CASCADE, user_id TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')), PRIMARY KEY (initiative_id, user_id));
CREATE TABLE IF NOT EXISTS clubs (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE);
CREATE TABLE IF NOT EXISTS club_members (
  club_id TEXT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE, user_id TEXT NOT NULL,
  PRIMARY KEY (club_id, user_id));
CREATE TABLE IF NOT EXISTS club_deadlines (
  id TEXT PRIMARY KEY, club_id TEXT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
  title TEXT NOT NULL, due_date TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS profiles (
  user_id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '',
  telegram TEXT NOT NULL DEFAULT '', photo TEXT NOT NULL DEFAULT '',
  notify_lessons INTEGER NOT NULL DEFAULT 1, notify_deadlines INTEGER NOT NULL DEFAULT 1,
  notify_grades INTEGER NOT NULL DEFAULT 1, notify_attendance INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS grades (
  id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
  course TEXT NOT NULL, score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100), letter TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS surveys (
  id TEXT PRIMARY KEY, club_id TEXT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
  title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL DEFAULT (datetime('now')));
CREATE TABLE IF NOT EXISTS survey_questions (
  id TEXT PRIMARY KEY, survey_id TEXT NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
  position INTEGER NOT NULL, text TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('short','file','choice','checkbox')),
  options TEXT NOT NULL DEFAULT '[]', required INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS survey_responses (
  id TEXT PRIMARY KEY, survey_id TEXT NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL, answers TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (survey_id, user_id));
CREATE TABLE IF NOT EXISTS feed_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('lessons','deadlines','grades','attendance')),
  text TEXT NOT NULL, happened_at TEXT NOT NULL);
"""

# Columns added to `clubs` after the first release; applied to existing databases on start.
CLUB_PAGE_COLUMNS = ("owner_id", "event_name", "event_start", "event_end", "event_time_start", "event_time_end", "description", "photo")

SEED = """
INSERT INTO initiatives VALUES ('n1','SDU Hackathon 2026','Event','48 hours of building. Teams of up to four.','seed','Aruzhan S.','aruzhan@sdu.edu.kz','2026-10-01','2026-10-19');
INSERT INTO initiatives VALUES ('n2','Campus Air Quality Study','Research','Student sensors across Blocks D to I.','seed','Dr. Dina Kassymova','d.kassymova@sdu.edu.kz','2026-09-20','2026-10-15');
INSERT INTO initiatives VALUES ('n3','Autumn Film Night','Party','Open-air screening, bring a blanket.','seed','Timur B.','timur@sdu.edu.kz','2026-10-10','2026-10-20');
INSERT INTO initiatives VALUES ('n4','Open Source Guild','Project','Weekly contributions to real repositories.','seed','Madina K.','madina@sdu.edu.kz','2026-09-01','2026-10-12');
INSERT INTO clubs (id, name) VALUES ('c1','Robotics Club');
INSERT INTO clubs (id, name) VALUES ('c2','Debate Society');
INSERT INTO clubs (id, name) VALUES ('c3','Data Science Circle');
INSERT INTO club_deadlines VALUES ('d1','c1','Spring cohort applications','2026-11-05');
INSERT INTO club_deadlines VALUES ('d2','c2','Registration for inter-university cup','2026-10-30');
INSERT INTO club_deadlines VALUES ('d3','c3','Kaggle sprint sign-up','2026-11-12');
"""

# (initiative id, number of demo followers); (club id, number of demo members)
DEMO_FOLLOWERS = (("n1", 141), ("n2", 37), ("n3", 77), ("n4", 91))
DEMO_MEMBERS = (("c1", 54), ("c2", 32), ("c3", 61))


class Database:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._path = str(path)
        with self.transaction() as conn:
            conn.executescript(SCHEMA)
            self._add_missing_club_columns(conn)
            self._allow_file_questions(conn)
            if conn.execute("SELECT COUNT(*) FROM initiatives").fetchone()[0] == 0:
                self._seed(conn)

    @contextmanager
    def transaction(self):
        conn = sqlite3.connect(self._path, timeout=5)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    @staticmethod
    def _add_missing_club_columns(conn) -> None:
        existing = {row["name"] for row in conn.execute("PRAGMA table_info(clubs)")}
        for column in CLUB_PAGE_COLUMNS:
            if column not in existing:
                conn.execute(f"ALTER TABLE clubs ADD COLUMN {column} TEXT NOT NULL DEFAULT ''")

    @staticmethod
    def _allow_file_questions(conn) -> None:
        """Databases created before document-upload questions have a CHECK without 'file'; rebuild the table once."""
        ddl = conn.execute("SELECT sql FROM sqlite_master WHERE name = 'survey_questions'").fetchone()["sql"]
        if "'file'" in ddl:
            return
        conn.execute("ALTER TABLE survey_questions RENAME TO survey_questions_old")
        conn.executescript(SCHEMA)
        conn.execute("INSERT INTO survey_questions SELECT id, survey_id, position, text, "
                     "CASE kind WHEN 'paragraph' THEN 'short' ELSE kind END, options, required FROM survey_questions_old")
        conn.execute("DROP TABLE survey_questions_old")

    @staticmethod
    def _seed(conn) -> None:
        conn.executescript(SEED)
        conn.executemany("INSERT INTO initiative_follows (initiative_id, user_id) VALUES (?, ?)",
                         [(ini, f"seed-f{n}") for ini, count in DEMO_FOLLOWERS for n in range(count)])
        conn.executemany("INSERT INTO club_members (club_id, user_id) VALUES (?, ?)",
                         [(club, f"seed-m{n}") for club, count in DEMO_MEMBERS for n in range(count)])
        conn.execute("INSERT INTO initiative_follows (initiative_id, user_id) VALUES ('n2', 'u-student')")
        conn.execute("INSERT INTO club_members (club_id, user_id) VALUES ('c2', 'u-student')")
