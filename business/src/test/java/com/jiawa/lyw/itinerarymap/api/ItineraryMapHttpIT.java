package com.jiawa.lyw.itinerarymap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.jiawa.lyw.identity.api.IdentityController;
import com.jiawa.lyw.identity.api.IdentityExceptionHandler;
import com.jiawa.lyw.identity.infrastructure.IdentityConfiguration;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.itinerary.api.ItineraryController;
import com.jiawa.lyw.itinerary.api.ItineraryExceptionHandler;
import com.jiawa.lyw.itinerary.infrastructure.ItineraryConfiguration;
import com.jiawa.lyw.itinerarymap.infrastructure.ItineraryMapConfiguration;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整 HTTP 验收：数据库、Redis 和高德替身均为本测试批次专用，退出后逐一删除并回查。
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = ItineraryMapHttpIT.Config.class)
@TestPropertySource(properties = {
        "jwt.secret=itinerary-map-integration-signing-key-at-least-32-bytes",
        "app.public-url=https://travel.example.test/travel",
        "identity.secure-cookie=false"
})
class ItineraryMapHttpIT {
    private static final String TEST_PREFIX = "IT-TEST-#19-MAP-";
    private static final String REDIS_PASSWORD = "change-me";
    private static final AmapStub AMAP = new AmapStub();
    private static final MySqlIntegrationDatabase DATABASE = new MySqlIntegrationDatabase();
    private static final GenericContainer<?> REDIS = redisContainer();

    static {
        REDIS.start();
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private StringRedisTemplate redis;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private ObjectMapper json;

    @DynamicPropertySource
    static void mapProperties(DynamicPropertyRegistry properties) {
        properties.add("app.map.amap.base-url", AMAP::baseUrl);
        properties.add("app.map.amap.api-key", () -> "TEST-amap-web-service-key");
        properties.add("app.map.amap.connect-timeout", () -> "1s");
        properties.add("app.map.amap.response-timeout", () -> "1s");
        properties.add("app.map.amap.route-concurrency", () -> "4");
        properties.add("app.map.amap.max-response-bytes", () -> "524288");
    }

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc = new JdbcTemplate(dataSource);
        json = new ObjectMapper().findAndRegisterModules();
        AMAP.reset();
        clearRows();
        clearRoutes();
        String legacyHash = "b406cd63d1530b73" + "464838f07947ccca";
        jdbc.update("INSERT INTO member "
                        + "(id, email, email_verified_at, password_hash, password_algorithm, account_status, password, name) "
                        + "VALUES (42, 'map-owner@example.com', CURRENT_TIMESTAMP(3), ?, 'LEGACY_DOUBLE_MD5', 'ACTIVE', ?, ?)",
                legacyHash, legacyHash, TEST_PREFIX + "owner");
    }

    @AfterEach
    void cleanupAndVerify() {
        clearRoutes();
        clearRows();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM itinerary", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE name LIKE ?", Integer.class,
                TEST_PREFIX + "%"));
        assertTrue(routeKeys().isEmpty(), "路线缓存测试数据必须清理");
    }

    @AfterAll
    static void closeIsolatedResources() {
        try {
            AMAP.close();
        } finally {
            try {
                REDIS.close();
            } finally {
                DATABASE.close();
            }
        }
    }

    @Test
    void performsSearchConfirmationRoutesCacheDegradationAndConcurrentReplay() throws Exception {
        String access = login();
        String itineraryId = create(access);
        String dayId = snapshot(access, itineraryId).path("content").path("days").get(0).path("id").asText();
        String firstItemId = addItem(access, itineraryId, dayId, "故宫安排", 1);
        String secondItemId = addItem(access, itineraryId, dayId, "手工地点安排", 2);

        JsonNode places = perform(get("/web/places/search").param("keyword", "故宫"), access, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.items[0].source").value("AMAP"))
                .andExpect(jsonPath("$.content.items[0].sourcePlaceId").value("B000A"))
                .andReturnJson();
        assertEquals(1, AMAP.searchCalls());

        perform(put("/web/itineraries/{id}/items/{itemId}/place", itineraryId, firstItemId), access,
                placeBody("00000000-0000-0000-0000-000000000701", 3, "AMAP", "B000A", "故宫博物院"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.version").value(4));
        assertEquals("故宫博物院", snapshot(access, itineraryId)
                .path("content").path("days").get(0).path("items").get(0)
                .path("placeReference").path("name").asText());

        AMAP.failAll(true);
        perform(put("/web/itineraries/{id}/items/{itemId}/place", itineraryId, secondItemId), access,
                placeBody("00000000-0000-0000-0000-000000000702", 4, "MANUAL", null, "手工地点"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.version").value(5));
        assertEquals(1, AMAP.searchCalls(), "手工地点保存不得调用供应商");

        AMAP.failAll(false);
        String replaceCommand = "00000000-0000-0000-0000-000000000703";
        List<JsonNode> concurrent = concurrently(
                () -> perform(put("/web/itineraries/{id}/items/{itemId}/place", itineraryId, firstItemId), access,
                        placeBody(replaceCommand, 5, "AMAP", "B000A", "故宫博物院")).andReturnJson(),
                () -> perform(put("/web/itineraries/{id}/items/{itemId}/place", itineraryId, firstItemId), access,
                        placeBody(replaceCommand, 5, "AMAP", "B000A", "故宫博物院")).andReturnJson()
        );
        assertEquals(Set.of(6L), concurrent.stream().map(node -> node.path("content").path("version").asLong()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM itinerary_command WHERE command_id = ?", Integer.class, replaceCommand));

        daily(access, itineraryId, dayId, "WALKING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.legs[0].status").value("LIVE"))
                .andExpect(jsonPath("$.content.legs[0].fromItemId").value(firstItemId))
                .andExpect(jsonPath("$.content.legs[0].toItemId").value(secondItemId));
        int liveCalls = AMAP.routeCalls();
        daily(access, itineraryId, dayId, "WALKING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.legs[0].status").value("CACHED"));
        assertEquals(liveCalls, AMAP.routeCalls(), "新鲜路线缓存不得二次调用供应商");

        String walkingKey = routeKeys().stream().findFirst().orElseThrow();
        redis.opsForValue().set(walkingKey, """
                {"route":{"distanceMeters":800,"durationSeconds":600,"polyline":[],
                "calculatedAt":"2026-08-31T00:00:00Z"},"freshUntil":"2026-09-01T00:00:00Z"}
                """);
        AMAP.failAll(true);
        daily(access, itineraryId, dayId, "WALKING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.degraded").value(true))
                .andExpect(jsonPath("$.content.legs[0].status").value("CACHED"))
                .andExpect(jsonPath("$.content.legs[0].stale").value(true));

        redis.delete(walkingKey);
        daily(access, itineraryId, dayId, "WALKING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.degraded").value(true))
                .andExpect(jsonPath("$.content.legs[0].status").value("UNAVAILABLE"));

        AMAP.failAll(false);
        daily(access, itineraryId, dayId, "DRIVING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.mode").value("DRIVING"))
                .andExpect(jsonPath("$.content.legs[0].status").value("LIVE"));
        daily(access, itineraryId, dayId, "TRANSIT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.mode").value("TRANSIT"))
                .andExpect(jsonPath("$.content.legs[0].status").value("LIVE"));
        assertTrue(AMAP.routeCalls() >= liveCalls + 4, "三种模式与失败重试路径均应命中本地替身");
        assertFalse(places.path("content").path("items").isMissingNode());
    }

    private List<JsonNode> concurrently(Callable<JsonNode> first, Callable<JsonNode> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<JsonNode>> futures = executor.invokeAll(List.of(first, second));
            List<JsonNode> results = new ArrayList<>();
            for (Future<JsonNode> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private Result daily(String access, String itineraryId, String dayId, String mode) throws Exception {
        return perform(get("/web/itineraries/{id}/days/{dayId}/map", itineraryId, dayId).param("mode", mode), access, null);
    }

    private String login() throws Exception {
        var response = mvc.perform(post("/web/identity/login").contentType("application/json")
                        .content("{\"email\":\"map-owner@example.com\",\"password\":\"Test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("content").path("accessToken").asText();
    }

    private String create(String access) throws Exception {
        return perform(post("/web/itineraries"), access, """
                {"commandId":"00000000-0000-0000-0000-000000000704","expectedVersion":0,"payload":{
                "title":"IT-TEST-#19-MAP-北京行程","startDate":"2026-09-01","endDate":"2026-09-01",
                "timeZone":"Asia/Shanghai","baseCurrency":"CNY",
                "destinations":[{"name":"北京","countryCode":"CN","timeZone":"Asia/Shanghai"}]}}
                """).andExpect(status().isOk()).andReturnJson().path("content").path("itineraryId").asText();
    }

    private String addItem(String access, String itineraryId, String dayId, String title, int sequence) throws Exception {
        return perform(post("/web/itineraries/{id}/items", itineraryId), access, """
                {"commandId":"00000000-0000-0000-0000-0000000007%02d","expectedVersion":%d,
                "payload":{"dayId":"%s","title":"%s"}}
                """.formatted(10 + sequence, sequence, dayId, title))
                .andExpect(status().isOk()).andReturnJson().path("content").path("itemId").asText();
    }

    private JsonNode snapshot(String access, String itineraryId) throws Exception {
        return perform(get("/web/itineraries/{id}", itineraryId), access, null)
                .andExpect(status().isOk()).andReturnJson();
    }

    private Result perform(
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

    private String placeBody(String commandId, long expectedVersion, String source, String sourcePlaceId, String name) {
        String sourceId = sourcePlaceId == null ? "null" : "\"" + sourcePlaceId + "\"";
        return """
                {"commandId":"%s","expectedVersion":%d,"place":{
                "source":"%s","sourcePlaceId":%s,"name":"%s","address":"测试地点",
                "administrativeAreaCode":"110101","longitude":116.397026,"latitude":39.918058,
                "coordinateSystem":"GCJ02"}}
                """.formatted(commandId, expectedVersion, source, sourceId, name);
    }

    private void clearRows() {
        if (jdbc == null) {
            return;
        }
        jdbc.update("DELETE FROM itinerary_command");
        jdbc.update("DELETE FROM itinerary_item_place");
        jdbc.update("DELETE FROM itinerary_item");
        jdbc.update("DELETE FROM itinerary_day");
        jdbc.update("DELETE FROM itinerary_destination");
        jdbc.update("DELETE FROM itinerary");
        jdbc.update("DELETE FROM identity_one_time_token");
        jdbc.update("DELETE FROM identity_refresh_session");
        jdbc.update("DELETE FROM member_login_log");
        jdbc.update("DELETE FROM member WHERE name LIKE ?", TEST_PREFIX + "%");
    }

    private Set<String> routeKeys() {
        Set<String> keys = redis.keys("itinerary-map:route:v1:*");
        return keys == null ? Set.of() : keys;
    }

    private void clearRoutes() {
        Set<String> keys = routeKeys();
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private final class Result {
        private final org.springframework.test.web.servlet.ResultActions actions;

        private Result(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        private Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        private JsonNode andReturnJson() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }

    private static GenericContainer<?> redisContainer() {
        String batch = UUID.randomUUID().toString().replace("-", "");
        return new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379).withReuse(false)
                .withLabel("lyw.purpose", "itinerary-map-http-test")
                .withLabel("lyw.test-batch", batch)
                .withCommand("redis-server", "--save", "", "--appendonly", "no", "--requirepass", REDIS_PASSWORD)
                .withTmpFs(Map.of("/data", "rw,size=32m"))
                .waitingFor(Wait.forListeningPort())
                .withCreateContainerCmdModifier(command -> {
                    command.withName("lyw-itinerary-map-http-test-" + batch);
                    command.getHostConfig().withMemory(96L * 1024 * 1024).withNanoCPUs(1_000_000_000L)
                            .withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(6379)));
                });
    }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement
    @Import({
            IdentityConfiguration.class, IdentityController.class, IdentityExceptionHandler.class,
            ItineraryConfiguration.class, ItineraryController.class, ItineraryExceptionHandler.class,
            ItineraryMapConfiguration.class, ItineraryMapController.class, ItineraryMapExceptionHandler.class
    })
    static class Config {
        @Bean
        @Primary
        IdentityMailGateway mailGateway() {
            return new IdentityMailGateway() {
                @Override public void sendVerificationLink(String email, java.net.URI link) { }
                @Override public void sendPasswordResetLink(String email, java.net.URI link) { }
            };
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory lettuceConnectionFactory() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    REDIS.getHost(), REDIS.getMappedPort(6379)
            );
            configuration.setPassword(RedisPassword.of(REDIS_PASSWORD));
            return new LettuceConnectionFactory(configuration);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
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

    private static final class AmapStub implements AutoCloseable {
        private final HttpServer server;
        private volatile boolean failAll;
        private int searchCalls;
        private int routeCalls;

        private AmapStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/", this::respond);
                server.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot start local Amap test substitute", exception);
            }
        }

        private synchronized void reset() {
            failAll = false;
            searchCalls = 0;
            routeCalls = 0;
        }

        private synchronized int searchCalls() {
            return searchCalls;
        }

        private synchronized int routeCalls() {
            return routeCalls;
        }

        private void failAll(boolean value) {
            failAll = value;
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void respond(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/v3/place/text".equals(path)) {
                    synchronized (this) {
                        searchCalls++;
                    }
                    if (failAll) {
                        write(exchange, 503, "provider temporarily unavailable");
                    } else {
                        write(exchange, 200, """
                            {"status":"1","pois":[{"id":"B000A","name":"故宫博物院","address":"景山前街4号",
                            "adcode":"110101","location":"116.397026,39.918058","pname":"北京市","adname":"东城区"}]}
                            """);
                    }
                } else if (path.startsWith("/v5/direction/")) {
                    synchronized (this) {
                        routeCalls++;
                    }
                    if (failAll) {
                        write(exchange, 503, "provider temporarily unavailable");
                    } else {
                        write(exchange, 200, """
                                {"status":"1","route":{"paths":[{"distance":"800","cost":{"duration":"600"},
                                "steps":[{"polyline":"116.397026,39.918058;116.407026,39.928058"}]}]}}
                                """);
                    }
                } else {
                    write(exchange, 404, "not found");
                }
            } finally {
                exchange.close();
            }
        }

        private static void write(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
