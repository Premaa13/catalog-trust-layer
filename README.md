catalog-trust-layer
=====================

AI shopping agents increasingly buy on behalf of users — but they have no reliable way to tell whether a product listing is trustworthy before completing a purchase. A price could have spiked unexpectedly, stock counts could be inconsistent with recent orders, or a listing could be stale and outdated. The **Catalog Trust Layer** sits between an AI buyer agent and a merchant catalog, scoring each listing for trust signals and blocking or flagging risky purchases before money moves — including live integration with Razorpay for test-mode checkout.

How It Works
-------------
The trust engine (`engine/trust_engine.py`, `engine/trust_scoring.py`) evaluates each product listing against a set of checks:

- **Price velocity** — flags listings where price has changed abnormally fast between catalog snapshots
- **Stock consistency** — flags listings where recent completed orders don't add up with reported stock
- **Freshness** — flags listings that haven't been updated recently (stale data risk)

Each listing receives a trust score and one of four actions (see Dashboard section below for the full color legend). Every verification is written to an audit log (`audit/audit_log.py`) for traceability.

Project Structure
------------------
    engine/       — trust scoring logic
    agent/        — AI shopping agent (intent parsing, purchase flow)
    payment/      — Razorpay test-mode integration
    shop/         — demo storefront (FastAPI + static HTML)
    dashboard/    — real-time trust analysis dashboard
    mcp_tool/     — MCP tool wrapper for listing verification
    data/         — synthetic dataset generator
    audit/        — audit logging + viewer
    evaluation/   — evaluation scripts and results

Environment Setup
------------------
Copy `.env.example` to `.env` and add your Razorpay **test-mode** API keys:

    RAZORPAY_KEY_ID=your_test_key_here
    RAZORPAY_KEY_SECRET=your_test_secret_here

Get test keys from your Razorpay Dashboard → **Settings → API Keys**. Never commit `.env` — it's already excluded via `.gitignore`.

This project generates a synthetic dataset for testing a Catalog Trust Layer. It outputs two CSV files:

- data/catalog.csv — product catalog snapshots (product_id, product_name, price, stock, last_updated)
- data/orders.csv — orders (order_id, product_id, quantity, timestamp, status)

The dataset includes:
- 50 normal/random products
- 4 planted test products (TEST-A, TEST-B, TEST-C, TEST-D)

Planted test cases
------------------
1. TEST-A — Price Spike Test
   - product_id: TEST-A
   - Two catalog snapshots 2 hours apart
   - New snapshot price is exactly 70% higher than the old snapshot
   - Designed to exercise price velocity checks

2. TEST-B — Stock Mismatch Test
   - product_id: TEST-B
   - Catalog stock is exactly 50
   - Completed orders in the last 2 hours total exactly 65 units (multiple orders)
   - Designed to exercise stock consistency checks

3. TEST-C — Stale Listing Test
   - product_id: TEST-C
   - last_updated is exactly 5 days before dataset generation
   - Designed to exercise freshness checks

4. TEST-D — Normal Control
   - product_id: TEST-D
   - Ordinary product; should pass all trust checks

Notes
-----
- Timestamps are stored in ISO 8601 format (UTC-aware).
- The script uses a fixed random seed for reproducibility.
- Faker is supported for product names if installed; otherwise a built-in list of realistic product names is used.
- This dataset is synthetic and intended for testing and development only.

Dependencies
------------
The project uses the Python package `faker` optionally. Install with:

    pip install -r requirements.txt

(If Faker is not installed, the script will still run using a fallback list of product names.)

Usage
-----
Run the generator script:

    python data\generate_synthetic_data.py

This will create `data/catalog.csv` and `data/orders.csv` and print a summary and the output file paths.

Running the Dashboard
---------------------
To launch the web dashboard for real-time trust analysis and audit logging:

    uvicorn dashboard.app:app --reload --port 8000

Then open your browser to **http://localhost:8000**

The dashboard displays:
- **Products Trust Analysis**: All products with their trust scores, status, and action recommendations
  - Green (ALLOW): Safe to transact
  - Yellow (ALLOW_WITH_WARNING): Proceed with caution
  - Orange (REVIEW): Manual review recommended
  - Red (BLOCK): Do not transact
  - Planted test cases (TEST-A, TEST-B, TEST-C, TEST-D) are highlighted with a special badge
- **Verification Audit Log**: Complete history of all trust verifications run through the system

Running the Full Demo
----------------------
To simulate an AI shopping agent checking listing trust before completing a Razorpay test-mode purchase:

    python agent_demo.py

This runs the end-to-end flow: the agent parses a purchase intent, the trust engine verifies the listing, and — if trusted — a Razorpay test-mode order is created via `payment/razorpay_client.py`.

To see the contrast without the trust layer (for comparison/evaluation purposes):

    python agent_demo_no_trust_layer.py

Evaluation
----------
`evaluation/run_evaluation.py` runs the trust engine against the full synthetic dataset and produces a summary in `evaluation/results_summary.txt`.

Disclaimer
----------
This project uses Razorpay **test-mode** keys only. No real payments are processed.