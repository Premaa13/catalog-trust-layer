package com.catalogtrust;

import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.service.ProjectDataPaths;
import com.catalogtrust.service.TrustEngineService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Runs the batch trust engine when the Spring Boot application starts.
 */
@Component
public class TrustEngineCommandLineRunner implements ApplicationRunner {
    private final TrustEngineService trustEngineService;
    private final ProjectDataPaths paths;

    public TrustEngineCommandLineRunner(TrustEngineService trustEngineService, ProjectDataPaths paths) {
        this.trustEngineService = trustEngineService;
        this.paths = paths;
    }

    @Override
    public void run(ApplicationArguments args) {
        var catalogFile = paths.dataFile("catalog.csv");
        var ordersFile = paths.dataFile("orders.csv");
        var resultsFile = paths.dataFile("results.csv");

        try {
            List<TrustScoringResult> results =
                    trustEngineService.run(catalogFile, ordersFile, resultsFile);
            trustEngineService.printSummary(results, resultsFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Trust engine could not complete: " + exception.getMessage(), exception);
        }
    }

}
