#!/usr/bin/env python3
"""
Catalog Trust Layer Dashboard - FastAPI Backend

Provides REST API endpoints for the trust layer dashboard:
- GET /api/products - All products with trust scores
- GET /api/audit-log - Audit log history
- GET / - Dashboard HTML
"""

import sys
from pathlib import Path
from typing import List
import pandas as pd
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from engine.trust_scoring import load_data, evaluate_product, get_data_paths


# Initialize FastAPI app
app = FastAPI(
    title="Catalog Trust Layer Dashboard",
    description="Real-time trust scoring for catalog listings"
)

# Serve static files (HTML, CSS, JS)
static_dir = Path(__file__).parent / "static"
if static_dir.exists():
    app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")


@app.get("/")
async def get_dashboard():
    """Serve the main dashboard HTML page."""
    dashboard_file = Path(__file__).parent / "static" / "index.html"
    if dashboard_file.exists():
        return FileResponse(dashboard_file)
    return {"error": "Dashboard not found"}


@app.get("/api/products")
async def get_products():
    """
    Get all products with their trust scores and evaluations.
    
    Returns a JSON list with each product's:
    - product_id
    - product_name
    - trust_score (0-100)
    - status (VERIFIED or FLAGGED)
    - action (ALLOW, ALLOW_WITH_WARNING, REVIEW, or BLOCK)
    - reasons (list of strings)
    - is_test_case (boolean, true for TEST-A/B/C/D)
    """
    
    # Load data
    catalog, orders = load_data()
    
    # Get unique product IDs
    unique_products = catalog["product_id"].unique()
    
    # Evaluate all products
    results = []
    test_cases = {"TEST-A", "TEST-B", "TEST-C", "TEST-D"}
    
    for product_id in unique_products:
        result = evaluate_product(product_id, catalog, orders)
        
        # Skip error results
        if "error" in result:
            continue
        
        # Add test case indicator
        result["is_test_case"] = product_id in test_cases
        results.append(result)
    
    # Sort: test cases first (in order), then normal products by ID
    test_case_order = {pid: i for i, pid in enumerate(["TEST-A", "TEST-B", "TEST-C", "TEST-D"])}
    results.sort(
        key=lambda x: (
            x["is_test_case"] == False,  # test cases first
            test_case_order.get(x["product_id"], 999),  # test case order
            x["product_id"]  # then normal products by ID
        )
    )
    
    return results


@app.get("/api/audit-log")
async def get_audit_log():
    """
    Get audit log entries as JSON.
    
    Returns a list of audit entries with most recent first.
    Each entry includes: timestamp, product_id, product_name, trust_score, 
    status, action, reasons
    """
    
    paths = get_data_paths()
    audit_log_path = Path(__file__).parent.parent / "audit" / "audit_log.csv"
    
    if not audit_log_path.exists():
        return []
    
    # Read CSV
    df = pd.read_csv(audit_log_path)
    
    # Fill NaN values in reasons
    df["reasons"] = df["reasons"].fillna("")
    
    # Sort by timestamp descending (most recent first)
    df = df.sort_values("timestamp", ascending=False)
    
    # Convert to list of dicts
    records = df.to_dict("records")
    
    return records


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
