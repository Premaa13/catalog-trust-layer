# Catalog Trust Layer - Java backend

This Spring Boot project is a separate Java implementation of the existing Python
trust-scoring logic, catalog REST APIs, test-mode Razorpay order creation,
shopping agent, and an MCP trust-verification server. It does not replace or
modify the Python project.

## Files

- `src/main/java/com/catalogtrust/CatalogTrustApplication.java` is the Spring
  Boot application's entry point.
- `src/main/java/com/catalogtrust/model/CatalogSnapshot.java` represents one
  product listing snapshot, including its price, stock, and update time.
- `src/main/java/com/catalogtrust/model/OrderRecord.java` represents an order
  used by the stock consistency check.
- `src/main/java/com/catalogtrust/model/TrustScoringResult.java` holds the
  score, status, action, and reasons returned by the scoring service.
- `src/main/java/com/catalogtrust/service/TrustScoringService.java` applies the
  Python scoring rules and stale-listing safety override.
- `src/main/java/com/catalogtrust/service/ProjectDataPaths.java` resolves the
  existing data and frontend files from the application location or an
  explicit `CATALOG_TRUST_PROJECT_ROOT`.
- `src/main/java/com/catalogtrust/TrustEngineCommandLineRunner.java` runs the
  batch engine during backend startup.
- `src/main/java/com/catalogtrust/service/CatalogApiService.java` builds
  dashboard and storefront catalog payloads using the repositories and scorer.
- `src/main/java/com/catalogtrust/controller/CatalogApiController.java`
  provides the catalog and audit-log REST routes.
- `src/main/java/com/catalogtrust/repository/AuditLogCsvRepository.java` reads
  existing audit entries in newest-first order.
- `src/main/java/com/catalogtrust/agent/ShoppingIntentParser.java` parses
  product keywords and optional price ceilings from a natural-language query.
- `src/main/java/com/catalogtrust/agent/ShoppingAgentService.java` searches and
  ranks catalog matches, checks each through the trust scorer, and recommends
  only listings that are allowed by the trust layer.
- `src/main/java/com/catalogtrust/mcp/ListingTrustMcpTool.java` exposes the
  shared trust scorer as the `verify_listing_trust` MCP tool.
- `src/main/java/com/catalogtrust/mcp/McpToolConfiguration.java` registers the
  annotated trust tool with Spring AI's MCP server.
- `src/main/resources/application.properties` configures the MCP server name,
  tool capability, and HTTP SSE endpoints.
- `src/test/java/com/catalogtrust/service/TrustScoringServiceTest.java` reads
  the existing root-level CSV data and checks TEST-A through TEST-D against
  results produced by the Python implementation.
- `src/test/java/com/catalogtrust/mcp/ListingTrustMcpServerTest.java` uses the
  official Java MCP client to initialize, discover, and invoke the live server.
- `src/test/java/com/catalogtrust/service/ProjectDataPathsTest.java` checks
  that file resolution is independent of an unrelated working directory.
- `pom.xml` configures Java 17, Spring Boot, and JUnit-based tests.

## Run the tests

Install a Java 17 (or later) JDK and Maven, then run from the repository root:

```powershell
mvn -f java-backend\pom.xml test
```

The tests load `data\catalog.csv` and `data\orders.csv` from the existing
Python project. Run them without regenerating those files so the Java results
are compared against the same fixture.

The MCP tests start a local server on a random port and use the Java MCP SDK's
SSE client transport. HTTP integration tests start the Spring application and
exercise the dashboard, storefront, catalog, audit, agent, and error routes.
Razorpay is mocked in tests; the suite makes no payment-provider requests.

## TEST-B note

The existing scoring code counts completed orders only when their timestamps
fall within the two hours ending at the latest TEST-B catalog snapshot. In the
checked-in CSV, the later order is after that snapshot, leaving 45 units in the
window. Since 45 is at least 80% of the claimed stock of 50, the Python result
is score 80 with `ALLOW_WITH_WARNING` and the high-sales-velocity reason; it is
not the 50-point stock-exceeded result described in the synthetic-data
generator comments.

## Run the batch trust engine

From the repository root, run:

```powershell
mvn -f java-backend\pom.xml spring-boot:run
```

The Spring Boot command-line runner finds the existing `data\catalog.csv` and
`data\orders.csv` relative to the application location, evaluates each unique
product ID once, prints the planted and normal product sections with summary
counts, and writes `data\results.csv`.
The output columns are `product_id,product_name,trust_score,status,action,reasons`,
sorted by `product_id`. Multiple catalog snapshots contribute to one product
result, and freshness is measured against the latest timestamp in the full
catalog, as in the Python implementation.

## Run the Java backend

From the repository root, start the Spring Boot server with:

```powershell
mvn -f java-backend\pom.xml spring-boot:run
```

The server listens on port `8080` by default. It serves the dashboard at `/`,
the storefront page at `/shop`, and the existing frontends' static files under
`/static/` and `/shop/static/`. The backend locates data and frontend files
from its application location, so running from the repository root or
`java-backend` does not change file resolution. To run a packaged backend when
its files are stored separately, set `CATALOG_TRUST_PROJECT_ROOT` to the
absolute repository path containing `data\catalog.csv` and `data\orders.csv`.

### API endpoints

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/products` | Trust evaluations plus storefront fields for every unique product, with TEST-A through TEST-D first. |
| `GET` | `/api/shop/products` | Alias used by the storefront frontend; returns the same product payload. |
| `GET` | `/api/audit-log` | Audit rows sorted newest first; returns an empty list if the audit file does not exist. |
| `GET` | `/api/products/{productId}` | Look up one product; returns JSON `detail` errors for blank or unknown IDs. |
| `GET` | `/api/products?product_id={productId}` | Equivalent product lookup using a query parameter. |
| `POST` | `/api/buy` | Verify the product and create a Razorpay test order only for ALLOW/ALLOW_WITH_WARNING listings. |
| `GET` | `/api/agent/run?query=...` | Server-sent shopping-agent progress and safe recommendations, compatible with the storefront EventSource client. |
| `GET` | `/api/agent/demo` | Deterministic trust-layer demo event stream used by the storefront. |
| `POST` | `/api/agent/shop` | Run the structured shopping flow for `{"goal":"..."}`; approved orders delegate to the trust-gated purchase service. |

Product JSON uses the Python API's snake_case trust fields (`product_id`,
`product_name`, `trust_score`, `is_test_case`) and includes the storefront
fields (`category`, `date_added`, `price`, `stock`, `image_url`). Data-file
errors return HTTP 500 with a JSON `detail` message. Invalid product lookups
return HTTP 400, unknown products return HTTP 404, and malformed JSON returns
HTTP 422. Agent event routes return `text/event-stream`.

### Test-mode Razorpay order creation

The storefront's existing `POST /api/buy` route accepts only a product ID:

```json
{"product_id":"TEST-D"}
```

The Java service loads the current catalog and orders, calculates the trust
result, and only asks Razorpay to create an order when the action is `ALLOW` or
`ALLOW_WITH_WARNING`. REVIEW/BLOCK products are returned as
`BLOCKED_BY_TRUST_LAYER`; unknown IDs return HTTP 404. Amounts are taken from
the latest catalog row and converted from INR to paise. This route creates a
Razorpay **test order only**; it does not initiate or capture a payment.

Set Razorpay test credentials in the shell before starting the backend. Do not
put credential values in source files or commit them:

```powershell
$env:RAZORPAY_KEY_ID = '<Razorpay test key ID>'
$env:RAZORPAY_KEY_SECRET = '<Razorpay test key secret>'
mvn -f java-backend\pom.xml spring-boot:run
```

The service rejects missing credentials and any key ID that does not begin
with `rzp_test_`. It connects only to `https://api.razorpay.com/v1`, applies a
15-second connect/read timeout, and does not include credentials or provider
response bodies in API messages. With no credentials, catalog browsing and
trust checks still work; an allowed purchase request returns the established
`ORDER_FAILED` response without contacting Razorpay.

To run the payment and full regression tests without contacting Razorpay:

```powershell
mvn -f java-backend\pom.xml test
```

Razorpay requests are mocked in tests. Successful live test-order creation
requires the test credentials to be configured in the environment. No live
Razorpay request or payment was made as part of the automated tests.

## Shopping agent

The Java shopping agent uses the same local keyword/price parsing and ranked
catalog search as the Python implementation; it does not call an external AI
service or require an AI API key. It checks candidate products with
`TrustScoringService` and returns trust reasons alongside recommendations.
REVIEW/BLOCK candidates are excluded. `/api/agent/run` streams events with the
same `step` names consumed by the current storefront. The `/api/agent/shop`
flow can create a test order only by delegating to `PurchaseService`, which
rechecks trust before using Razorpay. Tests mock payment integrations and make
no external requests.

### Safe payment verification coverage

Tests verify that `ALLOW` and `ALLOW_WITH_WARNING` reach the mocked order
service, while `REVIEW` and `BLOCK` return `BLOCKED_BY_TRUST_LAYER` without
calling it. Missing credentials, live-key rejection, invalid amounts, mocked
provider errors, provider HTTP errors, and timeouts are also covered. To test
successful provider order creation manually, use Razorpay **test-mode**
credentials only.
Do not submit real payment details; this backend creates orders but does not
initiate or capture payments.

## MCP trust-verification server

This backend uses Spring AI MCP Server Boot Starter `1.0.0`, compatible with
this project's Spring Boot `3.4.5` and Java 17. Spring AI 1.0.0 uses the
official Model Context Protocol Java SDK and supports the WebMVC Server-Sent
Events (SSE) transport. This is MCP-over-SSE, not Streamable HTTP.

Starting the backend with the command above also starts the MCP server:

- MCP client SSE connection: `http://localhost:8080/sse`
- MCP JSON-RPC message endpoint: `/mcp/message` (the server advertises this
  endpoint to clients; clients should connect through `/sse`)
- Registered tool: `verify_listing_trust`
- Required input: `{"product_id":"TEST-A"}` (exact, case-sensitive catalog ID)

The tool returns the same product ID, product name, trust score, status,
action, and reasons as the Python tool. Unknown products return
`{"error":"product_id not found"}`. Blank IDs produce an MCP tool error.
Successful verifications are appended to the existing audit log. REVIEW and
BLOCK decisions remain results only; this tool has no purchase/payment path
and never changes the scorer's decision.

To connect a separate Spring AI MCP client, add the matching `1.0.0` client
starter to that client project's Maven dependencies:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then configure the server's base URL (the client starter defaults to `/sse`):

```yaml
spring:
  ai:
    mcp:
      client:
        sse:
          connections:
            catalog-trust:
              url: http://localhost:8080
```

The server's HTTP MCP endpoint is unauthenticated by the starter. Keep it
reachable only by trusted local clients, or put an authentication and
authorization boundary in front of it before exposing it on a network.

## Integration test scope

The complete local test command is:

```powershell
mvn -f java-backend\pom.xml test
```

It checks all trust-engine outputs and the results CSV using a temporary output
file, preserving the current `data\results.csv`; runs live HTTP requests against
an embedded Spring server for the pages, API routes, and SSE responses; and
performs MCP initialize, tool discovery, and tool invocation against the
embedded MCP server using the official SDK client. Purchase flows and
Razorpay-provider outcomes use mocks. No real payment-provider call is made.
