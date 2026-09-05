"""运行隔离 MySQL HTTP 集成测试；凭据只通过子进程环境传递。"""

import argparse
import os
import subprocess
import sys
from urllib.parse import quote

from tests.scripts.mysql_migration_harness import PROJECT_ROOT, load_local_mysql_config


def main() -> int:
    parser = argparse.ArgumentParser(description="Run isolated identity HTTP integration tests")
    parser.add_argument(
        "--containers", action="store_true",
        help="Use the complete Testcontainers suite; ignore the test DSN and local database configuration",
    )
    args = parser.parse_args()
    env = os.environ.copy()
    if args.containers:
        env.pop("LYW_MIGRATION_TEST_DSN", None)
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
