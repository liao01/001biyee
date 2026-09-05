import unittest
from pathlib import Path

from tests.scripts.migration_specs import MEMBER_EMAIL_IDENTITY_MIGRATION
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema


PROJECT_ROOT = Path(__file__).resolve().parents[2]
MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260829_member_email_identity.sql"
BASE_SCHEMA_PATH = PROJECT_ROOT / "sql" / "travel_share.sql"
MIGRATION = MEMBER_EMAIL_IDENTITY_MIGRATION


LEGACY_SCHEMA = """
CREATE TABLE member (
    id BIGINT NOT NULL PRIMARY KEY,
    mobile VARCHAR(50) NOT NULL,
    password CHAR(32) NOT NULL,
    name VARCHAR(50),
    created_at DATETIME(3),
    updated_at DATETIME(3),
    UNIQUE KEY mobile_unique (mobile)
);
INSERT INTO member (id, mobile, password, name, created_at) VALUES
    (1, 'Alice@Example.com', REPEAT('1', 32), 'TEST Alice', '2025-01-02 03:04:05.000'),
    (2, CONCAT('138', '00000000'), REPEAT('2', 32), 'TEST legacy phone', '2025-02-03 04:05:06.000');
"""


class MemberEmailIdentityMigrationTests(unittest.TestCase):
    def test_dry_run_reports_counts_without_changing_schema(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)

            output = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=False)

            self.assertIn("eligible_email_rows\t1", output)
            self.assertIn("manual_email_binding_rows\t1", output)
            self.assertIsNone(mysql.scalar(schema, "SHOW COLUMNS FROM member LIKE 'email';"))
            self.assertIsNone(
                mysql.scalar(
                    schema,
                    "SELECT table_name FROM information_schema.tables "
                    "WHERE table_schema = DATABASE() AND table_name = 'identity_one_time_token';",
                )
            )

    def test_apply_backfills_only_email_accounts_and_is_repeatable(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)

            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            first_shape = mysql.execute(
                "SELECT table_name FROM information_schema.tables "
                "WHERE table_schema = DATABASE() AND table_name LIKE 'identity_%' "
                "ORDER BY table_name;"
                "SELECT column_name FROM information_schema.columns "
                "WHERE table_schema = DATABASE() AND table_name = 'member' "
                "ORDER BY ordinal_position;",
                database=schema,
            )

            mysql.execute(
                "UPDATE member SET password_hash = '$2a$12$alreadyUpgradedHash', "
                "password_algorithm = 'BCRYPT' WHERE id = 1;",
                database=schema,
            )
            second_output = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            second_shape = mysql.execute(
                "SELECT table_name FROM information_schema.tables "
                "WHERE table_schema = DATABASE() AND table_name LIKE 'identity_%' "
                "ORDER BY table_name;"
                "SELECT column_name FROM information_schema.columns "
                "WHERE table_schema = DATABASE() AND table_name = 'member' "
                "ORDER BY ordinal_position;",
                database=schema,
            )

            self.assertEqual(first_shape, second_shape)
            self.assertIn("eligible_email_rows\t0", second_output)
            self.assertIn("manual_email_binding_rows\t1", second_output)
            self.assertEqual(
                "alice@example.com",
                mysql.scalar(schema, "SELECT email FROM member WHERE id = 1;"),
            )
            self.assertIsNone(mysql.scalar(schema, "SELECT email FROM member WHERE id = 2;"))
            self.assertEqual(
                "BCRYPT",
                mysql.scalar(schema, "SELECT password_algorithm FROM member WHERE id = 1;"),
            )
            self.assertEqual(
                "$2a$12$alreadyUpgradedHash",
                mysql.scalar(schema, "SELECT password_hash FROM member WHERE id = 1;"),
            )
            self.assertEqual(
                "ACTIVE",
                mysql.scalar(schema, "SELECT account_status FROM member WHERE id = 1;"),
            )
            self.assertEqual(
                "EMAIL_BINDING_REQUIRED",
                mysql.scalar(schema, "SELECT account_status FROM member WHERE id = 2;"),
            )
            self.assertEqual(
                "YES",
                mysql.scalar(
                    schema,
                    "SELECT is_nullable FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() AND table_name = 'member' "
                    "AND column_name = 'mobile';",
                ),
            )
            self.assertEqual(
                "2",
                mysql.scalar(
                    schema,
                    "SELECT COUNT(*) FROM information_schema.tables "
                    "WHERE table_schema = DATABASE() AND table_name LIKE 'identity_%';",
                ),
            )

    def test_normalized_email_conflicts_require_binding_without_choosing_an_owner(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)
            mysql.execute(
                "INSERT INTO member (id, mobile, password, name) "
                "VALUES (3, ' alice@example.com', REPEAT('3', 32), 'TEST conflicting email');",
                database=schema,
            )

            report = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=False)
            self.assertIn("eligible_email_rows\t0", report)
            self.assertIn("manual_email_binding_rows\t3", report)
            self.assertIn("conflicting_email_rows\t2", report)
            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual("3", mysql.scalar(schema, "SELECT COUNT(*) FROM member WHERE email IS NULL;"))
            self.assertEqual(
                "3",
                mysql.scalar(schema, "SELECT COUNT(*) FROM member WHERE account_status = 'EMAIL_BINDING_REQUIRED';"),
            )

    def test_rerun_migrates_new_legacy_rows_without_reactivating_disabled_accounts(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)
            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            mysql.execute(
                "UPDATE member SET account_status = 'DISABLED' WHERE id = 1;"
                "INSERT INTO member (id, mobile, password, name) "
                "VALUES (3, 'new-legacy@example.com', REPEAT('3', 32), 'TEST new legacy');",
                database=schema,
            )

            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual("DISABLED", mysql.scalar(schema, "SELECT account_status FROM member WHERE id = 1;"))
            self.assertEqual("ACTIVE", mysql.scalar(schema, "SELECT account_status FROM member WHERE id = 3;"))
            self.assertEqual("LEGACY_DOUBLE_MD5", mysql.scalar(schema, "SELECT password_algorithm FROM member WHERE id = 3;"))

    def test_token_tables_store_only_token_hashes(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)
            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            for table in ("identity_one_time_token", "identity_refresh_session"):
                with self.subTest(table=table):
                    columns = set(
                        mysql.execute(
                            f"SELECT column_name FROM information_schema.columns "
                            f"WHERE table_schema = DATABASE() AND table_name = '{table}';",
                            database=schema,
                        ).splitlines()
                    )
                    self.assertIn("token_hash", columns)
                    self.assertNotIn("token", columns)

    def test_base_schema_contains_the_converged_identity_shape(self):
        with temporary_schema(MIGRATION) as (mysql, migrated):
            mysql.execute(LEGACY_SCHEMA, database=migrated)
            run_sql_script(mysql, migrated, MIGRATION_PATH, MIGRATION, apply=True)
            with temporary_schema(MIGRATION) as (fresh_mysql, fresh):
                fresh_mysql.execute(BASE_SCHEMA_PATH.read_text(encoding="utf-8"), database=fresh)
                contract_query = (
                    "SELECT table_name, column_name, column_type, is_nullable, "
                    "COALESCE(column_default, '<NULL>'), COALESCE(collation_name, '<NULL>') "
                    "FROM information_schema.columns WHERE table_schema = DATABASE() "
                    "AND (table_name LIKE 'identity_%' OR (table_name = 'member' AND "
                    "column_name IN ('email', 'email_verified_at', 'password_hash', 'password_algorithm', 'account_status'))) "
                    "ORDER BY table_name, column_name;"
                )
                self.assertEqual(
                    mysql.execute(contract_query, database=migrated),
                    fresh_mysql.execute(contract_query, database=fresh),
                )
                for db, schema in ((mysql, migrated), (fresh_mysql, fresh)):
                    db.execute(
                        "INSERT INTO member (id, email, password_hash, password_algorithm, name) "
                        "VALUES (99, 'new@example.com', 'test-placeholder', 'BCRYPT', 'TEST email only');",
                        database=schema,
                    )
                    run_sql_script(db, schema, MIGRATION_PATH, MIGRATION, apply=True)
                    self.assertEqual("PENDING_VERIFICATION", db.scalar(schema, "SELECT account_status FROM member WHERE id = 99;"))
                    self.assertIsNone(db.scalar(schema, "SELECT mobile FROM member WHERE id = 99;"))

    def test_partial_ddl_can_resume_and_existing_email_owner_is_preserved(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)
            mysql.execute("ALTER TABLE member ADD email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;", database=schema)
            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            mysql.execute(
                "INSERT INTO member (id, mobile, password, name) "
                "VALUES (3, ' alice@example.com', REPEAT('3', 32), 'TEST conflicting legacy');",
                database=schema,
            )

            report = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertIn("conflicting_email_rows\t1", report)
            self.assertEqual("alice@example.com", mysql.scalar(schema, "SELECT email FROM member WHERE id = 1;"))
            self.assertIsNone(mysql.scalar(schema, "SELECT email FROM member WHERE id = 3;"))
            self.assertEqual("EMAIL_BINDING_REQUIRED", mysql.scalar(schema, "SELECT account_status FROM member WHERE id = 3;"))


if __name__ == "__main__":
    unittest.main()
