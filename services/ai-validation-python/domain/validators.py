"""Placeholder AI logic: deterministic local rules behind the Validator port.

Swap or extend these classes (or set AI_PROVIDER_URL) to plug in a real model; nothing else changes.
"""
import base64
import re
from typing import Protocol

from domain.verdict import Verdict, reject

MAX_DOCUMENT_BYTES = 5_000_000
ALLOWED_DOCUMENT_TYPES = {"application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain"}
MIN_DESCRIPTION_CHARS, MAX_DESCRIPTION_CHARS, MIN_DESCRIPTION_WORDS = 10, 1000, 5
ISSUE_WORDS = ("broken", "not working", "leak", "danger", "не работает", "слом", "протеч", "опас")
IMAGE_SIGNATURES = (b"\xff\xd8", b"\x89PNG", b"RIFF")


class Validator(Protocol):
    def validate(self, body: dict) -> Verdict: ...


class DocumentValidator:
    """Checks that an uploaded document is present, an allowed type, intact and within the size limit."""

    def validate(self, body: dict) -> Verdict:
        name, mime = str(body.get("filename") or "").strip(), str(body.get("mimeType") or "").lower().strip()
        raw = str(body.get("contentBase64") or "")
        if not name or not raw:
            return reject("A non-empty document is required.")
        if mime not in ALLOWED_DOCUMENT_TYPES:
            return reject("Use a PDF, PNG, JPG, WEBP or TXT document.")
        try:
            content = base64.b64decode(raw, validate=True)
        except (ValueError, TypeError):
            return reject("The uploaded document is corrupted.")
        if not content or len(content) > MAX_DOCUMENT_BYTES:
            return reject("The document is empty or exceeds 5 MB.")
        if mime == "application/pdf" and not content.startswith(b"%PDF"):
            return reject("The file is not a valid PDF.", 0.96)
        if mime.startswith("image/") and not content.startswith(IMAGE_SIGNATURES):
            return reject("The file is not a valid image.", 0.94)
        return Verdict(True, 0.86, "The document is readable and has an allowed format.", ("format", "integrity", "size"))


class IssueReportValidator:
    """Checks that a FixMyCampus description looks like a real campus issue and its evidence file is sound."""

    def __init__(self, documents: DocumentValidator):
        self._documents = documents

    def validate(self, body: dict) -> Verdict:
        description = str(body.get("description") or "").strip()
        if len(description) < MIN_DESCRIPTION_CHARS:
            return reject(f"Describe the issue in at least {MIN_DESCRIPTION_CHARS} characters.")
        if len(description) > MAX_DESCRIPTION_CHARS:
            return reject(f"The issue description is limited to {MAX_DESCRIPTION_CHARS} characters.")
        if not self._is_meaningful(description):
            return reject("The description does not contain enough information about a campus issue.", 0.75)
        attachment = body.get("attachment")
        if attachment:
            evidence = self._documents.validate(attachment)
            if not evidence.valid:
                return reject("Evidence file rejected: " + evidence.reason, evidence.confidence)
        return Verdict(True, 0.84, "The report describes a plausible campus issue.", ("description", "category", "attachment"))

    @staticmethod
    def _is_meaningful(description: str) -> bool:
        return any(word in description.lower() for word in ISSUE_WORDS) or len(re.findall(r"\b\w+\b", description)) >= MIN_DESCRIPTION_WORDS
