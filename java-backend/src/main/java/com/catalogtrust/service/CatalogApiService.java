package com.catalogtrust.service;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.ProductApiResponse;
import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.repository.AuditLogCsvRepository;
import com.catalogtrust.repository.CatalogCsvRepository;
import com.catalogtrust.repository.OrderCsvRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the product and audit data consumed by the dashboard and storefront APIs.
 */
@Service
public class CatalogApiService {
    private static final Set<String> TEST_PRODUCTS = Set.of("TEST-A", "TEST-B", "TEST-C", "TEST-D");
    private static final List<String> TEST_PRODUCT_ORDER = List.of("TEST-A", "TEST-B", "TEST-C", "TEST-D");
    private static final Map<String, String> CATEGORY_IMAGES = Map.of(
            "Electronics", "https://images.unsplash.com/photo-1498049794561-7780e7231661?auto=format&fit=crop&w=480&h=480&q=80",
            "Fashion", "https://images.unsplash.com/photo-1445205170230-053b83016050?auto=format&fit=crop&w=480&h=480&q=80",
            "Grocery", "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=480&h=480&q=80",
            "Home & Kitchen", "https://images.unsplash.com/photo-1584990347499-78d6a6c2f4f8?auto=format&fit=crop&w=480&h=480&q=80"
    );

    private final ProjectDataPaths paths;
    private final TrustScoringService scoringService;
    private final AuditLogCsvRepository auditLogRepository;

    public CatalogApiService(
            ProjectDataPaths paths,
            TrustScoringService scoringService,
            AuditLogCsvRepository auditLogRepository
    ) {
        this.paths = paths;
        this.scoringService = scoringService;
        this.auditLogRepository = auditLogRepository;
    }

    public List<ProductApiResponse> getProducts() throws IOException {
        List<CatalogSnapshot> catalog = new CatalogCsvRepository(paths.dataFile("catalog.csv")).findAll();
        List<OrderRecord> orders = new OrderCsvRepository(paths.dataFile("orders.csv")).findAll();
        Map<String, CatalogSnapshot> latestByProduct = findLatestSnapshotByProduct(catalog);
        List<ProductApiResponse> products = new ArrayList<>();

        for (Map.Entry<String, CatalogSnapshot> product : latestByProduct.entrySet()) {
            TrustScoringResult result = scoringService.evaluateProduct(product.getKey(), catalog, orders);
            CatalogSnapshot latest = product.getValue();
            products.add(toApiResponse(result, latest));
        }

        products.sort(Comparator
                .comparing((ProductApiResponse product) -> !product.isTestCase())
                .thenComparingInt(product -> {
                    int index = TEST_PRODUCT_ORDER.indexOf(product.productId());
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparing(ProductApiResponse::productId));
        return List.copyOf(products);
    }

    public ProductApiResponse getProduct(String productId) throws IOException {
        return getProducts().stream()
                .filter(product -> product.productId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    public List<Map<String, Object>> getAuditLog() throws IOException {
        return auditLogRepository.findAllNewestFirst(paths.auditLogFile());
    }

    public Path dashboardFile() {
        return paths.projectFile("dashboard/static/index.html");
    }

    public Path storefrontFile() {
        return paths.projectFile("shop/static/index.html");
    }

    public Path staticDirectory(String application) {
        return paths.projectFile(application + "/static");
    }

    private Map<String, CatalogSnapshot> findLatestSnapshotByProduct(List<CatalogSnapshot> catalog) {
        Map<String, CatalogSnapshot> latestByProduct = new HashMap<>();
        for (CatalogSnapshot snapshot : catalog) {
            CatalogSnapshot current = latestByProduct.get(snapshot.productId());
            if (current == null || snapshot.lastUpdated().toInstant().isAfter(current.lastUpdated().toInstant())) {
                latestByProduct.put(snapshot.productId(), snapshot);
            }
        }
        return latestByProduct;
    }

    private ProductApiResponse toApiResponse(TrustScoringResult result, CatalogSnapshot latest) {
        String category = latest.category() == null ? "Uncategorized" : latest.category();
        return new ProductApiResponse(
                result.productId(),
                result.productName(),
                result.trustScore(),
                result.status(),
                result.action(),
                result.reasons(),
                TEST_PRODUCTS.contains(result.productId()),
                category,
                latest.dateAdded() == null ? "" : latest.dateAdded(),
                latest.price(),
                (int) latest.stock(),
                productImage(category)
        );
    }

    private String productImage(String category) {
        return CATEGORY_IMAGES.getOrDefault(
                category,
                CATEGORY_IMAGES.get("Electronics")
        );
    }
}
