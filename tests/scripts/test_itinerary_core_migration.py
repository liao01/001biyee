import unittest
from pathlib import Path

from tests.scripts.migration_specs import ITINERARY_CORE_MIGRATION
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema


PROJECT_ROOT = Path(__file__).resolve().parents[2]
MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260901_itinerary_core.sql"
BASELINE_PATH = PROJECT_ROOT / "sql" / "travel_share.sql"
MIGRATION = ITINERARY_CORE_MIGRATION

TABLES = {
    "itinerary",
    "itinerary_destination",
    "itinerary_day",
    "itinerary_item",
    "itinerary_command",
}

MEMBER_SCHEMA = """
CREATE TABLE member (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
"""


def table_names(mysql, schema):
    output = mysql.execute(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema = DATABASE() AND table_name LIKE 'itinerary%' "
        "ORDER BY table_name;",
        database=schema,
    )
    return set(output.splitlines()) if output else set()


def table_signature(mysql, schema, table):
    columns = mysql.execute(
        "SELECT column_name, column_type, is_nullable, COALESCE(column_default, '<NULL>'), extra "
        "FROM information_schema.columns "
        f"WHERE table_schema = DATABASE() AND table_name = '{table}' "
        "ORDER BY ordinal_position;",
        database=schema,
    )
    indexes = mysql.execute(
        "SELECT index_name, non_unique, seq_in_index, column_name "
        "FROM information_schema.statistics "
        f"WHERE table_schema = DATABASE() AND table_name = '{table}' "
        "ORDER BY index_name, seq_in_index;",
        database=schema,
    )
    foreign_keys = mysql.execute(
        "SELECT constraint_name, column_name, referenced_table_name, referenced_column_name "
        "FROM information_schema.key_column_usage "
        f"WHERE table_schema = DATABASE() AND table_name = '{table}' "
        "AND referenced_table_name IS NOT NULL "
        "ORDER BY constraint_name, ordinal_position;",
        database=schema,
    )
    return columns, indexes, foreign_keys


class ItineraryCoreMigrationTests(unittest.TestCase):
    def test_dry_run_reports_missing_tables_without_changing_schema(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(MEMBER_SCHEMA, database=schema)

            report = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=False)

            self.assertIn("missing_table_count\t5", report)
            self.assertIn("extra_table_count\t0", report)
            self.assertEqual(set(), table_names(mysql, schema))

    def test_apply_is_idempotent_and_creates_the_formal_contract(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(MEMBER_SCHEMA, database=schema)

            first = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            second = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual(TABLES, table_names(mysql, schema))
            self.assertIn("missing_table_count\t0", first)
            self.assertIn("missing_table_count\t0", second)
            self.assertIn("missing_column_count\t0", second)
            self.assertIn("missing_index_count\t0", second)
            self.assertIn("missing_foreign_key_count\t0", second)
            self.assertEqual(
                "1",
                mysql.scalar(
                    schema,
                    "SELECT column_default FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() AND table_name = 'itinerary' "
                    "AND column_name = 'version';",
                ),
            )
            self.assertEqual(
                "0",
                mysql.scalar(
                    schema,
                    "SELECT non_unique FROM information_schema.statistics "
                    "WHERE table_schema = DATABASE() AND table_name = 'itinerary_command' "
                    "AND index_name = 'uk_itinerary_command_id';",
                ),
            )

    def test_apply_recovers_when_an_earlier_run_stopped_between_tables(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(MEMBER_SCHEMA, database=schema)
            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            mysql.execute(
                "SET FOREIGN_KEY_CHECKS = 0; "
                "DROP TABLE itinerary_command, itinerary_item, itinerary_day; "
                "SET FOREIGN_KEY_CHECKS = 1;",
                database=schema,
            )

            report = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual(TABLES, table_names(mysql, schema))
            self.assertIn("missing_table_count\t0", report)

    def test_empty_database_baseline_matches_migrated_table_contract(self):
        with temporary_schema(MIGRATION) as (mysql, migrated_schema):
            mysql.execute(MEMBER_SCHEMA, database=migrated_schema)
            run_sql_script(mysql, migrated_schema, MIGRATION_PATH, MIGRATION, apply=True)
            migrated = {
                table: table_signature(mysql, migrated_schema, table) for table in TABLES
            }

        with temporary_schema(MIGRATION) as (mysql, baseline_schema):
            mysql.execute(BASELINE_PATH.read_text(encoding="utf-8"), database=baseline_schema)
            baseline = {
                table: table_signature(mysql, baseline_schema, table) for table in TABLES
            }

        self.assertEqual(migrated, baseline)


if __name__ == "__main__":
    unittest.main()
