"""运行隔离 MySQL HTTP 集成测试；凭据只通过子进程环境传递。"""

import argparse
import os
import subprocess
import sys
from urllib.parse import quote

from tests.scripts.mysql_migration_harness import PROJECT_ROOT, load_local_mysql_config


CONTAINER_INTEGRATION_TESTS = (
    "com.jiawa.lyw.itinerary.api.ItineraryHttpIT",
    "com.jiawa.lyw.itinerarymap.api.ItineraryMapHttpIT",
    "com.jiawa.lyw.itinerarymap.infrastructure.RedisRouteReferenceCacheIT",
)


def container_test_sources_exist() -> bool:
    source_root = PROJECT_ROOT / "business" / "src" / "test" / "java"
    missing = [
        name for name in CONTAINER_INTEGRATION_TESTS
        if not (source_root / (name.replace(".", "/") + ".java")).is_file()
    ]
    if missing:
        print("Missing required container integration test source: " + ", ".join(missing), file=sys.stderr)
        return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Run isolated identity HTTP integration tests")
    parser.add_argument(
        "--containers", action="store_true",
        help="Use the complete Testcontainers suite; ignore the test DSN and local database configuration",
    )
    args = parser.parse_args()
    env = os.environ.copy()
    if args.containers:
        if not container_test_sources_exist():
            return 2
        env.pop("LYW_MIGRATION_TEST_DSN", None)
        env.pop("SPRING_DATASOURCE_URL", None)
        env.pop("SPRING_DATA_REDIS_URL", None)
        env["LYW_INTEGRATION_CONTAINERS"] = "true"
    else:
        env.pop("LYW_INTEGRATION_CONTAINERS", None)
        try:
            config = load_local_mysql_config()
        except (OSError, KeyError, ValueError, RuntimeError):
            print("Integration tests require valid loopback MySQL configuration", file=sys.stderr)
            return 2
        env["LYW_MIGRATION_TEST_DSN"] = (
            "mysql://" + quote(str(config["user"]), safe="") + ":"
            + quote(str(config["password"]), safe="") + "@"
            + str(config["host"]) + ":" + str(config["port"]) + "/" + str(config["database"])
        )
    wrapper = PROJECT_ROOT / "business" / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
    command += ["--batch-mode", "--no-transfer-progress", "-Pintegration", "test-compile", "failsafe:integration-test", "failsafe:verify"]
    try:
        return subprocess.run(command, cwd=PROJECT_ROOT / "business", env=env, check=False).returncode
    except OSError:
        print("Cannot start Maven integration tests", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
