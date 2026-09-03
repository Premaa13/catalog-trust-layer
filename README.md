catalog-trust-layer
=====================

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
