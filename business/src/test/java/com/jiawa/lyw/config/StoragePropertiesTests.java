package com.jiawa.lyw.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoragePropertiesTests {

    @TempDir
    Path tempDir;

    @Test
    void derivesAllUploadLocationsFromOneRoot() {
        StorageProperties properties = new StorageProperties();
        Path uploadRoot = tempDir.resolve("uploads");

        properties.setUploadDir(uploadRoot);

        assertThat(properties.postsDir()).isEqualTo(uploadRoot);
        assertThat(properties.avatarsDir()).isEqualTo(uploadRoot.resolve("avatar"));
        assertThat(properties.locationsDir()).isEqualTo(uploadRoot.resolve("location"));
        assertThat(properties.resourceLocation()).isEqualTo(uploadRoot.toUri());
    }
}
