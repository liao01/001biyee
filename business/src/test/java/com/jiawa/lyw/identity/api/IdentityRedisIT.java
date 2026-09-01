package com.jiawa.lyw.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.jiawa.lyw.BusinessApplication;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 完整应用的 HTTP 行为；所有数据组件隔离，只有邮件在供应商边界替换。 */
@EnabledIfEnvironmentVariable(named = "LYW_INTEGRATION_CONTAINERS", matches = "true",
        disabledReason = "Requires explicit --containers mode; CI enables the complete container suite")
class IdentityRedisIT {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void repeatedAuthenticatedVisitsCountOnceAndACacheStallDoesNotBlockIdentity() throws Exception {
        String batch = UUID.randomUUID().toString().replace("-", "");
        Path directory = Files.createTempDirectory("lyw-identity-redis-test-");
        String originalCatalinaBase = System.getProperty("catalina.base");
        String originalCatalinaHome = System.getProperty("catalina.home");
        try (var database = new MySqlIntegrationDatabase();
             var redis = container("redis:7.4.5-alpine", "redis", batch, 6379, 96)
                     .withCommand("redis-server", "--save", "", "--appendonly", "no", "--requirepass", "change-me")
                     .withTmpFs(Map.of("/data", "rw,size=32m"));
             var mongo = container("mongo:7.0.23", "mongo", batch, 27017, 384)
                     .withCommand("mongod", "--wiredTigerCacheSizeGB", "0.25", "--bind_ip_all")
                     .withTmpFs(Map.of("/data/db", "rw,size=256m", "/data/configdb", "rw,size=16m"))) {
            redis.start();
            mongo.start();
            // 隔离应用配置来源；宿主机的 Spring 环境变量或 JVM 属性不能覆盖测试数据组件。
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            // Tomcat 绕过 Spring 读取并改写这两个 JVM 属性，必须限定到本批目录并恢复。
            System.setProperty("catalina.base", directory.toString());
            System.setProperty("catalina.home", directory.toString());
            try (var app = new SpringApplicationBuilder(BusinessApplication.class, MailConfig.class).environment(environment).run(
                    "--spring.config.location=classpath:application-prod.properties", "--spring.profiles.active=prod",
                    "--logging.config=classpath:logback-integration.xml",
                    "--server.address=127.0.0.1", "--server.port=0", "--server.tomcat.basedir=" + directory,
                    "--UPLOAD_DIR=" + directory.resolve("uploads"),
                    "--DB_URL=" + database.jdbcUrl(), "--DB_USERNAME=" + database.username(), "--DB_PASSWORD=" + database.password(),
                    "--REDIS_HOST=" + redis.getHost(), "--REDIS_PORT=" + redis.getMappedPort(6379), "--REDIS_PASSWORD=change-me",
                    "--MONGODB_URI=mongodb://" + mongo.getHost() + ":" + mongo.getMappedPort(27017) + "/lyw_test_chat",
                    "--DEEPSEEK_BASE_URL=http://127.0.0.1:1", "--DEEPSEEK_API_KEY=change-me", "--DASHSCOPE_API_KEY=change-me",
                    "--MAIL_USERNAME=integration@example.invalid", "--MAIL_AUTH_CODE=change-me",
                    "--JWT_SECRET=isolated-redis-http-test-signing-secret-at-least-32-bytes",
                    "--APP_PUBLIC_URL=https://travel.example.test/travel",
                    "--RAG_URL=http://127.0.0.1:1", "--RAG_WORKSPACE_SLUG=lyw_test", "--RAG_API_KEY=change-me", "--AMAP_API_KEY=change-me")) {
                int port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) app).getWebServer().getPort();
                URI base = URI.create("http://127.0.0.1:" + port + "/lyw/");
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                var categories = client.send(HttpRequest.newBuilder(base.resolve("web/post/categories"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, categories.statusCode());
                assertEquals(java.util.List.of("城市漫游", "自然风光", "美食"),
                        json.readTree(categories.body()).path("content").findValuesAsText("name"),
                        "正式种子的分类文字不能被宿主机默认编码改写");
                assertDau(client, base, 0);
                String credentials = "{\"email\":\"redis-test@example.com\",\"password\":\"Test-password-123\"}";
                assertEquals(200, post(client, base.resolve("web/identity/register"), credentials).statusCode());
                URI verification = app.getBean(Mailbox.class).verification;
                assertNotNull(verification);
                assertEquals(200, post(client, base.resolve("web/identity/verify-email"),
                        json.writeValueAsString(Map.of("token", verification.getQuery().substring(6)))).statusCode());
                var login = post(client, base.resolve("web/identity/login"), credentials);
                assertEquals(200, login.statusCode());
                String access = json.readTree(login.body()).path("content").path("accessToken").asText();
                assertDau(client, base, 0);
                for (int visit = 0; visit < 2; visit++) {
                    assertEquals(200, me(client, base, access).statusCode());
                }
                assertDau(client, base, 1);

                // 暂停仅本批 Redis，保留已建立的 TCP 连接，复现黑洞而非立即拒绝连接。
                redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
                try {
                    assertEquals(200, me(client, base, access).statusCode());
                } finally {
                    redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
                }
                assertEquals(200, me(client, base, access).statusCode());
                assertDau(client, base, 1);
            }
        } finally {
            restoreProperty("catalina.base", originalCatalinaBase);
            restoreProperty("catalina.home", originalCatalinaHome);
            if (!directory.getFileName().toString().startsWith("lyw-identity-redis-test-")) {
                throw new IllegalStateException("Unexpected test directory");
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private void restoreProperty(String name, String original) {
        if (original == null) System.clearProperty(name);
        else System.setProperty(name, original);
    }

    private GenericContainer<?> container(String image, String component, String batch, int port, int memoryMb) {
        return new GenericContainer<>(image).withExposedPorts(port).withReuse(false)
                .withLabel("lyw.purpose", "identity-redis-test").withLabel("lyw.test-batch", batch)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                .withCreateContainerCmdModifier(command -> {
                    command.withName("lyw-identity-redis-test-" + batch + "-" + component);
                    command.getHostConfig().withMemory(memoryMb * 1024L * 1024)
                            .withNanoCPUs(1_000_000_000L)
                            .withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(port)));
                });
    }

    private HttpResponse<String> post(HttpClient client, URI uri, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> me(HttpClient client, URI base, String access) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve("web/identity/me")).timeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertDau(HttpClient client, URI base, long expected) throws Exception {
        var result = client.send(HttpRequest.newBuilder(base.resolve("admin/report/dau")).timeout(Duration.ofSeconds(5))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode());
        assertEquals(expected, json.readTree(result.body()).path("content").path("dau").asLong(-1));
    }

    @TestConfiguration
    static class MailConfig {
        @Bean @Primary Mailbox mailbox() { return new Mailbox(); }
    }

    static class Mailbox implements IdentityMailGateway {
        volatile URI verification;
        @Override public void sendVerificationLink(String email, URI link) { verification = link; }
        @Override public void sendPasswordResetLink(String email, URI link) {
            throw new AssertionError("This scenario must not request a password reset");
        }
    }
}
