import logging
import re

from domain.errors import ApiError, conflict, invalid
from domain.models import CATEGORIES, NOTIFICATION_KINDS, AuthUser
from domain.recommendation import StrategyRegistry
from infrastructure.repositories import ClubRepository, InitiativeRepository, ProfileRepository
from infrastructure.ai_client import AiValidationClient

log = logging.getLogger("project-forge")
EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
MAX_PHOTO_CHARS = 1_500_000
RECOMMENDATION_LIMIT = 3


class InitiativeService:
    def __init__(self, initiatives: InitiativeRepository, strategies: StrategyRegistry):
        self._initiatives, self._strategies = initiatives, strategies

    def search(self, user: AuthUser, text: str, category: str):
        return self._initiatives.search(user.id, text, category)

    def create(self, user: AuthUser, title: str, category: str, summary: str, contact: str):
        title, summary, contact = (title or "").strip(), (summary or "").strip(), (contact or "").strip()
        if not title or not summary:
            raise invalid("Title and summary are required.")
        if category not in CATEGORIES:
            raise invalid(f"Category must be one of {', '.join(CATEGORIES)}.")
        if not EMAIL.match(contact):
            raise invalid("Enter a valid contact email.")
        new_id = self._initiatives.create(user.id, user.name, title, category, summary, contact)
        log.info("initiative %s created by %s", new_id, user.id)
        return self._initiatives.get(new_id, user.id)

    def toggle_follow(self, user: AuthUser, initiative_id: str):
        self._initiatives.get(initiative_id, user.id)  # 404 if missing
        self._initiatives.toggle_follow(initiative_id, user.id)
        return self._initiatives.get(initiative_id, user.id)

    def recommend(self, user: AuthUser, strategy_name: str):
        strategy = self._strategies.get(strategy_name)
        ranked = strategy.rank(self._initiatives.search(user.id), self._initiatives.followed_categories(user.id))
        return ranked[:RECOMMENDATION_LIMIT]


class ClubService:
    def __init__(self, clubs: ClubRepository, ai: AiValidationClient):
        self._clubs, self._ai = clubs, ai

    def list(self, user: AuthUser):
        return self._clubs.list(user.id)

    def toggle(self, user: AuthUser, club_id: str):
        self._clubs.get(club_id, user.id)
        self._clubs.toggle_membership(club_id, user.id)
        return self._clubs.get(club_id, user.id)

    def create(self, user: AuthUser, name: str, deadline_title: str = "", due_date: str = "", document: dict | None = None):
        name, deadline_title, due_date = (name or "").strip(), (deadline_title or "").strip(), (due_date or "").strip()
        if not name or len(name) > 80:
            raise invalid("Club name is required and must be 80 characters or fewer.")
        if (deadline_title and not due_date) or (due_date and not deadline_title):
            raise invalid("Provide both a deadline title and date, or leave both empty.")
        if due_date and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", due_date):
            raise invalid("Deadline date must use YYYY-MM-DD format.")
        if not isinstance(document, dict):
            raise invalid("Upload a club registration document.")
        verification = self._ai.validate_club_document(document)
        if not verification.get("valid"):
            raise invalid("Club document rejected by AI: " + str(verification.get("reason", "The document is not valid.")))
        try:
            club_id = self._clubs.create(user.id, name)
        except ValueError:
            raise conflict("A club with this name already exists.") from None
        if deadline_title:
            self._clubs.add_deadline(club_id, deadline_title, due_date)
        log.info("club %s created by %s", club_id, user.id)
        club = self._clubs.get(club_id, user.id).to_json()
        club["aiVerification"] = verification
        return club


class ProfileService:
    def __init__(self, profiles: ProfileRepository, clubs: ClubRepository):
        self._profiles, self._clubs = profiles, clubs

    def overview(self, user: AuthUser) -> dict:
        self._profiles.ensure(user.id, user.name)
        profile = self._profiles.get(user.id)
        enabled = [kind for kind, on in profile["notifications"].items() if on]
        return {"profile": {**profile, "role": user.role}, "grades": self._profiles.grades(user.id),
                "clubs": self._profiles.clubs(user.id), "deadlines": self._clubs.deadlines(),
                "feed": self._profiles.feed(user.id, enabled)}

    def update(self, user: AuthUser, body: dict) -> dict:
        self._profiles.ensure(user.id, user.name)
        fields = self._validated_fields(body)
        if fields:
            self._profiles.update(user.id, fields)
        return {**self._profiles.get(user.id), "role": user.role}

    @staticmethod
    def _validated_fields(body: dict) -> dict:
        fields: dict = {}
        if "name" in body:
            if not str(body["name"]).strip():
                raise invalid("Name cannot be empty.")
            fields["name"] = str(body["name"]).strip()
        if "email" in body:
            if not EMAIL.match(str(body["email"])):
                raise invalid("Enter a valid email address.")
            fields["email"] = str(body["email"]).strip()
        for key in ("phone", "telegram"):
            if key in body:
                if len(str(body[key])) > 40:
                    raise invalid(f"{key.capitalize()} is too long.")
                fields[key] = str(body[key]).strip()
        if "photo" in body:
            if len(str(body["photo"])) > MAX_PHOTO_CHARS:
                raise ApiError(413, "PAYLOAD_TOO_LARGE", "Photo is too large. Use an image under 1 MB.")
            fields["photo"] = str(body["photo"])
        flags = body.get("notifications") or {}
        fields.update({f"notify_{kind}": int(bool(flags[kind])) for kind in NOTIFICATION_KINDS if kind in flags})
        return fields
