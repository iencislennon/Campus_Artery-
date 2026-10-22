"""Strategy pattern: interchangeable ways of ranking initiatives, chosen per request."""
from abc import ABC, abstractmethod

from domain.errors import invalid
from domain.models import Initiative


class RecommendationStrategy(ABC):
    name: str

    @abstractmethod
    def rank(self, initiatives: list[Initiative], followed_categories: set[str]) -> list[Initiative]:
        ...


class PopularityStrategy(RecommendationStrategy):
    name = "popularity"

    def rank(self, initiatives, followed_categories):
        return sorted(initiatives, key=lambda i: i.followers, reverse=True)


class RecentActivityStrategy(RecommendationStrategy):
    name = "recent"

    def rank(self, initiatives, followed_categories):
        return sorted(initiatives, key=lambda i: i.updated_at, reverse=True)


class CategoryBasedStrategy(RecommendationStrategy):
    name = "category"

    def rank(self, initiatives, followed_categories):
        return sorted(initiatives, key=lambda i: (i.category not in followed_categories, -i.followers))


class StrategyRegistry:
    def __init__(self, *strategies: RecommendationStrategy):
        self._by_name = {s.name: s for s in strategies}

    def register(self, strategy: RecommendationStrategy) -> None:
        self._by_name[strategy.name] = strategy

    def get(self, name: str) -> RecommendationStrategy:
        try:
            return self._by_name[name or "popularity"]
        except KeyError:
            raise invalid(f"Unknown strategy '{name}'. Use one of {sorted(self._by_name)}.") from None
