import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tests.scripts.migration_specs import (
    POST_CATEGORIES_MIGRATION,
    POST_LOCATION_COMPATIBILITY_MIGRATION,
)
from tests.scripts.mysql_migration_harness import MigrationSpec, load_local_mysql_config


class MigrationSpecTests(unittest.TestCase):
    def test_each_migration_declares_safe_independent_identifiers(self):
        self.assertNotEqual(
            POST_CATEGORIES_MIGRATION.apply_variable,
            POST_LOCATION_COMPATIBILITY_MIGRATION.apply_variable,
        )
        self.assertNotEqual(
            POST_CATEGORIES_MIGRATION.schema_prefix,
            POST_LOCATION_COMPATIBILITY_MIGRATION.schema_prefix,
        )

    def test_unsafe_mysql_identifiers_are_rejected_before_execution(self):
        invalid_specs = (
            {"name": "post categories", "apply_variable": "apply_post_categories", "schema_prefix": "lyw_post_test"},
            {"name": "post_categories", "apply_variable": "apply;drop", "schema_prefix": "lyw_post_test"},
            {"name": "post_categories", "apply_variable": "apply_post_categories", "schema_prefix": "production"},
        )

        for values in invalid_specs:
            with self.subTest(values=values):
                with self.assertRaises(ValueError):
                    MigrationSpec(**values)

    def test_missing_mysql_configuration_reports_how_to_enable_integration_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            missing_properties = Path(directory) / "application.properties"
            with patch.dict(os.environ, {}, clear=True):
                with self.assertRaisesRegex(RuntimeError, "LYW_MIGRATION_TEST_DSN"):
                    load_local_mysql_config(missing_properties)

    def test_ci_can_supply_a_loopback_mysql_dsn_without_a_local_properties_file(self):
        with tempfile.TemporaryDirectory() as directory:
            missing_properties = Path(directory) / "application.properties"
            with patch.dict(
                os.environ,
                {"LYW_MIGRATION_TEST_DSN": "mysql://migration:change-me@127.0.0.1:3307/lyw"},
                clear=True,
            ):
                config = load_local_mysql_config(missing_properties)

        self.assertEqual("127.0.0.1", config["host"])
        self.assertEqual(3307, config["port"])
        self.assertEqual("migration", config["user"])
        self.assertEqual("change-me", config["password"])
        self.assertEqual("lyw", config["database"])


if __name__ == "__main__":
    unittest.main()
