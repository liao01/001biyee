import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
COMPOSE_PATH = PROJECT_ROOT / "deploy" / "compose.yaml"
PRODUCTION_PROPERTIES = (
    PROJECT_ROOT / "business" / "src" / "main" / "resources" / "application-prod.properties"
)
CI_WORKFLOW = PROJECT_ROOT / ".github" / "workflows" / "ci.yml"
PREPARE_SERVER = PROJECT_ROOT / "scripts" / "deploy" / "prepare_server.sh"
VERIFY_SERVER = PROJECT_ROOT / "scripts" / "deploy" / "verify_server.sh"
NGINX_ROUTES = PROJECT_ROOT / "deploy" / "nginx" / "lyw-locations.conf.inc"


class DeploymentComposeContractTests(unittest.TestCase):
    def load_compose_model(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime_env = Path(directory) / "runtime.env"
            runtime_env.write_text("", encoding="utf-8")
            env = os.environ.copy()
            env["LYW_RUNTIME_ENV_FILE"] = str(runtime_env)
            completed = subprocess.run(
                [
                    "docker",
                    "compose",
                    "-f",
                    str(COMPOSE_PATH),
                    "config",
                    "--format",
                    "json",
                ],
                cwd=PROJECT_ROOT,
                env=env,
                text=True,
                encoding="utf-8",
                capture_output=True,
                check=False,
            )
        self.assertEqual(0, completed.returncode, completed.stderr)
        return json.loads(completed.stdout)

    def test_compose_defines_private_healthy_single_server_topology(self):
        model = self.load_compose_model()
        services = model["services"]
        expected = {
            "lyw-mysql",
            "lyw-mongo",
            "lyw-redis",
            "lyw-storage-init",
            "lyw-backend",
            "lyw-frontend",
            "lyw-prometheus",
        }
        self.assertEqual(expected, set(services))

        for name in ("lyw-mysql", "lyw-mongo", "lyw-redis", "lyw-prometheus"):
            with self.subTest(service=name):
                self.assertNotIn("ports", services[name])

        for name in expected - {"lyw-storage-init"}:
            with self.subTest(service=name):
                self.assertEqual("unless-stopped", services[name]["restart"])
                self.assertIn("mem_limit", services[name])
                self.assertIn("healthcheck", services[name])

        backend_healthcheck = " ".join(services["lyw-backend"]["healthcheck"]["test"])
        self.assertIn("/lyw/actuator/health/readiness", backend_healthcheck)
        self.assertEqual(True, model["networks"]["lyw_internal"]["internal"])

    def test_production_only_exposes_safe_actuator_endpoints(self):
        properties = PRODUCTION_PROPERTIES.read_text(encoding="utf-8")
        self.assertIn("management.endpoints.web.exposure.include=health,info,prometheus", properties)
        self.assertIn("management.endpoint.health.probes.enabled=true", properties)
        self.assertIn("management.endpoint.health.show-details=never", properties)
        self.assertNotIn("management.endpoints.web.exposure.include=*", properties)

    def test_ci_runs_migrations_security_and_compose_validation(self):
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("migration-tests:", workflow)
        self.assertIn("LYW_MIGRATION_TEST_DSN", workflow)
        self.assertIn("compose-contract:", workflow)
        self.assertIn("docker compose -f deploy/compose.yaml config --quiet", workflow)
        self.assertIn("scripts/security/scan_repository.py", workflow)

    def test_server_scripts_prepare_and_verify_prometheus(self):
        prepare = PREPARE_SERVER.read_text(encoding="utf-8")
        verify = VERIFY_SERVER.read_text(encoding="utf-8")

        self.assertIn("/opt/lyw/data/prometheus", prepare)
        self.assertIn("-o 65534 -g 65534", prepare)
        self.assertIn("lyw-prometheus", verify)

    def test_public_nginx_does_not_proxy_actuator_endpoints(self):
        routes = NGINX_ROUTES.read_text(encoding="utf-8")

        self.assertIn("location ^~ /business/lyw/actuator/", routes)
        self.assertIn("return 404;", routes)


if __name__ == "__main__":
    unittest.main()
