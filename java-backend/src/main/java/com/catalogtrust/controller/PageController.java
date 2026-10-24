package com.catalogtrust.controller;

import com.catalogtrust.service.CatalogApiService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
public class PageController {
    private final CatalogApiService catalogApiService;

    public PageController(CatalogApiService catalogApiService) {
        this.catalogApiService = catalogApiService;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> dashboard() {
        return htmlPage(catalogApiService.dashboardFile(), "Dashboard not found");
    }

    @GetMapping(value = "/shop", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> storefront() {
        return htmlPage(catalogApiService.storefrontFile(), "Storefront not found");
    }

    private ResponseEntity<?> htmlPage(Path file, String error) {
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.ok(Map.of("error", error));
        }
        Resource page = new FileSystemResource(file);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page);
    }
}
