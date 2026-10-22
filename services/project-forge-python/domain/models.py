from dataclasses import dataclass, field

CATEGORIES = ("Project", "Event", "Party", "Research")
NOTIFICATION_KINDS = ("lessons", "deadlines", "grades", "attendance")


@dataclass(frozen=True)
class AuthUser:
    id: str
    name: str
    role: str


@dataclass(frozen=True)
class Initiative:
    id: str
    title: str
    category: str
    summary: str
    organizer: str
    contact: str
    followers: int
    updated_at: str
    following: bool = False

    def to_json(self) -> dict:
        return {"id": self.id, "title": self.title, "category": self.category, "summary": self.summary,
                "organizer": self.organizer, "contact": self.contact, "followers": self.followers,
                "updatedAt": self.updated_at, "following": self.following}


@dataclass(frozen=True)
class Club:
    id: str
    name: str
    members: int
    joined: bool
    deadline: dict = field(default_factory=dict)
    owned: bool = False
    event: dict = field(default_factory=dict)

    def to_json(self) -> dict:
        return {"id": self.id, "name": self.name, "members": self.members, "joined": self.joined,
                "deadline": self.deadline, "owned": self.owned, "event": self.event}
