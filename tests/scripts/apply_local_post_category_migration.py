import argparse
import re
from pathlib import Path

from tests.scripts.mysql_migration_harness import (
    MySqlMigrationHarness,
    load_local_mysql_config,
    run_sql_script,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]
MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260829_post_categories.sql"
LOCATION_MIGRATION_PATH = PROJECT_ROOT / "sql" / "migrations" / "20260829_post_location_compatibility.sql"


def main() -> None:
    parser = argparse.ArgumentParser(description="对项目当前本地 MySQL 执行帖子结构与分类迁移")
    parser.add_argument("--apply", action="store_true", help="执行历史分类回填；省略时仅 dry-run")
    args = parser.parse_args()

    config = load_local_mysql_config()
    database = str(config["database"])
    if not re.fullmatch(r"[A-Za-z0-9_]+", database):
        raise RuntimeError("本地数据库名包含不安全字符")

    with MySqlMigrationHarness(config) as mysql:
        location_report = run_sql_script(mysql, database, LOCATION_MIGRATION_PATH, apply=True)
        report = run_sql_script(mysql, database, MIGRATION_PATH, apply=args.apply)
        mode = "APPLY" if args.apply else "DRY_RUN"
        print(f"mode\t{mode}")
        print(f"database\t{database}")
        print("migration\tpost_location_compatibility")
        print(location_report)
        print("migration\tpost_categories")
        print(report)


if __name__ == "__main__":
    main()
