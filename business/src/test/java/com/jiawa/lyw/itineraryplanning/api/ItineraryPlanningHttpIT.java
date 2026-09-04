package com.jiawa.lyw.itineraryplanning.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.identity.api.IdentityController;
import com.jiawa.lyw.identity.api.IdentityExceptionHandler;
import com.jiawa.lyw.identity.infrastructure.IdentityConfiguration;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.itinerary.application.ItineraryApplicationService;
import com.jiawa.lyw.itinerary.application.ItineraryIdGenerator;
import com.jiawa.lyw.itinerary.api.ItineraryController;
import com.jiawa.lyw.itinerary.api.ItineraryExceptionHandler;
import com.jiawa.lyw.itinerary.infrastructure.ItineraryConfiguration;
import com.jiawa.lyw.itineraryplanning.application.DefaultItineraryPlanningApplicationService;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlannerGateway;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlanningApplicationService;
import com.jiawa.lyw.itineraryplanning.application.PlanningRepository;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.RevisionProposalValidator;
import com.jiawa.lyw.itineraryplanning.infrastructure.MyBatisPlanningRepository;
import com.jiawa.lyw.itineraryplanning.infrastructure.PlanningMapper;
import com.jiawa.lyw.itineraryplanning.infrastructure.RevisionContractParser;
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
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ItineraryPlanningHttpIT.Config.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "jwt.secret=integration-test-signing-key-at-least-32-bytes",
        "app.public-url=https://travel.example.test/travel",
        "identity.secure-cookie=false",
        "app.ai.itinerary.dify.base-url=http://127.0.0.1:1"
})
class ItineraryPlanningHttpIT {
    private static final MySqlIntegrationDatabase DATABASE = new MySqlIntegrationDatabase();
    private static final String PREFIX = "IT-TEST-#18-";

    @Autowired private WebApplicationContext context;
    @Autowired private DataSource dataSource;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private ObjectMapper json;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc = new JdbcTemplate(dataSource);
        json = new ObjectMapper().findAndRegisterModules();
        cleanup();
        String legacyHash = "b406cd63d1530b73" + "464838f07947ccca";
        jdbc.update("INSERT INTO member "
                        + "(id, email, email_verified_at, password_hash, password_algorithm, account_status, password, name) "
                        + "VALUES (42, 'planning@example.com', CURRENT_TIMESTAMP(3), ?, 'LEGACY_DOUBLE_MD5', "
                        + "'ACTIVE', ?, ?)", legacyHash, legacyHash, PREFIX + "owner");
    }

    @AfterEach
    void cleanupAndVerify() {
        cleanup();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary_planning_request", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM member WHERE name LIKE 'IT-TEST-#18-%'", Integer.class
        ));
    }

    @AfterAll static void closeDatabase() { DATABASE.close(); }

    @Test
    void ownerGeneratesConfirmsAndRejectsWithoutAnyDirectAiWrite() throws Exception {
        mvc.perform(get("/web/itineraries/42/planning/request"))
                .andExpect(status().isUnauthorized());
        String access = login();
        String itineraryId = createItinerary(access);

        JsonNode saved = perform(put("/web/itineraries/{id}/planning/request", itineraryId), access, """
                {"requestId":null,"expectedVersion":0,"draft":{
                  "startDate":"2026-10-02","endDate":"2026-10-03","budgetAmount":3000.00,
                  "budgetCurrency":"CNY","partySize":2,
                  "preferences":{"pace":"BALANCED","tags":["CULTURE"],"notes":"不早起"},
                  "destinations":[{"name":"杭州","countryCode":"CN","timeZone":"Asia/Shanghai"}]
                }}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(1)).json();

        JsonNode generated = perform(post("/web/itineraries/{id}/planning/generate", itineraryId), access,
                "{\"expectedVersion\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.status").value("READY"))
                .andExpect(jsonPath("$.content.knowledgeReferenceIds[0]").value("kb:test-guide:1"))
                .json();
        String proposalId = generated.path("content").path("id").asText();

        perform(post("/web/itineraries/{id}/planning/proposals/{proposal}/confirm", itineraryId, proposalId),
                access, """
                {"decisionId":"00000000-0000-0000-0000-000000000401",
                 "commandId":"00000000-0000-0000-0000-000000000402",
                 "expectedItineraryVersion":1,"selectedOperationKeys":["add-museum"]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.confirmed").value(true))
                .andExpect(jsonPath("$.content.resultVersion").value(2));
        perform(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.days[0].items[0].title").value("博物馆"));

        JsonNode second = perform(post("/web/itineraries/{id}/planning/generate", itineraryId), access,
                "{\"expectedVersion\":3}")
                .andExpect(status().isOk()).json();
        perform(post("/web/itineraries/{id}/planning/proposals/{proposal}/reject",
                        itineraryId, second.path("content").path("id").asText()),
                access, "{\"decisionId\":\"00000000-0000-0000-0000-000000000403\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.confirmed").value(false));
        perform(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.version").value(2))
                .andExpect(jsonPath("$.content.days[0].items.length()").value(1));
    }

    private String login() throws Exception {
        return json.readTree(mvc.perform(post("/web/identity/login")
                        .contentType("application/json")
                        .content("{\"email\":\"planning@example.com\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("content").path("accessToken").asText();
    }

    private String createItinerary(String access) throws Exception {
        return perform(post("/web/itineraries"), access, """
                {"commandId":"00000000-0000-0000-0000-000000000400","expectedVersion":0,"payload":{
                  "title":"IT-TEST-#18-杭州","startDate":"2026-10-02","endDate":"2026-10-03",
                  "timeZone":"Asia/Shanghai","baseCurrency":"CNY",
                  "destinations":[{"name":"杭州","countryCode":"CN","timeZone":"Asia/Shanghai"}]}}
                """).andExpect(status().isOk()).json().path("content").path("itineraryId").asText();
    }

    private Result perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String access,
            String body
    ) throws Exception {
        request.header("Authorization", "Bearer " + access);
        if (body != null) request.contentType("application/json").content(body);
        return new Result(mvc.perform(request));
    }

    private void cleanup() {
        jdbc.update("DELETE FROM itinerary_revision_resolution");
        jdbc.update("DELETE FROM itinerary_revision_operation");
        jdbc.update("DELETE FROM itinerary_revision_proposal");
        jdbc.update("DELETE FROM itinerary_planning_destination");
        jdbc.update("DELETE FROM itinerary_planning_request");
        jdbc.update("DELETE FROM itinerary_command");
        jdbc.update("DELETE FROM itinerary_item");
        jdbc.update("DELETE FROM itinerary_day");
        jdbc.update("DELETE FROM itinerary_destination");
        jdbc.update("DELETE FROM itinerary");
        jdbc.update("DELETE FROM identity_one_time_token");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM member_login_log");
        jdbc.update("DELETE FROM member WHERE name LIKE 'IT-TEST-#18-%'");
    }

    private final class Result {
        private final org.springframework.test.web.servlet.ResultActions actions;
        private Result(org.springframework.test.web.servlet.ResultActions actions) { this.actions = actions; }
        private Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher); return this;
        }
        private JsonNode json() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = PlanningMapper.class)
    @Import({
            IdentityConfiguration.class, IdentityController.class, IdentityExceptionHandler.class,
            ItineraryConfiguration.class, ItineraryController.class, ItineraryExceptionHandler.class,
            ItineraryPlanningController.class, ItineraryPlanningExceptionHandler.class
    })
    static class Config {
        @Bean @Primary IdentityMailGateway mailGateway() {
            return new IdentityMailGateway() {
                @Override public void sendVerificationLink(String email, URI link) { }
                @Override public void sendPasswordResetLink(String email, URI link) { }
            };
        }
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-03T00:00:00Z"), ZoneOffset.UTC);
        }
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
        }
        @Bean SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            factory.setMapperLocations(java.util.stream.Stream.of(
                    "classpath:mapper/identity/*.xml", "classpath:mapper/itinerary/*.xml",
                    "classpath:mapper/itineraryplanning/*.xml"
            ).flatMap(pattern -> {
                try { return java.util.Arrays.stream(resolver.getResources(pattern)); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            }).toArray(org.springframework.core.io.Resource[]::new));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory;
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean RevisionContractParser revisionContractParser(ObjectMapper mapper) {
            return new RevisionContractParser(mapper, PlanningModels.REVISION_CONTRACT_V1);
        }
        @Bean RevisionProposalValidator revisionProposalValidator() {
            return new RevisionProposalValidator(PlanningModels.REVISION_CONTRACT_V1, 80);
        }
        @Bean PlanningRepository planningRepository(
                PlanningMapper mapper, ItineraryIdGenerator ids, ObjectMapper json,
                RevisionContractParser parser
        ) { return new MyBatisPlanningRepository(mapper, ids, json, parser); }
        @Bean ItineraryPlannerGateway itineraryPlannerGateway() {
            return (actor, request, snapshot) -> {
                boolean alreadyConfirmedFirstProposal = snapshot.version() > 1;
                PlanningModels.AddItemOperation add = new PlanningModels.AddItemOperation(
                        alreadyConfirmedFirstProposal ? "add-park" : "add-museum",
                        alreadyConfirmedFirstProposal ? "增加公园" : "增加博物馆",
                        new PlanningModels.ItemFields(
                                LocalDate.of(2026, 10, 2),
                                alreadyConfirmedFirstProposal ? "公园" : "博物馆",
                                alreadyConfirmedFirstProposal ? "西湖公园" : "浙江省博物馆",
                                alreadyConfirmedFirstProposal ? LocalTime.of(11, 0) : LocalTime.of(9, 0),
                                alreadyConfirmedFirstProposal ? LocalTime.of(12, 0) : LocalTime.of(10, 0),
                                null,
                                alreadyConfirmedFirstProposal
                                        ? new BigDecimal("0.00") : new BigDecimal("20.00")
                        )
                );
                return new ItineraryPlannerGateway.Generation(
                        new PlanningModels.CandidateProposal(
                                PlanningModels.REVISION_CONTRACT_V1, "知识库建议", List.of(add),
                                List.of("kb:test-guide:1")
                        ),
                        "test-run", "test-model", "test-workflow", 1, 1L
                );
            };
        }
        @Bean ItineraryPlanningApplicationService planningService(
                PlanningRepository repository, ItineraryPlannerGateway gateway,
                RevisionProposalValidator validator, ItineraryApplicationService itineraries,
                ItineraryIdGenerator ids, Clock clock
        ) {
            return new DefaultItineraryPlanningApplicationService(
                    repository, gateway, validator, itineraries, ids, clock
            );
        }
    }
}
