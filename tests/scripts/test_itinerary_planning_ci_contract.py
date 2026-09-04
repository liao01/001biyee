"""#18 Dify 行程规划的数据、构建与持续集成契约。"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]


class ItineraryPlanningCiContractTests(unittest.TestCase):
    def test_domain_language_and_adr_keep_one_planning_fact_source(self):
        context = (ROOT / "CONTEXT.md").read_text(encoding="utf-8")
        for term in (
            "规划请求",
            "修订建议",
            "建议操作",
            "建议确认",
            "生成运行",
            "知识引用",
        ):
            with self.subTest(term=term):
                self.assertIn(f"**{term}**", context)

        adr = (ROOT / "docs/adr/0004-dify-itinerary-planning-adapter.md").read_text(
            encoding="utf-8"
        )
        for phrase in ("Dify", "可替换", "MySQL", "LangChain4j", "直接写"):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, adr)

    def test_data_document_names_tables_and_operational_boundaries(self):
        document = (ROOT / "docs/data/itinerary-planning.md").read_text(encoding="utf-8")
        for table in (
            "itinerary_planning_request",
            "itinerary_planning_destination",
            "itinerary_revision_proposal",
            "itinerary_revision_operation",
            "itinerary_revision_resolution",
        ):
            with self.subTest(table=table):
                self.assertIn(f"`{table}`", document)
        for phrase in (
            "唯一事实源",
            "Writer",
            "Reader",
            "ItineraryApplicationService",
            "幂等",
            "失败收敛",
            "dry-run",
            "回滚",
            "秘密",
            "知识库",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, document)

    def test_ci_auto_discovers_planning_migration_and_http_integration(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        runner = (ROOT / "scripts/run_backend_integration.py").read_text(encoding="utf-8")
        migration_test = ROOT / "tests/scripts/test_itinerary_planning_migration.py"
        http_it = ROOT / (
            "business/src/test/java/com/jiawa/lyw/itineraryplanning/api/"
            "ItineraryPlanningHttpIT.java"
        )

        self.assertTrue(migration_test.is_file())
        self.assertIn("20260902_itinerary_planning.sql", migration_test.read_text(encoding="utf-8"))
        self.assertIn("python3 -m unittest discover -s tests -t . -v", workflow)
        self.assertTrue(http_it.is_file())
        self.assertRegex(http_it.read_text(encoding="utf-8"), r"class\s+ItineraryPlanningHttpIT\b")
        self.assertIn("failsafe:integration-test", runner)
        self.assertIn("failsafe:verify", runner)
        self.assertNotIn("-Dit.test=", runner)

    def test_production_boundary_excludes_fakes_and_legacy_rag_entry(self):
        main_root = ROOT / "business/src/main"
        test_http_it = ROOT / (
            "business/src/test/java/com/jiawa/lyw/itineraryplanning/api/"
            "ItineraryPlanningHttpIT.java"
        )
        main_text = "\n".join(
            path.read_text(encoding="utf-8", errors="replace")
            for path in main_root.rglob("*")
            if path.is_file()
        )
        test_http_text = test_http_it.read_text(encoding="utf-8")
        self.assertIn('"test-run"', test_http_text)
        self.assertNotIn('"test-run"', main_text)
        self.assertNotRegex(main_text, r"\b(?:Fake|Stub)ItineraryPlannerGateway\b")

        production = (ROOT / "business/src/main/resources/application-prod.properties").read_text(
            encoding="utf-8"
        )
        environment = (ROOT / ".env.example").read_text(encoding="utf-8")
        required_secrets = {
            "DIFY_ITINERARY_BASE_URL",
            "DIFY_ITINERARY_API_KEY",
            "DIFY_ITINERARY_USER_HASH_KEY",
        }
        for secret in required_secrets:
            with self.subTest(secret=secret):
                self.assertIn("${" + secret + "}", production)
                self.assertIn(secret + "=", environment)
        for legacy in ("RAG_URL", "RAG_WORKSPACE_SLUG", "RAG_API_KEY"):
            with self.subTest(legacy=legacy):
                self.assertNotIn(legacy, production)
                self.assertNotIn(legacy, environment)
                self.assertNotIn(legacy, main_text)
        self.assertIsNone(re.search(r"Bearer\s+[A-Za-z0-9_-]{20,}", main_text))


if __name__ == "__main__":
    unittest.main()
