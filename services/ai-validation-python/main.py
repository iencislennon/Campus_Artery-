"""Composition root for the AI validation service (placeholder AI logic, provider-agnostic)."""
import logging
import os

from application.validation_service import ValidationService
from domain.validators import DocumentValidator, IssueReportValidator
from infrastructure.external_provider import ExternalProvider
from presentation.server import serve


def build_service() -> ValidationService:
    documents = DocumentValidator()
    provider_url = os.environ.get("AI_PROVIDER_URL", "").strip()
    return ValidationService({"club-document": documents, "fixmycampus": IssueReportValidator(documents)},
                             ExternalProvider(provider_url) if provider_url else None)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="[ai-validation] %(message)s")
    serve(build_service(), int(os.environ.get("AI_VALIDATION_PORT", "8084")))
