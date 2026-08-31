"""验证集成运行器的公开命令行，不启动 Maven 或读写数据库。"""

import os
import subprocess
import sys
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]


class BackendIntegrationRunnerTests(unittest.TestCase):
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
