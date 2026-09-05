"""验证集成运行器的公开命令行，不启动 Maven 或读写数据库。"""

import os
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.run_backend_integration import main


PROJECT_ROOT = Path(__file__).resolve().parents[2]


class BackendIntegrationRunnerTests(unittest.TestCase):
    def test_container_mode_enables_the_complete_suite_and_does_not_forward_a_local_dsn(self):
        with patch.dict(os.environ, {"LYW_MIGRATION_TEST_DSN": "invalid-test-dsn"}), \
                patch.object(sys, "argv", ["run_backend_integration", "--containers"]), \
                patch("scripts.run_backend_integration.subprocess.run") as process:
            process.return_value.returncode = 0
            self.assertEqual(0, main())
            # 子进程环境是运行器对 Maven 的公开契约；失败时不输出完整环境。
            child_env = process.call_args.kwargs["env"]
            self.assertEqual("true", child_env.get("LYW_INTEGRATION_CONTAINERS"))
            self.assertFalse("LYW_MIGRATION_TEST_DSN" in child_env)

    def test_help_explains_container_mode_without_loading_database_configuration(self):
        env = os.environ.copy()
        env["LYW_MIGRATION_TEST_DSN"] = "invalid-test-dsn"
        result = subprocess.run(
            [sys.executable, "-m", "scripts.run_backend_integration", "--help"],
            cwd=PROJECT_ROOT,
            env=env,
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("--containers", result.stdout)
        self.assertNotIn("invalid-test-dsn", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
