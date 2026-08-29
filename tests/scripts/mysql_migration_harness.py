import contextlib
import os
import re
import secrets
import subprocess
import tempfile
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
LOCAL_PROPERTIES = PROJECT_ROOT / "business" / "src" / "main" / "resources" / "application.properties"


def _resolve_property(value: str) -> str:
    match = re.fullmatch(r"\$\{([^:}]+):([^}]*)}", value.strip())
    if not match:
        return value.strip()
    env_name, fallback = match.groups()
    return os.environ.get(env_name, fallback)


def load_local_mysql_config() -> dict[str, str | int]:
    properties: dict[str, str] = {}
    for raw_line in LOCAL_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = _resolve_property(value)

    jdbc_url = properties["spring.datasource.url"]
    match = re.match(r"jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)", jdbc_url)
    if not match:
        raise RuntimeError("Local datasource is not a MySQL JDBC URL")
    host, port, database = match.groups()
    if host not in {"localhost", "127.0.0.1"}:
        raise RuntimeError("Migration tests only allow loopback MySQL")
    return {
        "host": host,
        "port": int(port or 3306),
        "user": properties["spring.datasource.username"],
        "password": properties["spring.datasource.password"],
        "database": database,
    }


class MySqlMigrationHarness:
    def __init__(self, config: dict[str, str | int]):
        self.config = config
        self._defaults_file: Path | None = None

    def __enter__(self):
        handle = tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".cnf", delete=False)
        handle.write("[client]\n")
        handle.write(f"host={self.config['host']}\n")
        handle.write(f"port={self.config['port']}\n")
        handle.write(f"user={self.config['user']}\n")
        handle.write(f"password={self.config['password']}\n")
        handle.close()
        self._defaults_file = Path(handle.name)
        return self

    def __exit__(self, exc_type, exc, tb):
        if self._defaults_file and self._defaults_file.exists():
            self._defaults_file.unlink()

    def execute(self, sql: str, database: str | None = None, include_headers: bool = False) -> str:
        if self._defaults_file is None:
            raise RuntimeError("Harness must be used as a context manager")
        command = [
            "mysql",
            f"--defaults-extra-file={self._defaults_file}",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
        ]
        if not include_headers:
            command.append("--skip-column-names")
        if database:
            command.append(database)
        completed = subprocess.run(
            command,
            input=sql,
            text=True,
            encoding="utf-8",
            capture_output=True,
            check=False,
        )
        if completed.returncode != 0:
            raise RuntimeError(completed.stderr.strip() or "mysql command failed")
        return completed.stdout.strip()

    def scalar(self, database: str, sql: str) -> str | None:
        output = self.execute(sql, database=database)
        if not output:
            return None
        value = output.splitlines()[0]
        return None if value == "NULL" else value


@contextlib.contextmanager
def temporary_schema():
    schema = f"lyw_post_category_migration_test_{secrets.token_hex(5)}"
    if not re.fullmatch(r"lyw_post_category_migration_test_[0-9a-f]{10}", schema):
        raise RuntimeError("Unsafe temporary schema name")
    with MySqlMigrationHarness(load_local_mysql_config()) as harness:
        harness.execute(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        try:
            yield harness, schema
        finally:
            harness.execute(f"DROP DATABASE IF EXISTS `{schema}`;")


def run_sql_script(harness: MySqlMigrationHarness, schema: str, path: Path, apply: bool) -> str:
    sql = path.read_text(encoding="utf-8")
    apply_value = 1 if apply else 0
    return harness.execute(
        f"SET @apply_post_category_migration = {apply_value};\n{sql}",
        database=schema,
        include_headers=True,
    )
