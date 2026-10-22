import base64
import logging
import re

from domain.errors import ApiError, invalid, not_found
from domain.models import AuthUser
from infrastructure.repositories import ClubRepository
from infrastructure.survey_repository import SurveyRepository

log = logging.getLogger("project-forge")
QUESTION_KINDS = ("short", "file", "choice", "checkbox")
CHOICE_KINDS = ("choice", "checkbox")
TIME = re.compile(r"([01]\d|2[0-3]):[0-5]\d")
MAX_QUESTIONS = 30
MAX_OPTIONS = 20
MAX_ANSWER_CHARS = 2000
MAX_FILE_BYTES = 5_000_000
ALLOWED_FILE_TYPES = {"application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain"}


class SurveyService:
    """Surveys belong to a club; only the club owner may create, edit, delete them or read the results."""

    def __init__(self, surveys: SurveyRepository, clubs: ClubRepository):
        self._surveys, self._clubs = surveys, clubs

    def club_page(self, user: AuthUser, club_id: str) -> dict:
        club = self._clubs.get(club_id, user.id)
        surveys = [{**s, "responded": self._surveys.has_responded(s["id"], user.id)} for s in self._surveys.list_for_club(club_id)]
        return {**club.to_json(), "surveys": surveys}

    def update_event(self, user: AuthUser, club_id: str, body: dict) -> dict:
        self._require_owner(user, club_id)
        event_name, description = str(body.get("eventName") or "").strip(), str(body.get("description") or "").strip()
        start, end = str(body.get("startDate") or "").strip(), str(body.get("endDate") or "").strip()
        if len(event_name) > 120 or len(description) > 2000:
            raise invalid("Event name is limited to 120 characters and the description to 2000.")
        if end and not start:
            raise invalid("Set a start date before the end date.")
        if start and end and end < start:
            raise invalid("The end date cannot be before the start date.")
        start_time, end_time = str(body.get("startTime") or "").strip(), str(body.get("endTime") or "").strip()
        if any(t and not TIME.fullmatch(t) for t in (start_time, end_time)):
            raise invalid("Times must use HH:MM format.")
        if end_time and not start_time:
            raise invalid("Set a start time before the end time.")
        if start_time and end_time and start == end and end_time < start_time:
            raise invalid("The end time cannot be before the start time.")
        photo = str(body.get("photo") or "")
        if len(photo) > 1_500_000:
            raise ApiError(413, "PAYLOAD_TOO_LARGE", "Photo is too large. Use an image under 1 MB.")
        self._clubs.update_event(club_id, {"event_name": event_name, "event_start": start, "event_end": end, "event_time_start": start_time, "event_time_end": end_time,
                                           "description": description, "photo": photo})
        return self.club_page(user, club_id)

    def get(self, user: AuthUser, survey_id: str) -> dict:
        survey = self._surveys.get(survey_id)
        owned = self._clubs.get(survey["clubId"], user.id).owned
        return {**survey, "owned": owned, "responded": self._surveys.has_responded(survey_id, user.id)}

    def create(self, user: AuthUser, body: dict) -> dict:
        club_id = str(body.get("clubId") or "")
        self._require_owner(user, club_id)
        title, description, questions = self._validated(body)
        survey_id = self._surveys.create(club_id, title, description, questions)
        log.info("survey %s created by %s", survey_id, user.id)
        return self.get(user, survey_id)

    def update(self, user: AuthUser, body: dict) -> dict:
        survey_id = str(body.get("id") or "")
        self._require_owner(user, self._surveys.get(survey_id)["clubId"])
        title, description, questions = self._validated(body)
        self._surveys.update(survey_id, title, description, questions)
        return self.get(user, survey_id)

    def delete(self, user: AuthUser, survey_id: str) -> dict:
        self._require_owner(user, self._surveys.get(survey_id)["clubId"])
        self._surveys.delete(survey_id)
        return {"deleted": survey_id}

    def respond(self, user: AuthUser, survey_id: str, answers) -> dict:
        survey = self._surveys.get(survey_id)
        if not isinstance(answers, dict):
            raise invalid("Answers must be an object keyed by question id.")
        clean = {q["id"]: self._clean_answer(q, answers.get(q["id"])) for q in survey["questions"]}
        self._surveys.save_response(survey_id, user.id, clean)
        return {"saved": True}

    def results(self, user: AuthUser, survey_id: str) -> dict:
        survey = self._surveys.get(survey_id)
        self._require_owner(user, survey["clubId"])
        responses = self._surveys.responses(survey_id)
        return {"title": survey["title"], "total": len(responses),
                "questions": [self._summary(q, [r.get(q["id"]) for r in responses]) for q in survey["questions"]]}

    def answer_file(self, user: AuthUser, survey_id: str, question_id: str, response_number: int) -> dict:
        survey = self._surveys.get(survey_id)
        self._require_owner(user, survey["clubId"])
        responses = self._surveys.responses(survey_id)
        answer = responses[response_number].get(question_id) if 0 <= response_number < len(responses) else None
        if not isinstance(answer, dict):
            raise not_found("File not found.")
        return answer

    def _require_owner(self, user: AuthUser, club_id: str) -> None:
        if not self._clubs.get(club_id, user.id).owned:
            raise ApiError(403, "FORBIDDEN", "Only the club owner can do this.")

    @staticmethod
    def _validated(body: dict):
        title, description = str(body.get("title") or "").strip(), str(body.get("description") or "").strip()
        if not title or len(title) > 120:
            raise invalid("Survey title is required and must be 120 characters or fewer.")
        raw = body.get("questions")
        if not isinstance(raw, list) or not raw:
            raise invalid("Add at least one question.")
        if len(raw) > MAX_QUESTIONS:
            raise invalid(f"A survey can have at most {MAX_QUESTIONS} questions.")
        return title, description, [SurveyService._validated_question(n, q) for n, q in enumerate(raw, 1)]

    @staticmethod
    def _validated_question(number: int, q) -> dict:
        if not isinstance(q, dict):
            raise invalid(f"Question {number} is malformed.")
        text, kind = str(q.get("text") or "").strip(), q.get("kind")
        if not text:
            raise invalid(f"Question {number} needs a title.")
        if kind not in QUESTION_KINDS:
            raise invalid(f"Question {number} has an unknown type.")
        options = [str(o).strip() for o in (q.get("options") or []) if str(o).strip()] if kind in CHOICE_KINDS else []
        if kind in CHOICE_KINDS and not 2 <= len(options) <= MAX_OPTIONS:
            raise invalid(f"Question {number} needs between 2 and {MAX_OPTIONS} options.")
        return {"id": q.get("id") or "", "text": text, "kind": kind, "options": options, "required": bool(q.get("required"))}

    @staticmethod
    def _clean_answer(question: dict, answer):
        kind, options = question["kind"], question["options"]
        empty = answer in (None, "", [])
        if empty:
            if question["required"]:
                raise invalid(f"“{question['text']}” is required.")
            return [] if kind == "checkbox" else ""
        if kind == "file":
            return SurveyService._clean_file(question, answer)
        if kind == "checkbox":
            if not isinstance(answer, list) or any(a not in options for a in answer):
                raise invalid(f"Pick valid options for “{question['text']}”.")
            return list(dict.fromkeys(answer))
        if kind == "choice":
            if answer not in options:
                raise invalid(f"Pick a valid option for “{question['text']}”.")
            return answer
        return str(answer).strip()[:MAX_ANSWER_CHARS]

    @staticmethod
    def _clean_file(question: dict, answer) -> dict:
        label = f"“{question['text']}”"
        if not isinstance(answer, dict) or not answer.get("filename") or answer.get("mimeType") not in ALLOWED_FILE_TYPES:
            raise invalid(f"Upload a PDF, PNG, JPG, WEBP or TXT file for {label}.")
        try:
            content = base64.b64decode(str(answer.get("contentBase64") or ""), validate=True)
        except ValueError:
            raise invalid(f"The file for {label} is corrupted.") from None
        if not content or len(content) > MAX_FILE_BYTES:
            raise invalid(f"The file for {label} is empty or larger than 5 MB.")
        return {"filename": str(answer["filename"])[:200], "mimeType": answer["mimeType"], "contentBase64": answer["contentBase64"]}

    @staticmethod
    def _summary(question: dict, answers: list) -> dict:
        base = {"id": question["id"], "text": question["text"], "kind": question["kind"]}
        if question["kind"] in CHOICE_KINDS:
            counts = {o: 0 for o in question["options"]}
            for answer in answers:
                for picked in (answer if isinstance(answer, list) else [answer]):
                    if picked in counts:
                        counts[picked] += 1
            return {**base, "counts": counts}
        if question["kind"] == "file":
            return {**base, "files": [{"n": n, "filename": a["filename"]} for n, a in enumerate(answers) if isinstance(a, dict)]}
        return {**base, "texts": [a for a in answers if a]}
