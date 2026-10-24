package com.catalogtrust.service;

import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Finds project files from the application location, independently of the launch directory.
 */
@Component
public class ProjectDataPaths {
    private final Path projectRoot;

    public ProjectDataPaths() {
        this(applicationLocation(), Path.of("").toAbsolutePath(), System.getenv("CATALOG_TRUST_PROJECT_ROOT"));
    }

    ProjectDataPaths(Path applicationLocation, Path workingDirectory, String configuredProjectRoot) {
        this.projectRoot = resolveProjectRoot(applicationLocation, workingDirectory, configuredProjectRoot);
    }

    public Path dataFile(String fileName) {
        return projectRoot.resolve("data").resolve(fileName);
    }

    public Path auditLogFile() {
        return projectRoot.resolve("audit").resolve("audit_log.csv");
    }

    public Path projectFile(String relativePath) {
        return projectRoot.resolve(relativePath);
    }

    private Path resolveProjectRoot(
            Path applicationLocation,
            Path workingDirectory,
            String configuredProjectRoot
    ) {
        if (configuredProjectRoot != null && !configuredProjectRoot.isBlank()) {
            Path configured = Path.of(configuredProjectRoot).toAbsolutePath().normalize();
            if (containsRequiredDataFiles(configured)) {
                return configured;
            }
            throw new IllegalStateException(
                    "CATALOG_TRUST_PROJECT_ROOT does not contain data/catalog.csv and data/orders.csv: "
                            + configured
            );
        }

        for (Path startingDirectory : List.of(applicationLocation, workingDirectory)) {
            Path currentDirectory = Files.isDirectory(startingDirectory)
                    ? startingDirectory
                    : startingDirectory.getParent();
            while (currentDirectory != null) {
                if (containsRequiredDataFiles(currentDirectory)) {
                    return currentDirectory;
                }
                currentDirectory = currentDirectory.getParent();
            }
        }

        throw new IllegalStateException(
                "Could not locate data/catalog.csv and data/orders.csv from the application location "
                        + applicationLocation + " or working directory " + workingDirectory
        );
    }

    private boolean containsRequiredDataFiles(Path directory) {
        return Files.isRegularFile(directory.resolve("data/catalog.csv"))
                && Files.isRegularFile(directory.resolve("data/orders.csv"));
    }

    private static Path applicationLocation() {
        if (ProjectDataPaths.class.getProtectionDomain().getCodeSource() == null) {
            return Path.of("").toAbsolutePath();
        }
        try {
            return Path.of(ProjectDataPaths.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Unable to determine the Java application location", exception);
        }
    }
}
