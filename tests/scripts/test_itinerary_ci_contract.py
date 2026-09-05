"""#17 行程核心的数据文档与持续集成契约。"""

from pathlib import Path
import re
import unittest

from tests.scripts.migration_specs import ITINERARY_DAILY_MAP_MIGRATION
from tests.scripts.mysql_migration_harness import temporary_schema


ROOT = Path(__file__).resolve().parents[2]


def iter_tests(suite):
    for test in suite:
        if isinstance(test, unittest.TestSuite):
            yield from iter_tests(test)
        else:
            yield test


class ItineraryCiContractTests(unittest.TestCase):
    def test_data_document_names_every_normalized_source_table(self):
        document = (ROOT / "docs/data/itinerary.md").read_text(encoding="utf-8")
        documented_tables = {
            match.group(1)
            for match in re.finditer(r"^\| `([a-z_]+)` \|", document, re.MULTILINE)
        }
        self.assertEqual(
            {
                "itinerary",
                "itinerary_destination",
                "itinerary_day",
                "itinerary_item",
                "itinerary_command",
                "itinerary_item_place",
            },
            documented_tables,
        )

        migration_links = re.findall(
            r"\]\(\.\./\.\./(sql/migrations/[0-9a-z_]+\.sql)\)", document
        )
        self.assertEqual(
            {
                "sql/migrations/20260901_itinerary_core.sql",
                "sql/migrations/20260905_itinerary_daily_map.sql",
            },
            set(migration_links),
        )
        for link in migration_links:
            with self.subTest(link=link):
                self.assertTrue((ROOT / link).is_file())

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

    def test_python_discovery_includes_daily_map_migration_contract(self):
        suite = unittest.defaultTestLoader.discover(
            str(ROOT / "tests"), top_level_dir=str(ROOT)
        )
        discovered = {test.id() for test in iter_tests(suite)}

        self.assertIn(
            "tests.scripts.test_itinerary_daily_map_migration."
            "ItineraryDailyMapMigrationTests.test_apply_converges_and_is_repeatable",
            discovered,
        )

    def test_empty_database_baseline_excludes_provider_raw_columns(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            mysql.execute(
                (ROOT / "sql" / "travel_share.sql").read_text(encoding="utf-8"),
                database=schema,
            )

            raw_column_count = mysql.scalar(
                schema,
                "SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema = DATABASE() "
                "AND table_name = 'itinerary_item_place' "
                "AND column_name IN ('provider_json', 'amap_json', 'lng', 'lat', 'route_json');",
            )

            self.assertEqual("0", raw_column_count)


if __name__ == "__main__":
    unittest.main()
