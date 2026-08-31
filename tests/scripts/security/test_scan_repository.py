import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.security.scan_repository import scan_git_refs, scan_paths, scan_text


class ScanTextTests(unittest.TestCase):
    def test_identity_fixture_exception_requires_exact_path_and_value(self):
        path = Path("web/src/modules/identity/identitySession.test.js")
        value = "TEST-password-123"
        self.assertEqual([], scan_text(path, f"password: '{value}'"))
        self.assertTrue(scan_text(Path("web/src/view/login.vue"), f"password: '{value}'"))
        self.assertTrue(scan_text(path, "password: 'unapproved-fixture-value'"))

    def test_detects_secret_assignment_without_exposing_value(self):
        candidate_value = "sk-" + "example-secret-value-123456"

        findings = scan_text(
            Path("application.properties"),
            f"service.api-key={candidate_value}",
        )

        self.assertEqual("generic-secret-assignment", findings[0].rule_id)
        self.assertNotIn(candidate_value, findings[0].summary)
        self.assertNotIn(candidate_value, repr(findings[0]))

    def test_allows_environment_placeholder(self):
        findings = scan_text(
            Path("application.properties"),
            "service.api-key=${SERVICE_API_KEY}",
        )

        self.assertEqual([], findings)

    def test_known_historical_fixtures_are_scoped_to_their_documented_paths(self):
        plan_path = Path(
            "docs/superpowers/plans/2026-08-29-intelligent-travel-platform-phase-0-foundation.md"
        )
        fixture_findings = scan_text(plan_path, "password: 'Secret123'")
        production_findings = scan_text(
            Path("application.properties"),
            "password=Secret123",
        )

        self.assertEqual([], fixture_findings)
        self.assertIn(
            "generic-secret-assignment",
            {finding.rule_id for finding in production_findings},
        )

    def test_allows_quoted_xml_environment_placeholder(self):
        findings = scan_text(
            Path("generator.xml"),
            '<jdbcConnection password="${DB_PASSWORD}">',
        )

        self.assertEqual([], findings)

    def test_detects_sql_data_row_and_phone_number(self):
        phone_number = "188" + "00000000"
        findings = scan_text(
            Path("dump.sql"),
            f"INSERT INTO member VALUES (1, '{phone_number}');",
        )

        self.assertGreaterEqual(
            {finding.rule_id for finding in findings},
            {"sql-data-row", "phone-number"},
        )

    def test_does_not_treat_mapper_insert_as_sql_dump_data(self):
        findings = scan_text(
            Path("business/src/main/resources/mapper/MemberMapper.xml"),
            "INSERT INTO member (id) VALUES (1)",
        )

        self.assertNotIn(
            "sql-data-row",
            {finding.rule_id for finding in findings},
        )

    def test_allows_post_category_reference_seed_but_not_business_rows(self):
        category_findings = scan_text(
            Path("sql/migrations/post_categories.sql"),
            "INSERT INTO post_category (code, name) VALUES ('FOOD', '美食');",
        )
        member_findings = scan_text(
            Path("sql/migrations/member_seed.sql"),
            "INSERT INTO member (id, name) VALUES (1, 'traveler');",
        )

        self.assertNotIn(
            "sql-data-row",
            {finding.rule_id for finding in category_findings},
        )
        self.assertIn(
            "sql-data-row",
            {finding.rule_id for finding in member_findings},
        )

    def test_allows_empty_password_form_field(self):
        findings = scan_text(
            Path("web/src/view/login.vue"),
            'const form = { password: "" };',
        )

        self.assertEqual([], findings)

    def test_does_not_treat_password_label_as_assignment(self):
        findings = scan_text(
            Path("business/src/main/java/example/Member.java"),
            'builder.append(", password=");',
        )

        self.assertEqual([], findings)

    def test_does_not_treat_quoted_criterion_key_as_assignment(self):
        findings = scan_text(
            Path("business/src/main/java/example/MemberExample.java"),
            'addCriterion("password =", value, "password");',
        )

        self.assertEqual([], findings)

    def test_detects_private_key_header(self):
        private_key_header = "-----BEGIN " + "PRIVATE KEY-----"
        findings = scan_text(
            Path("identity.pem"),
            f"{private_key_header}\nnot-a-real-key\n",
        )

        self.assertIn(
            "private-key",
            {finding.rule_id for finding in findings},
        )

    def test_excluded_path_is_skipped_before_file_metadata_access(self):
        with patch.object(
            Path,
            "is_file",
            side_effect=PermissionError("metadata access denied"),
        ):
            findings = scan_paths([Path(".git")])

        self.assertEqual([], findings)

    def test_worktree_scan_skips_explicit_local_spring_secret_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            local_config = root / "business/src/main/resources/application.properties"
            local_config.parent.mkdir(parents=True)
            local_config.write_text("service.password=real-local-value", encoding="utf-8")

            findings = scan_paths([root])

        self.assertEqual([], findings)

    def test_scans_security_test_directory_for_real_secrets(self):
        candidate_value = "ghp_" + "exampleSecretValue1234567890"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            fixture = root / "tests/scripts/security/fixture.txt"
            fixture.parent.mkdir(parents=True)
            fixture.write_text(
                f"access_token={candidate_value}", encoding="utf-8"
            )

            findings = scan_paths([root])

        self.assertIn("github-token", {finding.rule_id for finding in findings})

    def test_scans_superpowers_documentation_for_real_secrets(self):
        candidate_value = "ghp_" + "exampleSecretValue1234567890"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            fixture = root / "docs/superpowers/notes.md"
            fixture.parent.mkdir(parents=True)
            fixture.write_text(candidate_value, encoding="utf-8")

            findings = scan_paths([root])

        self.assertIn("github-token", {finding.rule_id for finding in findings})

    def test_detects_json_secret_assignment(self):
        candidate_value = "credential-" + "value-1234567890"

        findings = scan_text(
            Path("config.json"),
            f'{{"apiKey":"{candidate_value}"}}',
        )

        self.assertIn(
            "generic-secret-assignment",
            {finding.rule_id for finding in findings},
        )

    def test_ignores_registry_contact_email_in_package_lock(self):
        contact_email = "registry" + "@example.invalid"

        findings = scan_text(
            Path("web/package-lock.json"),
            f'{{"deprecated":"contact {contact_email}"}}',
        )

        self.assertNotIn(
            "email-address",
            {finding.rule_id for finding in findings},
        )

    def test_allows_reserved_example_email_addresses(self):
        for domain in ("example.com", "example.org", "example.net", "example.invalid"):
            findings = scan_text(
                Path("business/src/test/java/ExampleTest.java"),
                f'var email = "demo@{domain}";',
            )

            self.assertNotIn(
                "email-address",
                {finding.rule_id for finding in findings},
            )

    def test_still_detects_high_confidence_token_in_package_lock(self):
        candidate_value = "ghp_" + "exampleSecretValue1234567890"

        findings = scan_text(
            Path("web/package-lock.json"),
            f'{{"resolved":"{candidate_value}"}}',
        )

        self.assertIn("github-token", {finding.rule_id for finding in findings})

    def test_allows_pinned_github_action_commit_in_workflow(self):
        action_commit = "3d3c42e5aac5ba805825da76410c181273ba90b1"

        findings = scan_text(
            Path(".github/workflows/ci.yml"),
            f"uses: actions/checkout@{action_commit} # v7.0.1",
        )

        self.assertNotIn(
            "password-hash",
            {finding.rule_id for finding in findings},
        )

    def test_still_detects_unscoped_hash_in_workflow(self):
        suspicious_hash = "a" * 40

        findings = scan_text(
            Path(".github/workflows/ci.yml"),
            f"run: echo {suspicious_hash}",
        )

        self.assertIn(
            "password-hash",
            {finding.rule_id for finding in findings},
        )

    def test_all_refs_scan_checks_blob_reused_outside_excluded_path(self):
        candidate_value = "ghp_" + "exampleSecretValue1234567890"
        with tempfile.TemporaryDirectory() as temp_dir:
            repository = Path(temp_dir)
            subprocess.run(
                ["git", "init"],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )
            subprocess.run(
                [
                    "git",
                    "config",
                    "user.email",
                    "security" + "@example.invalid",
                ],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )
            subprocess.run(
                ["git", "config", "user.name", "Security Test"],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )
            public_path = repository / "config/credential.txt"
            public_path.parent.mkdir(parents=True)
            public_path.write_text(
                f"access_token={candidate_value}",
                encoding="utf-8",
            )
            subprocess.run(
                ["git", "add", "."],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )
            subprocess.run(
                ["git", "commit", "-m", "add credential"],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )
            excluded_path = repository / ".worktrees/fixture.txt"
            excluded_path.parent.mkdir(parents=True)
            excluded_path.write_bytes(public_path.read_bytes())
            public_path.unlink()
            subprocess.run(
                ["git", "add", "-A"],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )
            subprocess.run(
                ["git", "commit", "-m", "move credential"],
                cwd=repository,
                capture_output=True,
                check=True,
                text=True,
            )

            findings = scan_git_refs(repository)

        self.assertIn("github-token", {finding.rule_id for finding in findings})

    def test_ref_scan_only_reads_requested_history(self):
        candidate_value = "ghp_" + "exampleSecretValue1234567890"
        with tempfile.TemporaryDirectory() as temp_dir:
            repository = Path(temp_dir)
            subprocess.run(["git", "init", "-b", "main"], cwd=repository, check=True, capture_output=True)
            subprocess.run(["git", "config", "user.email", "security@example.invalid"], cwd=repository, check=True)
            subprocess.run(["git", "config", "user.name", "Security Test"], cwd=repository, check=True)
            (repository / "README.md").write_text("clean\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repository, check=True)
            subprocess.run(["git", "commit", "-m", "clean"], cwd=repository, check=True, capture_output=True)
            subprocess.run(["git", "switch", "-c", "contaminated"], cwd=repository, check=True, capture_output=True)
            (repository / "credential.txt").write_text(candidate_value, encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repository, check=True)
            subprocess.run(["git", "commit", "-m", "contaminated"], cwd=repository, check=True, capture_output=True)

            clean_findings = scan_git_refs(repository, refs=["main"])
            all_findings = scan_git_refs(repository)

        self.assertNotIn("github-token", {finding.rule_id for finding in clean_findings})
        self.assertIn("github-token", {finding.rule_id for finding in all_findings})


class CommandLineTests(unittest.TestCase):
    def test_cli_report_omits_secret_value_and_returns_one_for_findings(self):
        candidate_value = "ghp_" + "exampleSecretValue1234567890"
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "config.txt").write_text(
                f"access_token={candidate_value}\n",
                encoding="utf-8",
            )
            report = root / "report.json"

            completed = subprocess.run(
                [
                    sys.executable,
                    "scripts/security/scan_repository.py",
                    str(root),
                    "--report",
                    str(report),
                ],
                cwd=Path(__file__).resolve().parents[3],
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(1, completed.returncode)
            self.assertNotIn(candidate_value, completed.stdout)
            self.assertNotIn(candidate_value, completed.stderr)
            report_data = json.loads(report.read_text(encoding="utf-8"))
            self.assertNotIn(candidate_value, json.dumps(report_data))

    def test_runtime_configuration_and_credential_utilities_are_clean(self):
        repository = Path(__file__).resolve().parents[3]
        protected_files = [
            repository
            / "business/src/main/resources/application.properties.example",
            repository / "business/src/main/resources/application.yml.example",
            repository
            / "business/src/main/java/com/jiawa/lyw/Util/MailUtils.java",
            repository
            / "business/src/main/java/com/jiawa/lyw/Util/JwtUtil.java",
        ]

        findings = []
        for protected_file in protected_files:
            findings.extend(
                scan_text(
                    protected_file.relative_to(repository),
                    protected_file.read_text(encoding="utf-8"),
                )
            )

        self.assertEqual([], findings)

    def test_repository_ignores_runtime_and_credential_files(self):
        repository = Path(__file__).resolve().parents[3]
        ignored_paths = [
            ".env",
            ".env.local",
            "service.log",
            "log/runtime.log",
            "logs/runtime.log",
            "business/log/runtime.log",
            "business/target/classes/App.class",
            "gengerator/target/classes/App.class",
            "uploads/member-avatar.jpg",
            "business/src/main/resources/application.properties",
            "business/src/main/resources/application.yml",
            "identity.p12",
            "identity.pfx",
            "identity.pem",
            "identity.key",
            "identity.jks",
            "identity.keystore",
            "load-test.jmx",
            ".idea/misc.xml",
        ]

        for ignored_path in ignored_paths:
            completed = subprocess.run(
                ["git", "check-ignore", "-q", "--no-index", "--", ignored_path],
                cwd=repository,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, completed.returncode, ignored_path)

        example = subprocess.run(
            ["git", "check-ignore", "-q", "--no-index", "--", ".env.example"],
            cwd=repository,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(1, example.returncode)

        for safe_example in (
            "business/src/main/resources/application.properties.example",
            "business/src/main/resources/application.yml.example",
        ):
            completed = subprocess.run(
                ["git", "check-ignore", "-q", "--no-index", "--", safe_example],
                cwd=repository,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(1, completed.returncode, safe_example)


if __name__ == "__main__":
    unittest.main()
