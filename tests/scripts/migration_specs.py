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

MEMBER_EMAIL_IDENTITY_MIGRATION = MigrationSpec(
    name="member_email_identity",
    apply_variable="apply_member_email_identity_migration",
    schema_prefix="lyw_member_identity_migration_test",
)

ITINERARY_CORE_MIGRATION = MigrationSpec(
    name="itinerary_core",
    apply_variable="apply_itinerary_core_migration",
    schema_prefix="lyw_itinerary_core_migration_test",
)

ITINERARY_PLANNING_MIGRATION = MigrationSpec(
    name="itinerary_planning",
    apply_variable="apply_itinerary_planning_migration",
    schema_prefix="lyw_itinerary_planning_migration_test",
)
