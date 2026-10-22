import json
import uuid

from domain.errors import not_found
from infrastructure.database import Database


def _new_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:8]}"


class SurveyRepository:
    def __init__(self, db: Database):
        self._db = db

    def list_for_club(self, club_id: str) -> list[dict]:
        sql = """SELECT s.id, s.title, s.description,
                        (SELECT COUNT(*) FROM survey_questions q WHERE q.survey_id = s.id) AS questions,
                        (SELECT COUNT(*) FROM survey_responses r WHERE r.survey_id = s.id) AS responses
                 FROM surveys s WHERE s.club_id = ? ORDER BY s.created_at, s.rowid"""
        with self._db.transaction() as conn:
            rows = conn.execute(sql, (club_id,)).fetchall()
        return [dict(r) for r in rows]

    def get(self, survey_id: str) -> dict:
        with self._db.transaction() as conn:
            survey = conn.execute("SELECT id, club_id, title, description FROM surveys WHERE id = ?", (survey_id,)).fetchone()
            if survey is None:
                raise not_found("Survey not found.")
            questions = conn.execute("SELECT id, text, kind, options, required FROM survey_questions "
                                     "WHERE survey_id = ? ORDER BY position", (survey_id,)).fetchall()
        return {"id": survey["id"], "clubId": survey["club_id"], "title": survey["title"],
                "description": survey["description"],
                "questions": [{"id": q["id"], "text": q["text"], "kind": q["kind"],
                               "options": json.loads(q["options"]), "required": bool(q["required"])} for q in questions]}

    def create(self, club_id: str, title: str, description: str, questions: list[dict]) -> str:
        survey_id = _new_id("s")
        with self._db.transaction() as conn:
            conn.execute("INSERT INTO surveys (id, club_id, title, description) VALUES (?,?,?,?)",
                         (survey_id, club_id, title, description))
            self._write_questions(conn, survey_id, questions)
        return survey_id

    def update(self, survey_id: str, title: str, description: str, questions: list[dict]) -> None:
        with self._db.transaction() as conn:
            conn.execute("UPDATE surveys SET title = ?, description = ? WHERE id = ?", (title, description, survey_id))
            keep = [q["id"] for q in questions if q.get("id")]
            marks = ",".join("?" * len(keep))
            conn.execute(f"DELETE FROM survey_questions WHERE survey_id = ? AND id NOT IN ({marks})" if keep
                         else "DELETE FROM survey_questions WHERE survey_id = ?", (survey_id, *keep))
            self._write_questions(conn, survey_id, questions)

    @staticmethod
    def _write_questions(conn, survey_id: str, questions: list[dict]) -> None:
        for position, q in enumerate(questions):
            values = (position, q["text"], q["kind"], json.dumps(q["options"]), int(q["required"]))
            known = q.get("id") and conn.execute("SELECT 1 FROM survey_questions WHERE id = ? AND survey_id = ?",
                                                 (q["id"], survey_id)).fetchone()
            if known:
                conn.execute("UPDATE survey_questions SET position=?, text=?, kind=?, options=?, required=? WHERE id=?",
                             (*values, q["id"]))
            else:
                conn.execute("INSERT INTO survey_questions (id, survey_id, position, text, kind, options, required) "
                             "VALUES (?,?,?,?,?,?,?)", (_new_id("q"), survey_id, *values))

    def delete(self, survey_id: str) -> None:
        with self._db.transaction() as conn:
            conn.execute("DELETE FROM surveys WHERE id = ?", (survey_id,))

    def save_response(self, survey_id: str, user_id: str, answers: dict) -> None:
        with self._db.transaction() as conn:
            conn.execute("INSERT INTO survey_responses (id, survey_id, user_id, answers) VALUES (?,?,?,?) "
                         "ON CONFLICT (survey_id, user_id) DO UPDATE SET answers = excluded.answers, created_at = datetime('now')",
                         (_new_id("r"), survey_id, user_id, json.dumps(answers)))

    def has_responded(self, survey_id: str, user_id: str) -> bool:
        with self._db.transaction() as conn:
            return conn.execute("SELECT 1 FROM survey_responses WHERE survey_id = ? AND user_id = ?",
                                (survey_id, user_id)).fetchone() is not None

    def responses(self, survey_id: str) -> list[dict]:
        with self._db.transaction() as conn:
            rows = conn.execute("SELECT answers FROM survey_responses WHERE survey_id = ? ORDER BY created_at", (survey_id,)).fetchall()
        return [json.loads(r["answers"]) for r in rows]
