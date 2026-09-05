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

    def table_definition(self, mysql, schema):
        return mysql.execute(
            "SELECT 'COLUMN', column_name, column_type, is_nullable, "
            "COALESCE(character_set_name, ''), COALESCE(collation_name, '') "
            "FROM information_schema.columns "
            "WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' "
            "UNION ALL "
            "SELECT 'INDEX', index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index), "
            "CAST(non_unique AS CHAR), '', '' FROM information_schema.statistics "
            "WHERE table_schema = DATABASE() AND table_name = 'itinerary_item_place' "
            "GROUP BY index_name, non_unique "
            "UNION ALL "
            "SELECT 'FK', rc.constraint_name, "
            "GROUP_CONCAT(kcu.column_name ORDER BY kcu.ordinal_position), "
            "CONCAT(kcu.referenced_table_name, '(', "
            "GROUP_CONCAT(kcu.referenced_column_name ORDER BY kcu.ordinal_position), ')'), "
            "rc.update_rule, rc.delete_rule "
            "FROM information_schema.referential_constraints rc "
            "JOIN information_schema.key_column_usage kcu "
            "ON kcu.constraint_schema = rc.constraint_schema "
            "AND kcu.table_name = rc.table_name "
            "AND kcu.constraint_name = rc.constraint_name "
            "WHERE rc.constraint_schema = DATABASE() "
            "AND rc.table_name = 'itinerary_item_place' "
            "GROUP BY rc.constraint_name, kcu.referenced_table_name, "
            "rc.update_rule, rc.delete_rule "
            "UNION ALL "
            "SELECT 'CHECK', constraint_name, check_clause, '', '', '' "
            "FROM information_schema.check_constraints "
            "WHERE constraint_schema = DATABASE() "
            "AND constraint_name LIKE 'chk_itinerary_item_place_%' "
            "ORDER BY 1, 2;",
            database=schema,
        )

    def routine_count(self, mysql, schema):
        return mysql.scalar(
            schema,
            "SELECT COUNT(*) FROM information_schema.routines "
            "WHERE routine_schema = DATABASE() "
            "AND routine_name LIKE '%itinerary_daily_map_migration%';",
        )

    def normalized_check_definitions(self, mysql, schema):
        rows = mysql.execute(
            "SELECT constraint_name, "
            "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
            "check_clause, '`', ''), ' ', ''), '_utf8mb4', ''), "
            "'_ascii', ''), CHAR(92), '') "
            "FROM information_schema.check_constraints "
            "WHERE constraint_schema = DATABASE() "
            "AND constraint_name LIKE 'chk_itinerary_item_place_%' "
            "ORDER BY constraint_name;",
            database=schema,
        )
        return dict(line.split("\t", 1) for line in rows.splitlines())

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

            definition = self.table_definition(mysql, schema)
            for expected in (
                "COLUMN\titem_id\tbigint\tNO\t\t",
                "COLUMN\titinerary_id\tbigint\tNO\t\t",
                "COLUMN\tsource\tvarchar(16)\tNO\tascii\tascii_bin",
                "COLUMN\tsource_place_id\tvarchar(128)\tYES\tutf8mb4\tutf8mb4_unicode_ci",
                "COLUMN\tname\tvarchar(200)\tNO\tutf8mb4\tutf8mb4_unicode_ci",
                "COLUMN\taddress\tvarchar(500)\tYES\tutf8mb4\tutf8mb4_unicode_ci",
                "COLUMN\tadministrative_area_code\tchar(6)\tYES\tascii\tascii_bin",
                "COLUMN\tlongitude\tdecimal(10,7)\tNO\t\t",
                "COLUMN\tlatitude\tdecimal(10,7)\tNO\t\t",
                "COLUMN\tcoordinate_system\tvarchar(16)\tNO\tascii\tascii_bin",
                "COLUMN\tconfirmed_at\tdatetime(3)\tNO\t\t",
                "COLUMN\tupdated_at\tdatetime(3)\tNO\t\t",
                "INDEX\tPRIMARY\titem_id\t0",
                "INDEX\tidx_itinerary_item_place_itinerary\titinerary_id,item_id\t1\t\t",
                "FK\tfk_itinerary_item_place_item\titem_id\titinerary_item(id)\tRESTRICT\tRESTRICT",
                "FK\tfk_itinerary_item_place_itinerary\titinerary_id\titinerary(id)\tRESTRICT\tCASCADE",
            ):
                with self.subTest(expected=expected):
                    self.assertIn(expected, definition)
            self.assertEqual(
                {
                    "chk_itinerary_item_place_area_code": "((administrative_area_codeisnull)orregexp_like(administrative_area_code,'^[0-9]{6}$'))",
                    "chk_itinerary_item_place_coordinate_system": "(coordinate_system='GCJ02')",
                    "chk_itinerary_item_place_latitude": "(latitudebetween-(90)and90)",
                    "chk_itinerary_item_place_longitude": "(longitudebetween-(180)and180)",
                    "chk_itinerary_item_place_source": "(sourcein('AMAP','MANUAL'))",
                    "chk_itinerary_item_place_source_id": "(((source='AMAP')and(source_place_idisnotnull))or((source='MANUAL')and(source_place_idisnull)))",
                },
                self.normalized_check_definitions(mysql, schema),
            )

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

    def test_same_name_wrong_objects_are_not_reported_as_healthy(self):
        scenarios = (
            (
                "column",
                "ALTER TABLE itinerary_item_place MODIFY source "
                "VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;",
                "column source.*varchar\\(16\\)",
                "missing_column_count",
            ),
            (
                "index",
                "ALTER TABLE itinerary_item_place ADD INDEX idx_place_fk_support (itinerary_id); "
                "ALTER TABLE itinerary_item_place "
                "DROP INDEX idx_itinerary_item_place_itinerary; "
                "ALTER TABLE itinerary_item_place "
                "ADD INDEX idx_itinerary_item_place_itinerary (item_id, itinerary_id);",
                "idx_itinerary_item_place_itinerary.*itinerary_id,item_id",
                "missing_index_count",
            ),
            (
                "foreign key",
                "ALTER TABLE itinerary_item_place "
                "DROP FOREIGN KEY fk_itinerary_item_place_item; "
                "ALTER TABLE itinerary_item_place "
                "ADD CONSTRAINT fk_itinerary_item_place_item FOREIGN KEY (item_id) "
                "REFERENCES itinerary_item(id) ON DELETE CASCADE ON UPDATE CASCADE;",
                "fk_itinerary_item_place_item.*RESTRICT/RESTRICT",
                "missing_foreign_key_count",
            ),
            (
                "check",
                "ALTER TABLE itinerary_item_place "
                "DROP CHECK chk_itinerary_item_place_source, "
                "ADD CONSTRAINT chk_itinerary_item_place_source CHECK (source = 'WRONG');",
                "chk_itinerary_item_place_source.*AMAP.*MANUAL",
                "missing_check_count",
            ),
            (
                "check literal case",
                "ALTER TABLE itinerary_item_place "
                "DROP CHECK chk_itinerary_item_place_source, "
                "ADD CONSTRAINT chk_itinerary_item_place_source "
                "CHECK (source IN ('amap', 'manual'));",
                "chk_itinerary_item_place_source.*AMAP.*MANUAL",
                "missing_check_count",
            ),
            (
                "check grouping",
                "ALTER TABLE itinerary_item_place "
                "DROP CHECK chk_itinerary_item_place_source_id; "
                "ALTER TABLE itinerary_item_place ADD CONSTRAINT "
                "chk_itinerary_item_place_source_id CHECK ("
                "source = 'AMAP' AND (source_place_id IS NOT NULL OR source = 'MANUAL') "
                "AND source_place_id IS NULL);",
                "chk_itinerary_item_place_source_id.*pairing",
                "missing_check_count",
            ),
        )
        for name, corrupt_sql, expected_error, drift_metric in scenarios:
            with self.subTest(name=name), temporary_schema(
                ITINERARY_DAILY_MAP_MIGRATION
            ) as (mysql, schema):
                self.install_core(mysql, schema)
                run_sql_script(
                    mysql,
                    schema,
                    MIGRATION_PATH,
                    ITINERARY_DAILY_MAP_MIGRATION,
                    apply=True,
                )
                mysql.execute(corrupt_sql, database=schema)
                before = self.table_definition(mysql, schema)

                dry_run = run_sql_script(
                    mysql,
                    schema,
                    MIGRATION_PATH,
                    ITINERARY_DAILY_MAP_MIGRATION,
                    apply=False,
                )
                self.assertIn(f"{drift_metric}\t1", dry_run)
                self.assertEqual(before, self.table_definition(mysql, schema))

                with self.assertRaisesRegex(RuntimeError, expected_error):
                    run_sql_script(
                        mysql,
                        schema,
                        MIGRATION_PATH,
                        ITINERARY_DAILY_MAP_MIGRATION,
                        apply=True,
                    )

                self.assertEqual(before, self.table_definition(mysql, schema))
                self.assertEqual("0", self.routine_count(mysql, schema))

    def test_nonempty_incomplete_table_fails_without_schema_side_effects(self):
        with temporary_schema(ITINERARY_DAILY_MAP_MIGRATION) as (mysql, schema):
            self.install_core(mysql, schema)
            mysql.execute(
                "CREATE TABLE itinerary_item_place ("
                "item_id BIGINT NOT NULL, PRIMARY KEY (item_id)"
                ") ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci; "
                "INSERT INTO itinerary_item_place (item_id) VALUES (1);",
                database=schema,
            )
            before = self.table_definition(mysql, schema)

            with self.assertRaisesRegex(
                RuntimeError,
                "non-empty itinerary_item_place.*itinerary_id",
            ):
                run_sql_script(
                    mysql,
                    schema,
                    MIGRATION_PATH,
                    ITINERARY_DAILY_MAP_MIGRATION,
                    apply=True,
                )

            self.assertEqual(before, self.table_definition(mysql, schema))
            self.assertEqual(
                "1", mysql.scalar(schema, "SELECT COUNT(*) FROM itinerary_item_place;")
            )
            self.assertEqual("0", self.routine_count(mysql, schema))

if __name__ == "__main__":
    unittest.main()
