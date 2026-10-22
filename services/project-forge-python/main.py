"""Composition root for ProjectForge."""
import logging
import os
from pathlib import Path

from application.survey_service import SurveyService
from infrastructure.survey_repository import SurveyRepository
from application.services import ClubService, InitiativeService, ProfileService
from domain.recommendation import CategoryBasedStrategy, PopularityStrategy, RecentActivityStrategy, StrategyRegistry
from infrastructure.database import Database
from infrastructure.repositories import ClubRepository, InitiativeRepository, ProfileRepository
from infrastructure.ai_client import AiValidationClient
from presentation.server import Routes, serve


def build_routes(db_path: Path) -> Routes:
    db = Database(db_path)
    initiatives, clubs, profiles = InitiativeRepository(db), ClubRepository(db), ProfileRepository(db)
    strategies = StrategyRegistry(PopularityStrategy(), RecentActivityStrategy(), CategoryBasedStrategy())
    ai = AiValidationClient(os.environ.get("AI_VALIDATION_URL", "http://localhost:8084"))
    return Routes(InitiativeService(initiatives, strategies), ClubService(clubs, ai), ProfileService(profiles, clubs),
                  SurveyService(SurveyRepository(db), clubs))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="[project-forge] %(message)s")
    data_dir = Path(os.environ.get("CAMPUSOS_DATA_DIR", "data"))
    serve(build_routes(data_dir / "social.db"), int(os.environ.get("PROJECTFORGE_PORT", "8083")),
          os.environ.get("CAMPUSOS_ALLOWED_ORIGIN", "*"))
