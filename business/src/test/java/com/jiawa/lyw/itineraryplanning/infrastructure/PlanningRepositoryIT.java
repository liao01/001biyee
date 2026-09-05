package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itineraryplanning.application.PlanningRepository;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.PlanningStatus;
import com.jiawa.lyw.itineraryplanning.domain.ProposalStatus;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PlanningRepositoryIT.Config.class)
class PlanningRepositoryIT {
    private static final MySqlIntegrationDatabase DATABASE = new MySqlIntegrationDatabase();
    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");

    @Autowired private PlanningRepository repository;
    @Autowired private DataSource dataSource;
    @Autowired private TestIds ids;
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepare() {
        jdbc = new JdbcTemplate(dataSource);
        cleanup();
        ids.reset();
        jdbc.update("INSERT INTO member (id, name, email, email_verified_at, account_status) "
                        + "VALUES (7, 'IT-TEST-#18-owner', 'planning-owner@example.com', CURRENT_TIMESTAMP(3), 'ACTIVE')");
        jdbc.update("INSERT INTO itinerary "
                        + "(id, owner_member_id, title, start_date, end_date, time_zone, base_currency, status, version) "
                        + "VALUES (42, 7, 'IT-TEST-#18-itinerary', '2026-10-02', '2026-10-03', "
                        + "'Asia/Shanghai', 'CNY', 'DRAFT', 3)");
    }

    @AfterEach
    void cleanupAndVerify() {
        cleanup();
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM member WHERE name LIKE 'IT-TEST-#18-%'", Integer.class
        ));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_planning_request", Integer.class));
    }

    @AfterAll
    static void closeDatabase() {
        DATABASE.close();
    }

    @Test
    void persistsDraftGenerationProposalOperationsAndResolutionAcrossReads() {
        PlanningRepository.RequestRecord created = repository.createDraft(100, 7, request(), NOW);
        assertEquals(PlanningStatus.DRAFT, created.status());
        assertEquals("杭州", created.draft().destinations().get(0).name());

        PlanningRepository.RequestRecord claimed = repository.claimGeneration(100, 7, 1, NOW).orElseThrow();
        assertEquals(PlanningStatus.GENERATING, claimed.status());
        assertEquals(2, claimed.version());

        PlanningRepository.ProposalRecord ready = repository.saveReadyProposal(
                new PlanningRepository.ProposalRecord(
                        200, 100, 42, 7, 3, ProposalStatus.READY, validated(), "DIFY",
                        "run-1", "model", "workflow-v1", 250L, 321L, null
                ),
                NOW
        );
        PlanningRepository.ProposalRecord reopened = repository.findProposal(ready.id()).orElseThrow();
        assertEquals(ProposalStatus.READY, reopened.status());
        assertEquals("知识库建议", reopened.validatedProposal().proposal().summary());
        assertEquals(new BigDecimal("120.00"), reopened.validatedProposal().projectedCost());
        assertEquals(Set.of(), reopened.validatedProposal().dependencies().get("add-one"));
        assertEquals(PlanningStatus.READY, repository.findRequest(100).orElseThrow().status());
        assertEquals(3, repository.findRequest(100).orElseThrow().version());

        UUID decision = UUID.fromString("00000000-0000-0000-0000-000000000301");
        PlanningRepository.ResolutionRecord resolution = new PlanningRepository.ResolutionRecord(
                300, 200, 7, decision, true, "a".repeat(64), 3L,
                UUID.fromString("00000000-0000-0000-0000-000000000302"), 4L
        );
        repository.saveResolution(resolution, ProposalStatus.CONFIRMED, NOW);

        assertEquals(resolution, repository.findResolutionByDecision(decision).orElseThrow());
        assertEquals(ProposalStatus.CONFIRMED, repository.findProposal(200).orElseThrow().status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_revision_operation", Integer.class));
    }

    @Test
    void optimisticDraftAndGenerationClaimsAllowOnlyOneWinner() {
        repository.createDraft(100, 7, request(), NOW);

        assertTrue(repository.updateDraft(100, 7, 1, request(), NOW).isPresent());
        assertTrue(repository.updateDraft(100, 7, 1, request(), NOW).isEmpty());
        assertTrue(repository.claimGeneration(100, 7, 2, NOW).isPresent());
        assertTrue(repository.claimGeneration(100, 7, 2, NOW).isEmpty());
    }

    @Test
    void replaysTheStoredResolutionWhenOnlyTheGeneratedDatabaseIdDiffers() {
        repository.createDraft(100, 7, request(), NOW);
        repository.claimGeneration(100, 7, 1, NOW).orElseThrow();
        repository.saveReadyProposal(
                new PlanningRepository.ProposalRecord(
                        200, 100, 42, 7, 3, ProposalStatus.READY, validated(), "DIFY",
                        "run-replay", null, null, 1L, null, null
                ),
                NOW
        );
        UUID decision = UUID.fromString("00000000-0000-0000-0000-000000000311");
        UUID command = UUID.fromString("00000000-0000-0000-0000-000000000312");
        PlanningRepository.ResolutionRecord first = new PlanningRepository.ResolutionRecord(
                300, 200, 7, decision, true, "b".repeat(64), 3L, command, 4L
        );
        PlanningRepository.ResolutionRecord retry = new PlanningRepository.ResolutionRecord(
                301, 200, 7, decision, true, "b".repeat(64), 3L, command, 4L
        );

        assertEquals(first, repository.saveResolution(first, ProposalStatus.CONFIRMED, NOW));
        assertEquals(first, repository.saveResolution(retry, ProposalStatus.CONFIRMED, NOW));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_revision_resolution WHERE proposal_id = 200", Integer.class
        ));
    }

    private PlanningModels.RequestDraft request() {
        return new PlanningModels.RequestDraft(
                42, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
                new BigDecimal("3000.00"), Currency.getInstance("CNY"), 2,
                new PlanningModels.Preferences(
                        PlanningModels.TravelPace.BALANCED,
                        Set.of(PlanningModels.PreferenceTag.CULTURE), "不早起"
                ),
                List.of(new PlanningModels.DestinationInput(
                        "杭州", "CN", ZoneId.of("Asia/Shanghai")
                ))
        );
    }

    private PlanningModels.ValidatedProposal validated() {
        PlanningModels.AddItemOperation add = new PlanningModels.AddItemOperation(
                "add-one", "新增午餐",
                new PlanningModels.ItemFields(
                        LocalDate.of(2026, 10, 2), "午餐", "餐厅",
                        LocalTime.of(11, 30), LocalTime.of(13, 0), null,
                        new BigDecimal("120.00")
                )
        );
        PlanningModels.CandidateProposal proposal = new PlanningModels.CandidateProposal(
                PlanningModels.REVISION_CONTRACT_V1, "知识库建议", List.of(add),
                List.of("kb:guide:1")
        );
        return new PlanningModels.ValidatedProposal(
                proposal, new BigDecimal("120.00"), Map.of("add-one", Set.of())
        );
    }

    private void cleanup() {
        jdbc.update("DELETE FROM itinerary_revision_resolution");
        jdbc.update("DELETE FROM itinerary_revision_operation");
        jdbc.update("DELETE FROM itinerary_revision_proposal");
        jdbc.update("DELETE FROM itinerary_planning_destination");
        jdbc.update("DELETE FROM itinerary_planning_request");
        jdbc.update("DELETE FROM itinerary_item");
        jdbc.update("DELETE FROM itinerary_day");
        jdbc.update("DELETE FROM itinerary_destination");
        jdbc.update("DELETE FROM itinerary_command");
        jdbc.update("DELETE FROM itinerary");
        jdbc.update("DELETE FROM member WHERE name LIKE 'IT-TEST-#18-%'");
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = PlanningMapper.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
        }

        @Bean SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath:mapper/itineraryplanning/*.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory;
        }

        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean TestIds testIds() { return new TestIds(); }
        @Bean ItineraryIdGenerator itineraryIdGenerator(TestIds ids) { return ids::next; }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean RevisionContractParser revisionContractParser(ObjectMapper mapper) {
            return new RevisionContractParser(mapper, PlanningModels.REVISION_CONTRACT_V1);
        }
        @Bean PlanningRepository planningRepository(
                PlanningMapper mapper,
                ItineraryIdGenerator ids,
                ObjectMapper objectMapper,
                RevisionContractParser parser
        ) {
            return new MyBatisPlanningRepository(mapper, ids, objectMapper, parser);
        }
    }

    static final class TestIds {
        private final AtomicLong value = new AtomicLong(1000);
        long next() { return value.getAndIncrement(); }
        void reset() { value.set(1000); }
    }
}
