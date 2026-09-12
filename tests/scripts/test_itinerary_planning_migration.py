import unittest
from pathlib import Path

from tests.scripts.migration_specs import (
    ITINERARY_CORE_MIGRATION,
    ITINERARY_PLANNING_MIGRATION,
)
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema


PROJECT_ROOT = Path(__file__).resolve().parents[2]
CORE_MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260901_itinerary_core.sql"
MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260902_itinerary_planning.sql"
BASELINE_PATH = PROJECT_ROOT / "sql" / "travel_share.sql"
MIGRATION = ITINERARY_PLANNING_MIGRATION

TABLES = {
    "itinerary_planning_request",
    "itinerary_planning_destination",
    "itinerary_revision_proposal",
    "itinerary_revision_operation",
    "itinerary_revision_resolution",
}

MEMBER_SCHEMA = """
CREATE TABLE member (
    id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
"""


def install_itinerary_core(mysql, schema):
    mysql.execute(MEMBER_SCHEMA, database=schema)
    run_sql_script(
        mysql,
        schema,
        CORE_MIGRATION_PATH,
        ITINERARY_CORE_MIGRATION,
        apply=True,
    )


def table_names(mysql, schema):
    output = mysql.execute(
        "SELECT table_name FROM information_schema.tables "
        "WHERE table_schema = DATABASE() AND "
        "(table_name LIKE 'itinerary_planning_%' OR table_name LIKE 'itinerary_revision_%') "
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
    checks = mysql.execute(
        "SELECT tc.constraint_name, cc.check_clause "
        "FROM information_schema.table_constraints tc "
        "JOIN information_schema.check_constraints cc "
        "ON cc.constraint_schema = tc.constraint_schema "
        "AND cc.constraint_name = tc.constraint_name "
        f"WHERE tc.table_schema = DATABASE() AND tc.table_name = '{table}' "
        "AND tc.constraint_type = 'CHECK' ORDER BY tc.constraint_name;",
        database=schema,
    )
    return columns, indexes, foreign_keys, checks


class ItineraryPlanningMigrationTests(unittest.TestCase):
    def test_dry_run_reports_missing_tables_without_changing_schema(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            install_itinerary_core(mysql, schema)

            report = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=False)

            self.assertIn("missing_table_count\t5", report)
            self.assertIn("extra_table_count\t0", report)
            self.assertEqual(set(), table_names(mysql, schema))

    def test_apply_is_idempotent_and_creates_the_formal_contract(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            install_itinerary_core(mysql, schema)

            first = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            second = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual(TABLES, table_names(mysql, schema))
            for report in (first, second):
                self.assertIn("missing_table_count\t0", report)
                self.assertIn("missing_column_count\t0", report)
                self.assertIn("missing_index_count\t0", report)
                self.assertIn("missing_foreign_key_count\t0", report)
                self.assertIn("legacy_column_count\t0", report)
            self.assertEqual(
                "json",
                mysql.scalar(
                    schema,
                    "SELECT data_type FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() "
                    "AND table_name = 'itinerary_planning_request' "
                    "AND column_name = 'preferences_json';",
                ),
            )
            self.assertEqual(
                "0",
                mysql.scalar(
                    schema,
                    "SELECT non_unique FROM information_schema.statistics "
                    "WHERE table_schema = DATABASE() "
                    "AND table_name = 'itinerary_revision_resolution' "
                    "AND index_name = 'uk_itinerary_revision_decision_id';",
                ),
            )
            self.assertEqual(
                "1",
                mysql.scalar(
                    schema,
                    "SELECT column_default FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() "
                    "AND table_name = 'itinerary_planning_request' AND column_name = 'version';",
                ),
            )

    def test_apply_recovers_when_an_earlier_run_stopped_between_tables(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            install_itinerary_core(mysql, schema)
            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            mysql.execute(
                "SET FOREIGN_KEY_CHECKS = 0; "
                "DROP TABLE itinerary_revision_resolution, itinerary_revision_operation, "
                "itinerary_revision_proposal; "
                "SET FOREIGN_KEY_CHECKS = 1;",
                database=schema,
            )

            report = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual(TABLES, table_names(mysql, schema))
            self.assertIn("missing_table_count\t0", report)

    def test_empty_database_baseline_matches_migrated_table_contract(self):
        with temporary_schema(MIGRATION) as (mysql, migrated_schema):
            install_itinerary_core(mysql, migrated_schema)
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
