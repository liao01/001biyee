package com.jiawa.lyw.support;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** 测试专用隔离库；凭据只从环境读取，异常不携带连接信息。 */
public final class MySqlIntegrationDatabase implements AutoCloseable {
    private final String serverUrl;
    private final String username;
    private final String password;
    private final String schema;

    public MySqlIntegrationDatabase() {
        String dsn = System.getenv("LYW_MIGRATION_TEST_DSN");
        if (dsn == null || dsn.isBlank()) {
            throw new IllegalStateException("MySQL integration tests require LYW_MIGRATION_TEST_DSN");
        }
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
        schema = "lyw_identity_http_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = open("mysql")) {
            connection.createStatement().execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException ignored) {
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
        }
    }
}
