from tests.scripts.mysql_migration_harness import MigrationSpec


POST_CATEGORIES_MIGRATION = MigrationSpec(
    name="post_categories",
    apply_variable="apply_post_category_migration",
    schema_prefix="lyw_post_category_migration_test",
)

POST_LOCATION_COMPATIBILITY_MIGRATION = MigrationSpec(
    name="post_location_compatibility",
    apply_variable="apply_post_location_compatibility_migration",
    schema_prefix="lyw_post_location_migration_test",
)
