import unittest

from scripts.security.sanitize_sql import SqlSanitizationError, sanitize_sql


class SanitizeSqlTests(unittest.TestCase):
    def test_preserves_schema_and_removes_insert_rows(self):
        phone_number = "188" + "00000000"
        source = f"""
        SET NAMES utf8mb4;
        DROP TABLE IF EXISTS `member`;
        CREATE TABLE `member` (
          `id` bigint NOT NULL,
          `mobile` varchar(20)
        );
        INSERT INTO `member` VALUES (1, '{phone_number}');
        SET FOREIGN_KEY_CHECKS = 1;
        """

        result = sanitize_sql(source)

        self.assertIn("CREATE TABLE `member`", result)
        self.assertIn("DROP TABLE IF EXISTS `member`", result)
        self.assertNotIn("INSERT INTO", result)
        self.assertNotIn(phone_number, result)

    def test_removes_export_environment_metadata_comments(self):
        source = """
        /*
         Source Host: localhost:3306
         Source Schema: travel_share
        */
        CREATE TABLE `tag` (`id` bigint);
        """

        result = sanitize_sql(source)

        self.assertNotIn("Source Host", result)
        self.assertNotIn("Source Schema", result)
        self.assertIn("CREATE TABLE `tag`", result)

    def test_rejects_unclassified_statement(self):
        with self.assertRaises(SqlSanitizationError):
            sanitize_sql("CALL export_private_member_data();")

    def test_handles_semicolon_inside_quoted_schema_literal(self):
        source = """
        CREATE TABLE `message` (
          `content` varchar(20) DEFAULT 'safe;default'
        );
        """

        result = sanitize_sql(source)

        self.assertIn("'safe;default'", result)


if __name__ == "__main__":
    unittest.main()
