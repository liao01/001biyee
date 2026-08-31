package com.jiawa.lyw.support;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.Map;

/** 测试专用隔离库；未指定外部测试 DSN 时，由 Testcontainers 管理本批 MySQL。 */
public final class MySqlIntegrationDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final String schema;
    private final MySQLContainer<?> container;

    public MySqlIntegrationDatabase() {
        String batch = UUID.randomUUID().toString().replace("-", "");
        schema = "lyw_identity_http_test_" + batch;
        String dsn = System.getenv("LYW_MIGRATION_TEST_DSN");
        if (dsn == null || dsn.isBlank()) {
            container = new MySQLContainer<>("mysql:8.4.6")
                    .withDatabaseName("lyw_test_bootstrap")
                    .withUsername("root").withPassword("change-me")
                    .withReuse(false)
                    .withLabel("lyw.purpose", "identity-integration-test")
                    .withLabel("lyw.test-batch", batch)
                    .withTmpFs(Map.of("/var/lib/mysql", "rw,size=512m"))
                    .withCopyFileToContainer(MountableFile.forHostPath(
                            Path.of("..", "deploy", "mysql", "low-memory.cnf")),
                            "/etc/mysql/conf.d/low-memory.cnf")
                    .withCreateContainerCmdModifier(command -> {
                        command.withName("lyw-identity-test-" + batch);
                        command.getHostConfig().withMemory(768L * 1024 * 1024)
                                .withNanoCPUs(1_500_000_000L)
                                .withPortBindings(new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(3306)));
                    });
            try {
                container.start();
                serverUrl = "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(3306) + "/";
                username = container.getUsername();
                password = container.getPassword();
            } catch (RuntimeException failure) {
                container.close();
                throw new IllegalStateException("Cannot start isolated Testcontainers MySQL; check the local Docker engine", failure);
            }
        } else {
            container = null;
            URI uri;
            try {
                uri = URI.create(dsn);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalStateException("Invalid MySQL integration DSN");
            }
            if (!"mysql".equals(uri.getScheme())
                    || !("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                    || uri.getUserInfo() == null || !uri.getUserInfo().contains(":")) {
                throw new IllegalStateException("MySQL integration tests only allow authenticated loopback connections");
            }
            String[] credentials = uri.getUserInfo().split(":", 2);
            username = credentials[0];
            password = credentials[1];
            int port = uri.getPort() < 0 ? 3306 : uri.getPort();
            serverUrl = "jdbc:mysql://" + uri.getHost() + ":" + port + "/";
        }
        try (Connection connection = open("mysql")) {
            connection.createStatement().execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException ignored) {
            if (container != null) container.close();
            throw new IllegalStateException("Cannot create isolated MySQL integration database");
        }
        try (Connection connection = open(schema)) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(Path.of("..", "sql", "travel_share.sql")));
        } catch (Exception ignored) {
            close();
            throw new IllegalStateException("Cannot initialize isolated MySQL integration schema");
        }
    }

    public String jdbcUrl() {
        return jdbcUrl(schema);
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    private String jdbcUrl(String database) {
        return serverUrl + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private Connection open(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), username, password);
    }

    @Override
    public void close() {
        if (!schema.matches("lyw_identity_http_test_[a-f0-9]{32}")) {
            throw new IllegalStateException("Refusing to drop a database outside the integration test scope");
        }
        try (Connection connection = open("mysql")) {
            connection.createStatement().execute("DROP DATABASE IF EXISTS `" + schema + "`");
            try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?")) {
                statement.setString(1, schema);
                try (var result = statement.executeQuery()) {
                    result.next();
                    if (result.getInt(1) != 0) {
                        throw new IllegalStateException("Isolated test database still exists: " + schema);
                    }
                }
            }
        } catch (SQLException ignored) {
            throw new IllegalStateException("Isolated test database cleanup failed: " + schema);
        } finally {
            if (container != null) container.close();
        }
    }
}
