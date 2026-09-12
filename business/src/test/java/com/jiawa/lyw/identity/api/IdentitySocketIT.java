package com.jiawa.lyw.identity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.Util.JwtUtil;
import com.jiawa.lyw.aspect.LogAspect;
import com.jiawa.lyw.config.CorsConfig;
import com.jiawa.lyw.config.SpringMvcConfig;
import com.jiawa.lyw.identity.infrastructure.IdentityConfiguration;
import com.jiawa.lyw.identity.infrastructure.IdentityMailGateway;
import com.jiawa.lyw.interceptor.AdminLoginInterceptor;
import com.jiawa.lyw.interceptor.LogInterceptor;
import com.jiawa.lyw.interceptor.WebLoginInterceptor;
import com.jiawa.lyw.support.MySqlIntegrationDatabase;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 真实回环 HTTP 与 Cookie 传输；不加载本机业务配置，不发送真实邮件。 */
class IdentitySocketIT {
    @Test
    void registeredAccountCanVerifyLoginRotateLogoutAndResetOverRealHttp() throws Exception {
        Path directory = Files.createTempDirectory("lyw-identity-socket-test-");
        try (var database = new MySqlIntegrationDatabase();
             var context = new AnnotationConfigServletWebServerApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("identity-test", Map.of(
                    "jwt.secret", "socket-test-signing-secret-at-least-32-bytes",
                    "app.public-url", "http://127.0.0.1:5173/travel", "identity.secure-cookie", "false")));
            context.registerBean(DataSource.class, () -> new DriverManagerDataSource(database.jdbcUrl(), database.username(), database.password()));
            context.registerBean(TomcatServletWebServerFactory.class, () -> {
                var factory = new TomcatServletWebServerFactory(0);
                try { factory.setAddress(InetAddress.getByName("127.0.0.1")); }
                catch (Exception failure) { throw new IllegalStateException("Cannot bind loopback test server"); }
                factory.setContextPath("/lyw");
                factory.setBaseDirectory(directory.toFile());
                return factory;
            });
            context.register(Config.class);
            context.refresh();
            URI base = URI.create("http://127.0.0.1:" + context.getWebServer().getPort() + "/lyw/web/identity/");
            var mailbox = context.getBean(Mailbox.class);
            var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            var client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
            var json = new ObjectMapper();
            String credentials = "{\"email\":\"socket-test@example.com\",\"password\":\"Test-password-123\"}";
            assertEquals(200, post(client, base.resolve("register"), credentials).statusCode());
            assertEquals(403, post(client, base.resolve("login"), credentials).statusCode());
            assertNotNull(mailbox.verification);
            String verifyBody = json.writeValueAsString(Map.of("token", mailbox.verification.getQuery().substring(6)));
            assertEquals(200, post(client, base.resolve("verify-email"), verifyBody).statusCode());
            var login = post(client, base.resolve("login"), credentials);
            assertEquals(200, login.statusCode());
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(cookie -> cookie.isHttpOnly() && cookie.getName().equals("refresh_token")));
            var foreign = client.send(HttpRequest.newBuilder(base.resolve("refresh"))
                    .header("Origin", "https://untrusted.example.test").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, foreign.statusCode());
            String access = json.readTree(login.body()).path("content").path("accessToken").asText();
            var me = client.send(HttpRequest.newBuilder(base.resolve("me")).header("Authorization", "Bearer " + access).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, me.statusCode());
            assertFalse(json.readTree(me.body()).path("content").path("id").asText().isBlank());
            assertEquals(200, post(client, base.resolve("refresh"), "{}").statusCode());
            assertEquals(200, post(client, base.resolve("logout"), "{}").statusCode());
            assertTrue(cookies.getCookieStore().getCookies().isEmpty());
            assertEquals(401, post(client, base.resolve("refresh"), "{}").statusCode());
            assertEquals(200, post(client, base.resolve("login"), credentials).statusCode());
            assertEquals(200, post(client, base.resolve("request-password-reset"), "{\"email\":\"socket-test@example.com\"}").statusCode());
            String reset = json.writeValueAsString(Map.of("token", mailbox.reset.getQuery().substring(6), "newPassword", "Test-new-password-456"));
            assertEquals(200, post(client, base.resolve("reset-password"), reset).statusCode());
            assertEquals(401, post(client, base.resolve("refresh"), "{}").statusCode());
            assertEquals(401, post(client, base.resolve("login"), credentials).statusCode());
            assertEquals(200, post(client, base.resolve("login"), credentials.replace("Test-password-123", "Test-new-password-456")).statusCode());
            var command = java.util.List.of("node", "node_modules/vitest/vitest.mjs", "run", "--config", "vitest.identity-runtime.config.js");
            var process = new ProcessBuilder(command).directory(Path.of("..", "web").toFile()).redirectErrorStream(true);
            process.environment().remove("LYW_MIGRATION_TEST_DSN");
            process.environment().put("LYW_IDENTITY_RUNTIME_BASE", "http://127.0.0.1:" + context.getWebServer().getPort());
            Process frontend = process.start();
            var outputReader = java.util.concurrent.Executors.newSingleThreadExecutor();
            var output = outputReader.submit(() -> {
                try (var reader = frontend.inputReader(java.nio.charset.StandardCharsets.UTF_8)) {
                    reader.lines().forEach(System.out::println);
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            try {
                assertTrue(frontend.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "Frontend runtime integration timed out");
                output.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(0, frontend.exitValue(), "Frontend runtime integration failed");
            } finally {
                if (frontend.isAlive()) {
                    frontend.descendants().forEach(ProcessHandle::destroyForcibly);
                    frontend.destroyForcibly();
                }
                outputReader.shutdownNow();
                assertTrue(outputReader.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
            }
        } finally {
            if (!directory.getFileName().toString().startsWith("lyw-identity-socket-test-")) throw new IllegalStateException("Unexpected test directory");
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private HttpResponse<String> post(HttpClient client, URI uri, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration @EnableWebMvc @EnableTransactionManagement @EnableAspectJAutoProxy
    @Import({IdentityConfiguration.class, IdentityController.class, IdentityExceptionHandler.class, CorsConfig.class,
            SpringMvcConfig.class, WebLoginInterceptor.class, AdminLoginInterceptor.class, LogInterceptor.class, LogAspect.class, JwtUtil.class, MailboxController.class})
    static class Config {
        @Bean ServletRegistrationBean<DispatcherServlet> dispatcher(org.springframework.web.context.WebApplicationContext context) {
            return new ServletRegistrationBean<>(new DispatcherServlet(context), "/");
        }
        @Bean Mailbox mailbox() { return new Mailbox(); }
        @Bean SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/identity/*.xml"));
            return factory;
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
    }

    static class Mailbox implements IdentityMailGateway {
        volatile URI verification;
        volatile URI reset;
        public void sendVerificationLink(String email, URI link) { verification = link; }
        public void sendPasswordResetLink(String email, URI link) { reset = link; }
    }

    @org.springframework.web.bind.annotation.RestController
    static class MailboxController {
        private final Mailbox mailbox;
        MailboxController(Mailbox mailbox) { this.mailbox = mailbox; }
        @org.springframework.web.bind.annotation.GetMapping("/_test/identity-mailbox")
        Map<String, String> read() {
            return Map.of("verification", mailbox.verification == null ? "" : mailbox.verification.toString(),
                    "reset", mailbox.reset == null ? "" : mailbox.reset.toString());
        }
    }
}
