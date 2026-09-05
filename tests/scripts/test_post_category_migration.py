import unittest
from pathlib import Path

from tests.scripts.migration_specs import POST_CATEGORIES_MIGRATION
from tests.scripts.mysql_migration_harness import run_sql_script, temporary_schema


PROJECT_ROOT = Path(__file__).resolve().parents[2]
MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260829_post_categories.sql"
MIGRATION = POST_CATEGORIES_MIGRATION


LEGACY_SCHEMA = """
CREATE TABLE post (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255),
    content MEDIUMTEXT,
    create_time DATETIME,
    update_time DATETIME,
    status CHAR(1)
);
CREATE TABLE tag (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL
);
CREATE TABLE post_tag (
    id BIGINT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL
);
INSERT INTO post (id, user_id, title, status) VALUES
    (101, 1, '本地美食', '1'),
    (102, 1, '山野景点', '1'),
    (103, 1, '城市攻略', '1'),
    (104, 1, '分类冲突', '1'),
    (105, 1, '无法映射', '1');
INSERT INTO tag (id, name) VALUES
    (1, '美食'), (2, '景点'), (3, '旅行'), (4, '攻略'), (5, '摄影');
INSERT INTO post_tag (id, post_id, tag_id) VALUES
    (1, 101, 1),
    (2, 102, 2),
    (3, 103, 4),
    (4, 104, 1),
    (5, 104, 2),
    (6, 105, 5);
"""


class PostCategoryMigrationTests(unittest.TestCase):
    def test_migration_is_idempotent_and_preserves_ambiguous_history(self):
        with temporary_schema(MIGRATION) as (mysql, schema):
            mysql.execute(LEGACY_SCHEMA, database=schema)

            dry_run = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=False)
            self.assertIn("conflict_count\t1", dry_run)
            self.assertIn("unmapped_count\t1", dry_run)

            run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)
            second_run = run_sql_script(mysql, schema, MIGRATION_PATH, MIGRATION, apply=True)

            codes = set(mysql.execute("SELECT code FROM post_category;", database=schema).splitlines())
            self.assertEqual({"CITY_WALK", "NATURAL_SCENERY", "FOOD"}, codes)
            self.assertEqual("FOOD", mysql.scalar(schema, "SELECT category_code FROM post WHERE id = 101;"))
            self.assertEqual("NATURAL_SCENERY", mysql.scalar(schema, "SELECT category_code FROM post WHERE id = 102;"))
            self.assertEqual("CITY_WALK", mysql.scalar(schema, "SELECT category_code FROM post WHERE id = 103;"))
            self.assertIsNone(mysql.scalar(schema, "SELECT category_code FROM post WHERE id = 104;"))
            self.assertIsNone(mysql.scalar(schema, "SELECT category_code FROM post WHERE id = 105;"))
            self.assertIn("conflict_count\t1", second_run)
            self.assertIn("unmapped_count\t1", second_run)
            self.assertEqual("5", mysql.scalar(schema, "SELECT COUNT(*) FROM post;"))
            self.assertEqual("5", mysql.scalar(schema, "SELECT COUNT(*) FROM tag;"))


if __name__ == "__main__":
    unittest.main()
