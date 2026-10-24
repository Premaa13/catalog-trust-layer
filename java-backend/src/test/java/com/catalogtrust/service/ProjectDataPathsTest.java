package com.catalogtrust.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDataPathsTest {
    @TempDir
    Path unrelatedWorkingDirectory;

    @Test
    void findsProjectFilesFromApplicationLocationRatherThanWorkingDirectory() throws URISyntaxException {
        Path applicationLocation = Path.of(getClass().getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        ProjectDataPaths paths = new ProjectDataPaths(applicationLocation, unrelatedWorkingDirectory, null);

        assertTrue(paths.dataFile("catalog.csv").toFile().isFile());
        assertTrue(paths.dataFile("orders.csv").toFile().isFile());
        assertEquals(paths.dataFile("results.csv"), paths.projectFile("data/results.csv"));
    }

    @Test
    void rejectsAnExplicitProjectRootWithoutTheRequiredCsvFiles() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ProjectDataPaths(
                        unrelatedWorkingDirectory,
                        unrelatedWorkingDirectory,
                        unrelatedWorkingDirectory.toString()
                )
        );

        assertTrue(exception.getMessage().contains("CATALOG_TRUST_PROJECT_ROOT"));
    }
}
