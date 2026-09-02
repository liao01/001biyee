package com.jiawa.lyw.itinerary.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.identity.api.IdentityController;
import com.jiawa.lyw.identity.api.IdentityExceptionHandler;
import com.jiawa.lyw.identity.infrastructure.IdentityConfiguration;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.itinerary.infrastructure.ItineraryConfiguration;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.SqlSessionFactoryBean;
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
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ItineraryHttpIT.Config.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "jwt.secret=integration-test-signing-key-at-least-32-bytes",
        "app.public-url=https://travel.example.test/travel",
        "identity.secure-cookie=false"
})
class ItineraryHttpIT {
    private static final MySqlIntegrationDatabase DATABASE = new MySqlIntegrationDatabase();
    private static final String TEST_PREFIX = "IT-TEST-#17-";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private ObjectMapper json;

    @BeforeEach
    void prepareAccounts() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc = new JdbcTemplate(dataSource);
        json = new ObjectMapper().findAndRegisterModules();
        cleanup();
        String legacyHash = "b406cd63d1530b73" + "464838f07947ccca";
        jdbc.update("INSERT INTO member "
                        + "(id, email, email_verified_at, password_hash, password_algorithm, account_status, password, name) "
                        + "VALUES (42, 'owner@example.com', CURRENT_TIMESTAMP(3), ?, 'LEGACY_DOUBLE_MD5', 'ACTIVE', ?, ?), "
                        + "(84, 'other@example.com', CURRENT_TIMESTAMP(3), ?, 'LEGACY_DOUBLE_MD5', 'ACTIVE', ?, ?)",
                legacyHash, legacyHash, TEST_PREFIX + "owner",
                legacyHash, legacyHash, TEST_PREFIX + "other");
    }

    @AfterEach
    void removeBatchAndVerify() {
        cleanup();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM member WHERE name LIKE 'IT-TEST-#17-%'", Integer.class
        ));
    }

    @AfterAll
    static void removeIsolatedDatabase() {
        DATABASE.close();
    }

    @Test
    void authenticatedOwnerCreatesListsAndReadsAStringIdSnapshot() throws Exception {
        mvc.perform(get("/web/itineraries"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        String access = login("owner@example.com");
        JsonNode created = performJson(post("/web/itineraries"), access, createBody("00000000-0000-0000-0000-000000000201"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content.itineraryId").isString())
                .andExpect(jsonPath("$.content.version").value(1))
                .andReturnJson();
        String itineraryId = created.path("content").path("itineraryId").asText();

        performJson(get("/web/itineraries").param("limit", "20"), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[0].id").value(itineraryId))
                .andExpect(jsonPath("$.content.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.content.items[0].updatedAt").isString());
        performJson(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.id").value(itineraryId))
                .andExpect(jsonPath("$.content.days[0].id").isString())
                .andExpect(jsonPath("$.content.allowedTransitions").isArray())
                .andExpect(jsonPath("$.content.suggestedStatus").doesNotExist());

        String otherAccess = login("other@example.com");
        performJson(get("/web/itineraries/{id}", itineraryId), otherAccess, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.content.errorCode").value("ITINERARY_NOT_FOUND"));
        performJson(get("/web/itineraries/{id}", "999999999"), otherAccess, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.content.errorCode").value("ITINERARY_NOT_FOUND"));
    }

    @Test
    void everyMutationRouteUsesOneVersionAndSupportsSafeReplay() throws Exception {
        String access = login("owner@example.com");
        String itineraryId = create(access, "00000000-0000-0000-0000-000000000202");
        JsonNode initial = getSnapshot(access, itineraryId);
        String firstDay = initial.path("content").path("days").get(0).path("id").asText();
        String secondDay = initial.path("content").path("days").get(1).path("id").asText();

        performJson(put("/web/itineraries/{id}/destinations", itineraryId), access, """
                {"commandId":"00000000-0000-0000-0000-000000000203","expectedVersion":1,
                 "payload":{"destinations":[{"name":"杭州","countryCode":"CN","timeZone":"Asia/Shanghai"}]}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(2));
        performJson(patch("/web/itineraries/{id}", itineraryId), access, """
                {"commandId":"00000000-0000-0000-0000-000000000204","expectedVersion":2,
                 "payload":{"title":"IT-TEST-#17-更新行程"}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(3));

        JsonNode first = performJson(post("/web/itineraries/{id}/items", itineraryId), access, addItemBody(
                "00000000-0000-0000-0000-000000000205", 3, firstDay, "第一项"
        )).andExpect(status().isOk()).andExpect(jsonPath("$.content.itemId").isString()).andReturnJson();
        String firstItem = first.path("content").path("itemId").asText();
        performJson(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.suggestedStatus").value("PLANNED"));
        JsonNode second = performJson(post("/web/itineraries/{id}/items", itineraryId), access, addItemBody(
                "00000000-0000-0000-0000-000000000206", 4, firstDay, "第二项"
        )).andExpect(status().isOk()).andReturnJson();
        String secondItem = second.path("content").path("itemId").asText();

        performJson(put("/web/itineraries/{id}/days/{dayId}/item-order", itineraryId, firstDay), access, """
                {"commandId":"00000000-0000-0000-0000-000000000207","expectedVersion":5,
                 "payload":{"itemIds":["%s","%s"]}}
                """.formatted(secondItem, firstItem))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(6));
        performJson(patch("/web/itineraries/{id}/items/{itemId}", itineraryId, firstItem), access, """
                {"commandId":"00000000-0000-0000-0000-000000000208","expectedVersion":6,
                 "payload":{"dayId":"%s","title":"移动后","startTime":"09:00","endTime":"10:00"}}
                """.formatted(secondDay))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(7));

        String deleteBody = """
                {"commandId":"00000000-0000-0000-0000-000000000209","expectedVersion":7,"payload":{}}
                """;
        performJson(delete("/web/itineraries/{id}/items/{itemId}", itineraryId, secondItem), access, deleteBody)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(8));
        performJson(delete("/web/itineraries/{id}/items/{itemId}", itineraryId, secondItem), access, deleteBody)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.replayed").value(true))
                .andExpect(jsonPath("$.content.version").value(8));

        performJson(post("/web/itineraries/{id}/status-transitions", itineraryId), access, """
                {"commandId":"00000000-0000-0000-0000-000000000210","expectedVersion":8,
                 "payload":{"toStatus":"PLANNED"}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.content.version").value(9));
        performJson(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.suggestedStatus").value("IN_PROGRESS"));
    }

    @Test
    void validationVersionAndIdempotencyFailuresExposeOnlyStableCodes() throws Exception {
        String access = login("owner@example.com");
        String itineraryId = create(access, "00000000-0000-0000-0000-000000000211");
        String dayId = getSnapshot(access, itineraryId).path("content").path("days").get(0).path("id").asText();

        performJson(post("/web/itineraries/{id}/items", itineraryId), access, """
                {"commandId":"00000000-0000-0000-0000-000000000212","expectedVersion":1,
                 "payload":{"dayId":"%s","title":"坏时间","startTime":"10:00","endTime":"09:00"}}
                """.formatted(dayId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content.errorCode").value("INVALID_ITEM"));
        performJson(patch("/web/itineraries/{id}", itineraryId), access, """
                {"commandId":"00000000-0000-0000-0000-000000000213","expectedVersion":99,
                 "payload":{"title":"不会写入"}}
                """).andExpect(status().isConflict())
                .andExpect(jsonPath("$.content.errorCode").value("VERSION_CONFLICT"));

        String commandId = "00000000-0000-0000-0000-000000000214";
        performJson(post("/web/itineraries/{id}/items", itineraryId), access,
                addItemBody(commandId, 1, dayId, "原载荷")).andExpect(status().isOk());
        performJson(post("/web/itineraries/{id}/items", itineraryId), access,
                addItemBody(commandId, 1, dayId, "不同载荷"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.content.errorCode").value("IDEMPOTENCY_CONFLICT"));
    }

    private String create(String access, String commandId) throws Exception {
        return performJson(post("/web/itineraries"), access, createBody(commandId))
                .andExpect(status().isOk()).andReturnJson()
                .path("content").path("itineraryId").asText();
    }

    private JsonNode getSnapshot(String access, String itineraryId) throws Exception {
        return performJson(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk()).andReturnJson();
    }

    private String login(String email) throws Exception {
        var response = mvc.perform(post("/web/identity/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("content").path("accessToken").asText();
    }

    private Result performJson(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String access,
            String body
    ) throws Exception {
        request.header("Authorization", "Bearer " + access);
        if (body != null) {
            request.contentType("application/json").content(body);
        }
        return new Result(mvc.perform(request));
    }

    private static String createBody(String commandId) {
        return """
                {"commandId":"%s","expectedVersion":0,"payload":{
                  "title":"IT-TEST-#17-杭州行程","startDate":"2026-09-01","endDate":"2026-09-02",
                  "timeZone":"Asia/Shanghai","baseCurrency":"CNY",
                  "destinations":[{"name":"杭州","countryCode":"CN","timeZone":"Asia/Shanghai"}]}}
                """.formatted(commandId);
    }

    private static String addItemBody(String commandId, long version, String dayId, String title) {
        return """
                {"commandId":"%s","expectedVersion":%d,
                 "payload":{"dayId":"%s","title":"%s"}}
                """.formatted(commandId, version, dayId, title);
    }

    private void cleanup() {
        jdbc.update("DELETE FROM itinerary_command");
        jdbc.update("DELETE FROM itinerary_item");
        jdbc.update("DELETE FROM itinerary_day");
        jdbc.update("DELETE FROM itinerary_destination");
        jdbc.update("DELETE FROM itinerary");
        jdbc.update("DELETE FROM identity_one_time_token");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM member_login_log");
        jdbc.update("DELETE FROM member WHERE name LIKE 'IT-TEST-#17-%'");
    }

    private final class Result {
        private final org.springframework.test.web.servlet.ResultActions actions;
        private Result(org.springframework.test.web.servlet.ResultActions actions) { this.actions = actions; }
        private Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }
        private JsonNode andReturnJson() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement
    @Import({
            IdentityConfiguration.class, IdentityController.class, IdentityExceptionHandler.class,
            ItineraryConfiguration.class, ItineraryController.class, ItineraryExceptionHandler.class
    })
    static class Config {
        @Bean
        @Primary
        IdentityMailGateway mailGateway() {
            return new IdentityMailGateway() {
                @Override public void sendVerificationLink(String email, URI link) { }
                @Override public void sendPasswordResetLink(String email, URI link) { }
            };
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
        }

        @Bean
        SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            factory.setMapperLocations(java.util.stream.Stream.concat(
                    java.util.Arrays.stream(resolver.getResources("classpath:mapper/identity/*.xml")),
                    java.util.Arrays.stream(resolver.getResources("classpath:mapper/itinerary/*.xml"))
            ).toArray(org.springframework.core.io.Resource[]::new));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
