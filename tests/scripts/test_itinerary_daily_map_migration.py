import unittest
from pathlib import Path

from tests.scripts.migration_specs import ITINERARY_CORE_MIGRATION
from tests.scripts.migration_specs import ITINERARY_DAILY_MAP_MIGRATION
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema


ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "sql" / "migrations" / "20260901_itinerary_core.sql"
MIGRATION_PATH = ROOT / "sql" / "migrations" / "20260905_itinerary_daily_map.sql"


class ItineraryDailyMapMigrationTests(unittest.TestCase):
    def install_core(self, mysql, schema):
        mysql.execute(
            "CREATE TABLE member (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB;",
            database=schema,
        )
        run_sql_script(mysql, schema, CORE, ITINERARY_CORE_MIGRATION, apply=True)

    def test_dry_run_does_not_create_table(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            self.install_core(mysql, schema)

            report = run_sql_script(
                mysql, schema, MIGRATION_PATH, ITINERARY_DAILY_MAP_MIGRATION, apply=False
            )

            self.assertIn("missing_table_count\t1", report)
            self.assertIsNone(
                mysql.scalar(
                    schema,
                    "SELECT table_name FROM information_schema.tables "
                    "WHERE table_schema = DATABASE() "
                    "AND table_name = 'itinerary_item_place';",
                )
            )

    def test_apply_converges_and_is_repeatable(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            self.install_core(mysql, schema)

            first = run_sql_script(
                mysql, schema, MIGRATION_PATH, ITINERARY_DAILY_MAP_MIGRATION, apply=True
            )
            second = run_sql_script(
                mysql, schema, MIGRATION_PATH, ITINERARY_DAILY_MAP_MIGRATION, apply=True
            )

            for report in (first, second):
                self.assertIn("missing_table_count\t0", report)
                self.assertIn("missing_column_count\t0", report)
                self.assertIn("missing_index_count\t0", report)
                self.assertIn("missing_foreign_key_count\t0", report)
                self.assertIn("legacy_column_count\t0", report)

    def test_apply_recovers_an_incomplete_existing_table(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            self.install_core(mysql, schema)
            mysql.execute(
                "CREATE TABLE itinerary_item_place ("
                "item_id BIGINT NOT NULL, PRIMARY KEY (item_id)"
                ") ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;",
                database=schema,
            )

            report = run_sql_script(
                mysql, schema, MIGRATION_PATH, ITINERARY_DAILY_MAP_MIGRATION, apply=True
            )

            for metric in (
                "missing_table_count",
                "missing_column_count",
                "missing_index_count",
                "missing_foreign_key_count",
                "legacy_column_count",
            ):
                self.assertIn(f"{metric}\t0", report)
            self.assertEqual(
                "6",
                mysql.scalar(
                    schema,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                    "WHERE constraint_schema = DATABASE() "
                    "AND table_name = 'itinerary_item_place' "
                    "AND constraint_type = 'CHECK';",
                ),
            )

if __name__ == "__main__":
    unittest.main()
