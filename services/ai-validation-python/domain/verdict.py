from dataclasses import dataclass, field


@dataclass(frozen=True)
class Verdict:
    valid: bool
    confidence: float
    reason: str
    checks: tuple[str, ...] = field(default_factory=tuple)

    def to_json(self) -> dict:
        body = {"valid": self.valid, "confidence": self.confidence, "reason": self.reason}
        if self.checks:
            body["checks"] = list(self.checks)
        return body


def reject(reason: str, confidence: float = 0.99) -> Verdict:
    return Verdict(False, confidence, reason)
