import uuid
from datetime import date

from domain.errors import not_found
from domain.models import Club, Initiative
from infrastructure.database import Database

INITIATIVE_SELECT = """
SELECT i.id, i.title, i.category, i.summary, i.organizer_name, i.contact, i.updated_at,
       (SELECT COUNT(*) FROM initiative_follows f WHERE f.initiative_id = i.id) AS followers,
       EXISTS (SELECT 1 FROM initiative_follows f WHERE f.initiative_id = i.id AND f.user_id = :viewer) AS following
FROM initiatives i
"""

DEFAULT_GRADES = (("Data Structures", 92, "A"), ("Web Technologies", 88, "A-"),
                  ("Discrete Mathematics", 79, "B+"), ("Software Architecture", 85, "B+"))
DEFAULT_FEED = (("grades", "New grade: Web Technologies midterm, 88.", "Today, 09:12"),
                ("attendance", "Attendance changed: Discrete Mathematics, 14 Oct marked present.", "Yesterday"),
                ("lessons", "Software Architecture moved to F203, 11:30.", "Yesterday"),
                ("deadlines", "Deadline in 3 days: Kaggle sprint sign-up.", "Mon"))


def _initiative(row) -> Initiative:
    return Initiative(row["id"], row["title"], row["category"], row["summary"], row["organizer_name"], row["contact"],
                      row["followers"], row["updated_at"], bool(row["following"]))


class InitiativeRepository:
    def __init__(self, db: Database):
        self._db = db

    def search(self, viewer_id: str, text: str = "", category: str = "") -> list[Initiative]:
        sql = INITIATIVE_SELECT + " WHERE i.title LIKE :text AND (:category = '' OR i.category = :category) ORDER BY i.updated_at DESC"
        with self._db.transaction() as conn:
            rows = conn.execute(sql, {"viewer": viewer_id, "text": f"%{text}%", "category": category}).fetchall()
        return [_initiative(r) for r in rows]

    def get(self, initiative_id: str, viewer_id: str) -> Initiative:
        with self._db.transaction() as conn:
            row = conn.execute(INITIATIVE_SELECT + " WHERE i.id = :id", {"viewer": viewer_id, "id": initiative_id}).fetchone()
        if row is None:
            raise not_found("Initiative not found.")
        return _initiative(row)

    def create(self, owner_id: str, owner_name: str, title: str, category: str, summary: str, contact: str) -> str:
        new_id = f"n-{uuid.uuid4().hex[:8]}"
        today = date.today().isoformat()
        with self._db.transaction() as conn:
            conn.execute("INSERT INTO initiatives VALUES (?,?,?,?,?,?,?,?,?)",
                         (new_id, title, category, summary, owner_id, owner_name, contact, today, today))
        return new_id

    def toggle_follow(self, initiative_id: str, user_id: str) -> None:
        with self._db.transaction() as conn:
            removed = conn.execute("DELETE FROM initiative_follows WHERE initiative_id = ? AND user_id = ?",
                                   (initiative_id, user_id)).rowcount
            if not removed:
                conn.execute("INSERT INTO initiative_follows (initiative_id, user_id) VALUES (?, ?)", (initiative_id, user_id))

    def followed_categories(self, user_id: str) -> set[str]:
        with self._db.transaction() as conn:
            rows = conn.execute("SELECT DISTINCT i.category FROM initiatives i JOIN initiative_follows f ON f.initiative_id = i.id "
                                "WHERE f.user_id = ?", (user_id,)).fetchall()
        return {r["category"] for r in rows}


class ClubRepository:
    def __init__(self, db: Database):
        self._db = db

    def list(self, viewer_id: str) -> list[Club]:
        sql = """
        SELECT c.id, c.name, c.owner_id, c.event_name, c.event_start, c.event_end, c.event_time_start, c.event_time_end, c.description, c.photo,
               (SELECT COUNT(*) FROM club_members m WHERE m.club_id = c.id) AS members,
               EXISTS (SELECT 1 FROM club_members m WHERE m.club_id = c.id AND m.user_id = ?) AS joined,
               (SELECT title FROM club_deadlines d WHERE d.club_id = c.id ORDER BY due_date LIMIT 1) AS deadline_title,
               (SELECT due_date FROM club_deadlines d WHERE d.club_id = c.id ORDER BY due_date LIMIT 1) AS deadline_date
        FROM clubs c ORDER BY c.name
        """
        with self._db.transaction() as conn:
            rows = conn.execute(sql, (viewer_id,)).fetchall()
        return [Club(r["id"], r["name"], r["members"], bool(r["joined"]),
                     {"title": r["deadline_title"] or "No deadline", "date": r["deadline_date"] or "9999-12-31"},
                     r["owner_id"] == viewer_id,
                     {"name": r["event_name"], "start": r["event_start"], "end": r["event_end"], "startTime": r["event_time_start"], "endTime": r["event_time_end"],
                      "description": r["description"], "photo": r["photo"]}) for r in rows]

    def create(self, owner_id: str, name: str) -> str:
        club_id = f"c-{uuid.uuid4().hex[:8]}"
        try:
            with self._db.transaction() as conn:
                conn.execute("INSERT INTO clubs (id, name, owner_id) VALUES (?, ?, ?)", (club_id, name, owner_id))
        except Exception as error:
            if "UNIQUE constraint" in str(error):
                raise ValueError("duplicate club") from error
            raise
        return club_id

    def add_deadline(self, club_id: str, title: str, due_date: str) -> None:
        with self._db.transaction() as conn:
            conn.execute("INSERT INTO club_deadlines (id, club_id, title, due_date) VALUES (?,?,?,?)",
                         (f"d-{uuid.uuid4().hex[:8]}", club_id, title, due_date))

    def get(self, club_id: str, viewer_id: str) -> Club:
        match = [c for c in self.list(viewer_id) if c.id == club_id]
        if not match:
            raise not_found("Club not found.")
        return match[0]

    def update_event(self, club_id: str, fields: dict) -> None:
        columns = ", ".join(f"{column} = :{column}" for column in fields)
        with self._db.transaction() as conn:
            conn.execute(f"UPDATE clubs SET {columns} WHERE id = :club_id", {**fields, "club_id": club_id})

    def toggle_membership(self, club_id: str, user_id: str) -> None:
        with self._db.transaction() as conn:
            removed = conn.execute("DELETE FROM club_members WHERE club_id = ? AND user_id = ?", (club_id, user_id)).rowcount
            if not removed:
                conn.execute("INSERT INTO club_members (club_id, user_id) VALUES (?, ?)", (club_id, user_id))

    def deadlines(self) -> list[dict]:
        with self._db.transaction() as conn:
            rows = conn.execute("SELECT title, due_date FROM club_deadlines ORDER BY due_date").fetchall()
        return [{"title": r["title"], "date": r["due_date"]} for r in rows]


class ProfileRepository:
    def __init__(self, db: Database):
        self._db = db

    def ensure(self, user_id: str, name: str) -> None:
        """First visit: create the profile and demo academic data (grades and notifications are simulated)."""
        with self._db.transaction() as conn:
            if conn.execute("SELECT 1 FROM profiles WHERE user_id = ?", (user_id,)).fetchone():
                return
            conn.execute("INSERT INTO profiles (user_id, name, email, phone) VALUES (?,?,?,?)",
                         (user_id, name, f"{user_id.removeprefix('u-')}@sdu.edu.kz", "+7 700 000 00 00"))
            conn.executemany("INSERT INTO grades (user_id, course, score, letter) VALUES (?,?,?,?)",
                             [(user_id, *g) for g in DEFAULT_GRADES])
            conn.executemany("INSERT INTO feed_items (user_id, kind, text, happened_at) VALUES (?,?,?,?)",
                             [(user_id, *f) for f in DEFAULT_FEED])

    def get(self, user_id: str) -> dict:
        with self._db.transaction() as conn:
            row = conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
        return {"id": row["user_id"], "name": row["name"], "email": row["email"], "phone": row["phone"],
                "telegram": row["telegram"], "photo": row["photo"],
                "notifications": {k: bool(row[f"notify_{k}"]) for k in ("lessons", "deadlines", "grades", "attendance")}}

    def update(self, user_id: str, fields: dict) -> None:
        columns = ", ".join(f"{column} = :{column}" for column in fields)
        with self._db.transaction() as conn:
            conn.execute(f"UPDATE profiles SET {columns} WHERE user_id = :user_id", {**fields, "user_id": user_id})

    def grades(self, user_id: str) -> list[dict]:
        with self._db.transaction() as conn:
            rows = conn.execute("SELECT course, score, letter FROM grades WHERE user_id = ? ORDER BY id", (user_id,)).fetchall()
        return [{"course": r["course"], "grade": r["score"], "letter": r["letter"]} for r in rows]

    def feed(self, user_id: str, kinds: list[str]) -> list[dict]:
        if not kinds:
            return []
        marks = ",".join("?" * len(kinds))
        with self._db.transaction() as conn:
            rows = conn.execute(f"SELECT id, kind, text, happened_at FROM feed_items WHERE user_id = ? AND kind IN ({marks}) ORDER BY id",
                                (user_id, *kinds)).fetchall()
        return [{"id": f"f{r['id']}", "kind": r["kind"], "text": r["text"], "at": r["happened_at"]} for r in rows]

    def clubs(self, user_id: str) -> list[str]:
        with self._db.transaction() as conn:
            rows = conn.execute("SELECT c.name FROM clubs c JOIN club_members m ON m.club_id = c.id WHERE m.user_id = ? ORDER BY c.name",
                                (user_id,)).fetchall()
        return [r["name"] for r in rows]
