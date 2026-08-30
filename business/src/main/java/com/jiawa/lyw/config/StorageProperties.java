package com.jiawa.lyw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private Path uploadDir = Path.of("D:/idea/lyw/uploads");

    public Path getUploadDir() {
        return uploadDir.toAbsolutePath().normalize();
    }

    public void setUploadDir(Path uploadDir) {
        this.uploadDir = uploadDir;
    }

    public Path postsDir() {
        return getUploadDir();
    }

    public Path avatarsDir() {
        return getUploadDir().resolve("avatar");
    }

    public Path locationsDir() {
        return getUploadDir().resolve("location");
    }

    public URI resourceLocation() {
        return getUploadDir().toUri();
    }
}
