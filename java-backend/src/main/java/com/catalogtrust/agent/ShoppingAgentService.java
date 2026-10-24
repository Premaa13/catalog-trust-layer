package com.catalogtrust.agent;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.TrustScoringResult;
import com.catalogtrust.repository.CatalogCsvRepository;
import com.catalogtrust.repository.OrderCsvRepository;
import com.catalogtrust.service.ProjectDataPaths;
import com.catalogtrust.service.PurchaseService;
import com.catalogtrust.service.TrustScoringService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Searches catalog products, checks each with the trust scorer, and recommends only safe options.
 */
@Service
public class ShoppingAgentService {
    private static final Set<String> SAFE_ACTIONS = Set.of("ALLOW", "ALLOW_WITH_WARNING");
    private static final String NO_MATCHING_RESULTS = "No matching products found";

    private final ShoppingIntentParser intentParser;
    private final ProjectDataPaths paths;
    private final TrustScoringService scoringService;
    private final PurchaseService purchaseService;

    public ShoppingAgentService(
            ShoppingIntentParser intentParser,
            ProjectDataPaths paths,
            TrustScoringService scoringService,
            PurchaseService purchaseService
    ) {
        this.intentParser = intentParser;
        this.paths = paths;
        this.scoringService = scoringService;
        this.purchaseService = purchaseService;
    }

    public List<Map<String, Object>> runQuery(String userQuery) throws IOException {
        String query = userQuery == null ? "" : userQuery;
        ShoppingIntent intent = intentParser.parse(query);
        List<CatalogSnapshot> catalog = loadCatalog();
        List<OrderRecord> orders = loadOrders();
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event("step", "understanding", "message", "Understanding request: '" + query + "'"));

        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("step", "parsed");
        parsed.put("keyword", intent.keyword());
        parsed.put("max_price", intent.maxPrice());
        events.add(parsed);

        List<Candidate> candidates = searchCandidates(intent, catalog);
        List<Map<String, Object>> candidateData = candidates.stream().map(candidate -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("product_name", candidate.productName());
            item.put("price", candidate.price());
            item.put("product_id", candidate.productId());
            return item;
        }).toList();
        Map<String, Object> search = new LinkedHashMap<>();
        search.put("step", "search_results");
        search.put("count", candidates.size());
        search.put("candidates", candidateData);
        events.add(search);

        if (candidates.isEmpty()) {
            events.add(event("step", "no_results", "message", NO_MATCHING_RESULTS));
            return List.copyOf(events);
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();
        int excludedCount = 0;
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            Map<String, Object> checking = candidateFields(candidate);
            checking.put("step", "checking");
            checking.put("index", index + 1);
            events.add(checking);

            TrustScoringResult trustResult = scoringService.evaluateProduct(
                    candidate.productId(),
                    catalog,
                    orders
            );
            String action = trustResult.action();
            List<String> reasons = trustResult.reasons().isEmpty()
                    ? List.of("No reason provided.")
                    : trustResult.reasons();
            Map<String, Object> trustEvent = new LinkedHashMap<>();
            trustEvent.put("step", "trust_result");
            trustEvent.put("product_id", trustResult.productId());
            trustEvent.put("product_name", candidate.productName());
            trustEvent.put("trust_score", trustResult.trustScore());
            trustEvent.put("action", action);
            trustEvent.put("reasons", reasons);
            events.add(trustEvent);

            if (!SAFE_ACTIONS.contains(action)) {
                excludedCount++;
                events.add(event(
                        "step",
                        "skipping",
                        "message",
                        "Unsafe listing (" + action + "), skipping..."
                ));
                continue;
            }

            Map<String, Object> recommendation = candidateFields(candidate);
            recommendation.put("trust_score", trustResult.trustScore());
            recommendation.put("status", trustResult.status());
            recommendation.put("action", action);
            recommendation.put("reasons", reasons);
            recommendations.add(recommendation);
        }

        if (!recommendations.isEmpty()) {
            Map<String, Object> complete = new LinkedHashMap<>();
            complete.put("step", "search_complete");
            complete.put("count", candidates.size());
            complete.put("excluded_count", excludedCount);
            complete.put("recommendations", recommendations);
            events.add(complete);
        } else {
            Map<String, Object> noneSafe = new LinkedHashMap<>();
            noneSafe.put("step", "no_safe_options");
            noneSafe.put("message", "No trustworthy listings found matching your request");
            noneSafe.put("excluded_count", excludedCount);
            events.add(noneSafe);
        }
        return List.copyOf(events);
    }

    public Map<String, Object> runShopGoal(String goal) throws IOException {
        String normalizedGoal = goal == null ? "" : goal.strip();
        if (normalizedGoal.isEmpty()) {
            throw new IllegalArgumentException("Goal is required.");
        }
        ShoppingIntent intent = intentParser.parse(normalizedGoal);
        List<CatalogSnapshot> catalog = loadCatalog();
        List<OrderRecord> orders = loadOrders();
        List<Candidate> candidates = searchCandidates(intent, catalog);
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event("type", "goal", "message", "User goal received: " + normalizedGoal));
        events.add(event("type", "search", "message", "Searching the catalog for matching products..."));
        events.add(event("type", "search_result", "message",
                "Found " + candidates.size() + " matching product(s)."));

        Map<String, Object> finalResult;
        for (Candidate candidate : candidates) {
            Map<String, Object> candidateEvent = candidateFields(candidate);
            candidateEvent.put("type", "candidate");
            candidateEvent.put("message", "Candidate: " + candidate.productName() + " ("
                    + candidate.productId() + ") at " + String.format("₹%,.2f", candidate.price()));
            events.add(candidateEvent);

            TrustScoringResult trust = scoringService.evaluateProduct(
                    candidate.productId(), catalog, orders
            );
            List<String> reasons = trust.reasons().isEmpty() ? List.of("No reason provided.") : trust.reasons();
            Map<String, Object> trustEvent = new LinkedHashMap<>();
            trustEvent.put("type", "trust_check");
            trustEvent.put("product_id", trust.productId());
            trustEvent.put("product_name", candidate.productName());
            trustEvent.put("trust_score", trust.trustScore());
            trustEvent.put("action", trust.action());
            trustEvent.put("reasons", reasons);
            trustEvent.put("message", "Trust check completed for " + candidate.productId()
                    + ". Action: " + trust.action() + ".");
            events.add(trustEvent);

            if (!SAFE_ACTIONS.contains(trust.action())) {
                events.add(event("type", "agent_decision", "message",
                        "Unsafe listing detected. Skipping candidate and evaluating the next product."));
                continue;
            }

            events.add(event("type", "purchase",
                    "message", "Creating Razorpay test-mode order for the trusted listing..."));
            Map<String, Object> purchase = purchaseService.buy(candidate.productId());
            finalResult = new LinkedHashMap<>(purchase);
            finalResult.put("goal", normalizedGoal);
            if ("ORDER_CREATED".equals(purchase.get("purchase_status"))) {
                Map<String, Object> completed = new LinkedHashMap<>();
                completed.put("type", "purchase_complete");
                completed.put("product_id", candidate.productId());
                completed.put("product_name", candidate.productName());
                completed.put("order_id", purchase.get("order_id"));
                completed.put("amount", candidate.price());
                completed.put("trust_score", trust.trustScore());
                completed.put("status", trust.status());
                completed.put("message", "Purchase complete for " + candidate.productName()
                        + ". Razorpay Order ID: " + purchase.get("order_id"));
                events.add(completed);
            } else {
                events.add(event("type", "purchase_failed", "message",
                        "Payment failed for " + candidate.productName() + ": "
                                + purchase.getOrDefault("message", "Order creation failed.")));
            }
            return flowResult(normalizedGoal, events, finalResult);
        }

        finalResult = new LinkedHashMap<>();
        finalResult.put("goal", normalizedGoal);
        finalResult.put("keyword", intent.keyword());
        finalResult.put("max_price", intent.maxPrice());
        finalResult.put("purchase_status", "BLOCKED_BY_TRUST_LAYER");
        if (candidates.isEmpty()) {
            finalResult.put("message", "No matching products found for the requested goal.");
            events.add(event("type", "purchase_blocked",
                    "message", "No matching products were found. Purchase not completed."));
        } else {
            finalResult.put("message",
                    "The agent evaluated all matching listings and no trustworthy listing passed the Trust Layer.");
            events.add(event("type", "purchase_blocked",
                    "message", "No trustworthy listing passed the Trust Layer. No payment was created."));
        }
        return flowResult(normalizedGoal, events, finalResult);
    }

    public List<Map<String, Object>> demoEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(event("step", "understanding", "message",
                "Understanding request: 'demo: skip unsafe then buy safe'"));
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("step", "parsed");
        parsed.put("keyword", "wireless earbuds");
        parsed.put("max_price", 15000);
        events.add(parsed);
        Map<String, Object> candidates = new LinkedHashMap<>();
        candidates.put("step", "search_results");
        candidates.put("count", 2);
        candidates.put("candidates", List.of(
                Map.of("product_name", "Wireless Earbuds Pro", "price", 14683.72, "product_id", "TEST-A"),
                Map.of("product_name", "LED Table Lamp", "price", 2310.15, "product_id", "TEST-D")
        ));
        events.add(candidates);
        events.add(Map.of("step", "checking", "product_name", "Wireless Earbuds Pro",
                "price", 14683.72, "product_id", "TEST-A"));
        events.add(Map.of("step", "trust_result", "product_id", "TEST-A",
                "product_name", "Wireless Earbuds Pro", "trust_score", 50, "action", "REVIEW",
                "reasons", List.of("Price increased 70.0% in 2.0 hours (high-risk)")));
        events.add(event("step", "skipping", "message", "Unsafe listing (REVIEW), skipping..."));
        events.add(Map.of("step", "checking", "product_name", "LED Table Lamp",
                "price", 2310.15, "product_id", "TEST-D"));
        events.add(Map.of("step", "trust_result", "product_id", "TEST-D",
                "product_name", "LED Table Lamp", "trust_score", 100, "action", "ALLOW",
                "reasons", List.of("Listing is consistent and up to date.")));
        events.add(event("step", "purchasing", "message", "Listing verified. Purchasing..."));
        events.add(Map.of("step", "purchase_complete", "order_id", "demo-order-001",
                "product_name", "LED Table Lamp", "amount", 2310.15));
        return List.copyOf(events);
    }

    private List<Candidate> searchCandidates(
            ShoppingIntent intent,
            List<CatalogSnapshot> catalog
    ) {
        Map<String, CatalogSnapshot> latestByProduct = latestSnapshots(catalog);
        List<Candidate> matches = new ArrayList<>();
        for (CatalogSnapshot snapshot : latestByProduct.values()) {
            if (intent.maxPrice() != null && BigDecimal.valueOf(snapshot.price()).compareTo(intent.maxPrice()) > 0) {
                continue;
            }
            int relevance = relevance(intent.keyword(), snapshot.productName(), snapshot.category(), snapshot.productId());
            if (relevance > 0) {
                matches.add(new Candidate(snapshot.productId(), snapshot.productName(), snapshot.category(),
                        snapshot.price(), relevance));
            }
        }
        matches.sort(Comparator.comparingInt(Candidate::relevance).reversed()
                .thenComparingDouble(Candidate::price)
                .thenComparing(Candidate::productId));
        return matches;
    }

    private int relevance(String keyword, String productName, String category, String productId) {
        String normalizedKeyword = normalize(keyword);
        String productText = normalize(productName);
        String categoryText = normalize(category);
        String idText = normalize(productId);
        List<String> tokens = List.of(normalizedKeyword.split(" ")).stream().filter(token -> !token.isBlank()).toList();
        if (normalizedKeyword.isBlank()) {
            return 0;
        }
        if (normalizedKeyword.equals(idText) || normalizedKeyword.equals(idText.replace(" ", ""))) {
            return 5;
        }
        if (normalizedKeyword.length() > 1 && productText.contains(normalizedKeyword)) {
            return 4;
        }
        if (!tokens.isEmpty() && tokens.stream().allMatch(productText::contains)) {
            return 3;
        }
        if (!tokens.isEmpty() && tokens.stream().anyMatch(categoryText::contains)) {
            return 2;
        }
        return !tokens.isEmpty()
                && tokens.stream().anyMatch(token -> productText.contains(token) || categoryText.contains(token))
                ? 1 : 0;
    }

    private String normalize(String value) {
        return Pattern.compile("[^a-z0-9]+")
                .matcher(value == null ? "" : value.toLowerCase())
                .replaceAll(" ")
                .strip();
    }

    private Map<String, CatalogSnapshot> latestSnapshots(List<CatalogSnapshot> catalog) {
        Map<String, CatalogSnapshot> latest = new HashMap<>();
        for (CatalogSnapshot snapshot : catalog) {
            CatalogSnapshot current = latest.get(snapshot.productId());
            if (current == null || snapshot.lastUpdated().toInstant().isAfter(current.lastUpdated().toInstant())) {
                latest.put(snapshot.productId(), snapshot);
            }
        }
        return latest;
    }

    private List<CatalogSnapshot> loadCatalog() throws IOException {
        return new CatalogCsvRepository(paths.dataFile("catalog.csv")).findAll();
    }

    private List<OrderRecord> loadOrders() throws IOException {
        return new OrderCsvRepository(paths.dataFile("orders.csv")).findAll();
    }

    private Map<String, Object> candidateFields(Candidate candidate) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("product_id", candidate.productId());
        fields.put("product_name", candidate.productName());
        fields.put("price", candidate.price());
        return fields;
    }

    private Map<String, Object> event(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put(key1, value1);
        event.put(key2, value2);
        return event;
    }

    private Map<String, Object> flowResult(
            String goal,
            List<Map<String, Object>> events,
            Map<String, Object> finalResult
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("goal", goal);
        response.put("events", events);
        response.put("final_result", finalResult);
        return response;
    }

    private record Candidate(String productId, String productName, String category, double price, int relevance) {
    }
}
