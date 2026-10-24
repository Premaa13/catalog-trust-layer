# Catalog Trust Layer

### A Trust & Provenance Layer for AI Shopping Agents

AI shopping agents increasingly purchase products on behalf of users, but they need a reliable way to determine whether a product listing is trustworthy before completing a purchase.

**Catalog Trust Layer** sits between an AI shopping agent and a merchant catalog, evaluating product listings for suspicious price changes, stock inconsistencies, and stale information. It assigns trust scores and determines whether a product can proceed to purchase, requires caution, or should be reviewed or blocked.

The project includes a Python implementation and a Java Spring Boot backend, with REST APIs, an AI shopping agent, an MCP server, and Razorpay test-mode order creation.

## Key Features

* **Trust Scoring Engine** — Evaluates price changes, stock consistency, and listing freshness.
* **Four Trust Actions** — ALLOW, ALLOW_WITH_WARNING, REVIEW, and BLOCK.
* **Batch Evaluation** — Processes catalog data and generates `results.csv`.
* **REST APIs** — Provides catalog information, trust results, audit logs, and shopping-agent functionality.
* **AI Shopping Agent** — Parses shopping requests, matches products, and recommends products based on trust results.
* **MCP Server** — Exposes the `verify_listing_trust` tool through the Model Context Protocol.
* **Razorpay Integration** — Creates test-mode orders only after trust verification.
* **Audit Logging** — Records purchase verification decisions.
* **Python and Java Implementations** — Preserves the original Python project alongside the Java backend.

## Technology Stack

**Java Backend**

* Java 17
* Spring Boot 3.4.5
* Maven
* Spring Web / REST APIs
* Java MCP SDK
* Razorpay API
* CSV-based data storage

**Original Python Implementation**

* Python
* FastAPI
* CSV-based trust evaluation
* Razorpay integration
* MCP tool wrapper

## How It Works

The trust engine evaluates catalog listings using three main checks.

### 1. Price Velocity

Detects suspicious price changes between catalog snapshots. Large price increases can reduce a product's trust score.

### 2. Stock Consistency

Compares recent completed order quantities with the merchant's claimed stock to identify potential inconsistencies.

### 3. Listing Freshness

Evaluates how recently a listing was updated. Stale listings can receive penalties or be forced into manual review.

Each product receives a trust score, verification status, recommended action, and explanation of relevant risk signals.

### Trust Actions

| Action                  | Meaning                           |
| ----------------------- | --------------------------------- |
| 🟢 `ALLOW`              | Listing passes the trust checks.  |
| 🟡 `ALLOW_WITH_WARNING` | Listing may proceed with caution. |
| 🟠 `REVIEW`             | Manual review is recommended.     |
| 🔴 `BLOCK`              | Purchase should not proceed.      |

The purchase service rejects REVIEW and BLOCK products before contacting Razorpay.

## Project Structure

```text
catalog-trust-layer/
├── engine/                 # Python trust-scoring engine
├── agent/                  # Python shopping agent
├── payment/                # Python Razorpay integration
├── shop/                   # Python storefront
├── dashboard/              # Python trust dashboard
├── mcp_tool/               # Original Python MCP tool
├── data/                   # Catalog, orders, and generated results
├── audit/                  # Audit logging
├── evaluation/             # Evaluation scripts and results
├── java-backend/
│   ├── src/main/java/      # Java Spring Boot application
│   ├── src/test/java/      # Java automated tests
│   ├── data/               # Backend data directory, if configured
│   ├── pom.xml             # Maven configuration
│   └── README.md           # Java backend documentation
├── agent_demo.py
├── agent_demo_no_trust_layer.py
└── README.md
```

## Java Backend Setup

### Prerequisites

* Java 17 or later
* Maven 3.9 or later
* Git

Verify your installation:

```bash
java -version
mvn -version
```

### Run the Java Backend

From the repository root:

```bash
mvn -f java-backend/pom.xml spring-boot:run
```

The backend starts at:

`http://localhost:8080`

Keep the terminal running while using the application.

### Run Tests

```bash
mvn -f java-backend/pom.xml test
```

The integration work completed during development reported **51 passing tests**, with zero failures or errors. Run the command above to verify the current repository state.

### Available REST Endpoints

| Method | Endpoint                    | Purpose                                      |
| ------ | --------------------------- | -------------------------------------------- |
| GET    | `/`                         | Dashboard page                               |
| GET    | `/shop`                     | Storefront page                              |
| GET    | `/api/products`             | List evaluated products                      |
| GET    | `/api/shop/products`        | Storefront product listing                   |
| GET    | `/api/products/{productId}` | Retrieve a product                           |
| GET    | `/api/audit-log`            | Retrieve audit entries                       |
| GET    | `/api/agent/run?query=...`  | Stream shopping-agent progress               |
| GET    | `/api/agent/demo`           | Stream a deterministic agent demo            |
| POST   | `/api/agent/shop`           | Submit a shopping goal                       |
| POST   | `/api/buy`                  | Request trust-gated test-mode order creation |

Product-specific responses and errors follow the implementation documented in the Java backend README.

## MCP Integration

The Java backend implements an MCP tool named:

`verify_listing_trust`

The tool allows compatible MCP clients to discover and invoke listing verification.

It returns trust information for the requested product, including the score, status, action, and relevant reasons.

Use the MCP SDK, transport, and client configuration documented in `java-backend/README.md`. Ensure the backend is running before connecting the client.

The MCP tool does not bypass purchase verification or directly initiate payments.

## Razorpay Test-Mode Configuration

Razorpay credentials are required only when testing successful order creation against Razorpay's test API.

Configure these environment variables using your **Razorpay test-mode credentials**:

```text
RAZORPAY_KEY_ID=your_test_key_id
RAZORPAY_KEY_SECRET=your_test_key_secret
```

The key ID must use the test-mode prefix `rzp_test_`.

Never commit credentials to GitHub. Keep `.env` files and secrets excluded through `.gitignore`.

The purchase flow uses trust verification before order creation. No real payment should be initiated or captured by this test-mode integration.

## Synthetic Dataset

The original Python dataset generator creates:

* `data/catalog.csv` — catalog snapshots containing product information, prices, stock, and timestamps.
* `data/orders.csv` — order records containing product IDs, quantities, timestamps, and order statuses.

The dataset includes normal products and planted test cases.

Generate the synthetic dataset using the original Python implementation:

```bash
python data/generate_synthetic_data.py
```

**Important:** Regenerating the dataset changes the input data and may change trust scores and evaluation results.

### Planted Test Cases

| Product  | Scenario          | Purpose                                   |
| -------- | ----------------- | ----------------------------------------- |
| `TEST-A` | Price spike       | Tests abnormal price increases            |
| `TEST-B` | Stock consistency | Tests claimed stock against recent orders |
| `TEST-C` | Stale listing     | Tests freshness penalties                 |
| `TEST-D` | Normal control    | Tests an ordinary listing                 |

The dataset is synthetic and intended for development and testing.

## Running the Original Python Implementation

The Python implementation remains available separately.

Install its dependencies:

```bash
pip install -r requirements.txt
```

Run the original dashboard:

```bash
uvicorn dashboard.app:app --reload --port 8000
```

Open:

`http://localhost:8000`

Run the original shopping-agent demo:

```bash
python agent_demo.py
```

Run the comparison demo without the trust layer:

```bash
python agent_demo_no_trust_layer.py
```

Run the evaluation script:

```bash
python evaluation/run_evaluation.py
```

Use the Python commands only when working with the original Python implementation. The Java backend has its own startup command.

## Evaluation

The project evaluates product listings against the synthetic dataset and generates trust decisions for individual products.

During Java backend integration testing, the generated output for **70 distinct products** matched the Python reference results row by row.

The reported summary was:

| Result             |  Count |
| ------------------ | -----: |
| Verified — ALLOW   |     24 |
| ALLOW_WITH_WARNING |      3 |
| REVIEW             |     43 |
| BLOCK              |      0 |
| **Total products** | **70** |

These figures describe the dataset used during that test run. Results can differ if the input data changes.

## Security Considerations

* Trust verification occurs before purchase-order creation.
* REVIEW and BLOCK products cannot proceed through the purchase service.
* Razorpay credentials are supplied through environment variables.
* Automated payment tests use mocked provider calls.
* The MCP tool verifies listing trust without directly initiating payments.
* The dataset is synthetic and is not intended for production purchasing decisions.

## Disclaimer

Catalog Trust Layer is a research and development project for evaluating catalog trust and purchase safety.

Razorpay integration is intended for **test mode only**. No real payments are processed by the intended demonstration workflow.
