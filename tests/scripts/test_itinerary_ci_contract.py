"""#17 行程核心的数据文档与持续集成契约。"""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ItineraryCiContractTests(unittest.TestCase):
    def test_data_document_names_every_normalized_source_table(self):
        document = (ROOT / "docs/data/itinerary.md").read_text(encoding="utf-8")
        for table in (
            "itinerary",
            "itinerary_destination",
            "itinerary_day",
            "itinerary_item",
            "itinerary_command",
        ):
            with self.subTest(table=table):
                self.assertIn(f"`{table}`", document)
        self.assertIn("唯一事实源", document)
        self.assertIn("JSON", document)
        self.assertIn("不", document)

    def test_domain_language_and_adr_define_the_aggregate_boundary(self):
        context = (ROOT / "CONTEXT.md").read_text(encoding="utf-8")
        for term in ("日程日", "行程条目", "行程目的地", "行程命令", "行程版本", "行程负责人"):
            with self.subTest(term=term):
                self.assertIn(f"**{term}**", context)

        adr = (ROOT / "docs/adr/0003-itinerary-command-aggregate.md").read_text(encoding="utf-8")
        for phrase in ("规范化聚合", "命令边界", "通用 CRUD", "JSON"):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, adr)

    def test_ci_discovers_migrations_java_it_and_all_web_checks(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        runner = (ROOT / "scripts/run_backend_integration.py").read_text(encoding="utf-8")

        self.assertIn("python3 -m unittest discover -s tests -t . -v", workflow)
        self.assertIn("python3 -m scripts.run_backend_integration --containers", workflow)
        self.assertIn("identity and itinerary HTTP integration tests", workflow)
        self.assertIn("npm test", workflow)
        self.assertIn("npm run build", workflow)
        self.assertIn("failsafe:integration-test", runner)
        self.assertIn("failsafe:verify", runner)
        self.assertNotIn("-Dit.test=", runner)


if __name__ == "__main__":
    unittest.main()
