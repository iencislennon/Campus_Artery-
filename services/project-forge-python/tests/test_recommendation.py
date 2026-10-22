import unittest

from domain.errors import ApiError
from domain.models import Initiative
from domain.recommendation import CategoryBasedStrategy, PopularityStrategy, RecentActivityStrategy, StrategyRegistry


def make(id_, category, followers, updated):
    return Initiative(id_, id_, category, "", "", "", followers, updated)


ITEMS = [make("a", "Event", 10, "2026-10-01"), make("b", "Research", 50, "2026-09-01"), make("c", "Party", 30, "2026-10-20")]


class RecommendationTests(unittest.TestCase):
    def test_each_strategy_orders_differently(self):
        ids = lambda strategy, cats=frozenset(): [i.id for i in strategy.rank(ITEMS, set(cats))]
        self.assertEqual(ids(PopularityStrategy()), ["b", "c", "a"])
        self.assertEqual(ids(RecentActivityStrategy()), ["c", "a", "b"])
        self.assertEqual(ids(CategoryBasedStrategy(), {"Event"}), ["a", "b", "c"])

    def test_registry_selects_at_runtime_and_is_extensible(self):
        class Alphabetical(PopularityStrategy):
            name = "alpha"

            def rank(self, initiatives, followed_categories):
                return sorted(initiatives, key=lambda i: i.title)

        registry = StrategyRegistry(PopularityStrategy())
        registry.register(Alphabetical())
        self.assertEqual([i.id for i in registry.get("alpha").rank(ITEMS, set())], ["a", "b", "c"])
        self.assertEqual(registry.get("").name, "popularity")
        with self.assertRaises(ApiError):
            registry.get("missing")


if __name__ == "__main__":
    unittest.main()
