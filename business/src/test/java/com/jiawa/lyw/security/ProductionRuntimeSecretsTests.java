package com.jiawa.lyw.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRuntimeSecretsTests {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionConfigurationUsesOnlyTheDifyItinerarySecretContract() throws IOException {
        String production = Files.readString(REPOSITORY.resolve(
                "business/src/main/resources/application-prod.properties"));
        String environment = Files.readString(REPOSITORY.resolve(".env.example"));

        for (String legacy : List.of("RAG_URL", "RAG_WORKSPACE_SLUG", "RAG_API_KEY")) {
            assertFalse(production.contains(legacy), legacy);
            assertFalse(environment.contains(legacy), legacy);
        }
        for (String required : List.of(
                "DIFY_ITINERARY_BASE_URL",
                "DIFY_ITINERARY_API_KEY",
                "DIFY_ITINERARY_USER_HASH_KEY"
        )) {
            assertTrue(production.contains(required), required);
            assertTrue(environment.contains(required), required);
        }
    }

    @Test
    void legacyAnythingLlmWriteEndpointAndLiteralCredentialsAreAbsent() throws IOException {
        Path sourceRoot = REPOSITORY.resolve("business/src/main/java");
        Path legacyController = sourceRoot.resolve(
                "com/jiawa/lyw/controller/ai/CustomerServiceController.java");
        assertFalse(Files.exists(legacyController), "旧客服写入口必须删除");
        assertFalse(Files.exists(REPOSITORY.resolve("web/src/view/page/ai.vue")),
                "调用旧客服写入口的未使用页面必须删除");

        try (var paths = Files.walk(sourceRoot)) {
            String sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .map(ProductionRuntimeSecretsTests::read)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertFalse(sources.contains("localhost:3001/api/v1/workspace"));
            assertFalse(sources.contains("/web/customerService/message"));
            assertFalse(sources.matches("(?s).*Authorization.{0,80}Bearer [A-Za-z0-9_-]{20,}.*"));
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
