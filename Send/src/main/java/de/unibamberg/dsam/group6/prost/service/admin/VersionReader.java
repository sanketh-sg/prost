package de.unibamberg.dsam.group6.prost.service.admin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

@Service
public class VersionReader {
    @Value("classpath:version")
    private Resource versionFile;

    @Value("${spring.profiles.active}")
    private List<String> activeProfiles;

    public String getVersion() {
        if (this.activeProfiles.contains("dev")) {
            return "dev";
        }

        if (this.versionFile == null || !this.versionFile.exists()) {
            return "unknown";
        }

        try (final var reader = new InputStreamReader(this.versionFile.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException ignore) {
        }

        return "unknown";
    }
}
