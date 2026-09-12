import unittest
from pathlib import Path

from tests.scripts.migration_specs import POST_LOCATION_COMPATIBILITY_MIGRATION
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema


PROJECT_ROOT = Path(__file__).resolve().parents[2]
MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260829_post_location_compatibility.sql"
MIGRATION = POST_LOCATION_COMPATIBILITY_MIGRATION


LEGACY_SCHEMA = """
CREATE TABLE location_record (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
CREATE TABLE post (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255),
    content MEDIUMTEXT,
    create_time DATETIME,
    update_time DATETIME,
    status CHAR(1)
);
INSERT INTO location_record (id, name) VALUES (10, '测试地点');
INSERT INTO post (id, user_id, title, status) VALUES (101, 1, '旧帖子', '1');
"""


class PostLocationCompatibilityMigrationTests(unittest.TestCase):
    def test_migration_is_idempotent_and_preserves_existing_posts(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)

            dry_run = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=False)
            self.assertEqual(
                "0",
                mysql.scalar(
                    schema,
                    "SELECT COUNT(*) FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() AND table_name = 'post' "
                    "AND column_name = 'location_id';",
                ),
            )
            self.assertIn("location_column_count\t0", dry_run)

            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            second_run = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            self.assertEqual(
                "1",
                mysql.scalar(
                    schema,
                    "SELECT COUNT(*) FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() AND table_name = 'post' "
                    "AND column_name = 'location_id';",
                ),
            )
            self.assertEqual(
                "1",
                mysql.scalar(
                    schema,
                    "SELECT COUNT(*) FROM information_schema.statistics "
                    "WHERE table_schema = DATABASE() AND table_name = 'post' "
                    "AND index_name = 'idx_post_location_id';",
                ),
            )
            self.assertEqual(
                "1",
                mysql.scalar(
                    schema,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                    "WHERE constraint_schema = DATABASE() AND table_name = 'post' "
                    "AND constraint_name = 'fk_post_location_record' "
                    "AND constraint_type = 'FOREIGN KEY';",
                ),
            )
            self.assertEqual("1", mysql.scalar(schema, "SELECT COUNT(*) FROM post;"))
            self.assertIsNone(mysql.scalar(schema, "SELECT location_id FROM post WHERE id = 101;"))
            self.assertIn("location_column_count\t1", second_run)


if __name__ == "__main__":
    unittest.main()
