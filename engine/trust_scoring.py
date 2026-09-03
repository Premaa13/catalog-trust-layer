#!/usr/bin/env python3
"""
Reusable trust scoring functions for the Catalog Trust Layer.

This module provides the core scoring logic that can be imported by
both batch processing (trust_engine.py) and single-product queries.
"""

import pandas as pd
from pathlib import Path
from datetime import timezone


def get_data_paths():
    """Get paths to data files."""
    here = Path(__file__).resolve().parent
    project_root = here.parent
    data_dir = project_root / "data"
    return {
        "catalog": data_dir / "catalog.csv",
        "orders": data_dir / "orders.csv",
        "results": data_dir / "results.csv",
    }


def load_data(catalog_path=None, orders_path=None):
    """
    Load catalog and orders CSV files.
    
    Args:
        catalog_path: Path to catalog.csv. If None, uses default.
        orders_path: Path to orders.csv. If None, uses default.
    
    Returns:
        Tuple of (catalog_df, orders_df)
    """
    paths = get_data_paths()
    
    if catalog_path is None:
        catalog_path = paths["catalog"]
    if orders_path is None:
        orders_path = paths["orders"]
    
    catalog = pd.read_csv(catalog_path)
    orders = pd.read_csv(orders_path)
    
    # Parse timestamps
    catalog["last_updated"] = pd.to_datetime(catalog["last_updated"])
    orders["timestamp"] = pd.to_datetime(orders["timestamp"])
    
    # Convert price and stock to numeric
    catalog["price"] = pd.to_numeric(catalog["price"])
    catalog["stock"] = pd.to_numeric(catalog["stock"])
    orders["quantity"] = pd.to_numeric(orders["quantity"])
    
    return catalog, orders


def calculate_trust_score(product_id, catalog, orders):
    """
    Calculate trust score for a product based on three checks:
    1. Price Velocity
    2. Stock Consistency
    3. Freshness
    
    Args:
        product_id: The product ID to score
        catalog: DataFrame with catalog snapshots
        orders: DataFrame with order history
    
    Returns:
        Tuple of (trust_score: int, reasons: list of str)
    """
    
    # Get all snapshots for this product
    product_catalog = catalog[catalog["product_id"] == product_id].copy()
    product_orders = orders[orders["product_id"] == product_id].copy()
    
    trust_score = 100
    reasons = []
    
    # Find the latest timestamp in the entire dataset
    if len(catalog) > 0:
        latest_dataset_timestamp = catalog["last_updated"].max()
    else:
        latest_dataset_timestamp = pd.Timestamp.now(tz=timezone.utc)
    
    # ========== CHECK 1: Price Velocity ==========
    if len(product_catalog) > 1:
        # Sort by timestamp to get most recent snapshots
        sorted_snapshots = product_catalog.sort_values("last_updated")
        
        # Get the two most recent
        latest_snapshot = sorted_snapshots.iloc[-1]
        previous_snapshot = sorted_snapshots.iloc[-2]
        
        price_old = previous_snapshot["price"]
        price_new = latest_snapshot["price"]
        time_old = previous_snapshot["last_updated"]
        time_new = latest_snapshot["last_updated"]
        
        # Calculate % change and time difference
        if price_old != 0:
            price_change_pct = ((price_new - price_old) / price_old) * 100
        else:
            price_change_pct = 0
        
        time_diff_hours = (time_new - time_old).total_seconds() / 3600
        
        # Apply penalties
        if price_change_pct > 50:
            penalty = 50
            trust_score -= penalty
            reasons.append(f"Price increased {price_change_pct:.1f}% in {time_diff_hours:.1f} hours (high-risk)")
        elif price_change_pct > 25:
            penalty = 25
            trust_score -= penalty
            reasons.append(f"Price increased {price_change_pct:.1f}% in {time_diff_hours:.1f} hours (suspicious)")
    
    # ========== CHECK 2: Stock Consistency ==========
    # Get the latest snapshot to get claimed stock
    latest_catalog_snapshot = product_catalog.loc[product_catalog["last_updated"].idxmax()]
    claimed_stock = latest_catalog_snapshot["stock"]
    latest_catalog_timestamp = latest_catalog_snapshot["last_updated"]
    
    # Get orders within last 2 hours of latest timestamp
    two_hours_ago = latest_catalog_timestamp - pd.Timedelta(hours=2)
    recent_orders = product_orders[
        (product_orders["timestamp"] >= two_hours_ago) & 
        (product_orders["timestamp"] <= latest_catalog_timestamp) &
        (product_orders["status"] == "completed")
    ]
    
    recent_sold_quantity = recent_orders["quantity"].sum()
    
    if recent_sold_quantity > claimed_stock:
        penalty = 50
        trust_score -= penalty
        reasons.append(f"Claimed stock ({claimed_stock:.0f}) is less than recently sold quantity ({recent_sold_quantity:.0f}) in last 2 hours")
    elif recent_sold_quantity > 0 and recent_sold_quantity >= (0.8 * claimed_stock):
        penalty = 20
        trust_score -= penalty
        reasons.append(f"High sales velocity relative to claimed stock")
    
    # ========== CHECK 3: Freshness ==========
    # Calculate hours since last update (relative to latest timestamp in dataset)
    hours_since_update = (latest_dataset_timestamp - latest_catalog_timestamp).total_seconds() / 3600
    
    if hours_since_update > 72:
        penalty = 40
        trust_score -= penalty
        reasons.append(f"Listing stale: not updated in {hours_since_update:.1f} hours (severe)")
    elif hours_since_update > 48:
        penalty = 20
        trust_score -= penalty
        reasons.append(f"Listing stale: not updated in {hours_since_update:.1f} hours")
    elif hours_since_update > 24:
        penalty = 10
        trust_score -= penalty
        reasons.append(f"Listing aging: not updated in {hours_since_update:.1f} hours (warning)")
    
    # Floor at 0
    trust_score = max(0, trust_score)
    
    return trust_score, reasons


def map_score_to_status_and_action(score):
    """
    Map trust score to status and action.
    
    Score ranges:
    - 90-100  → VERIFIED, ALLOW
    - 60-89   → FLAGGED, ALLOW_WITH_WARNING
    - 30-59   → FLAGGED, REVIEW
    - 0-29    → FLAGGED, BLOCK
    
    Args:
        score: Trust score (0-100)
    
    Returns:
        Tuple of (status: str, action: str)
    """
    if 90 <= score <= 100:
        return "VERIFIED", "ALLOW"
    elif 60 <= score <= 89:
        return "FLAGGED", "ALLOW_WITH_WARNING"
    elif 30 <= score <= 59:
        return "FLAGGED", "REVIEW"
    else:  # 0-29
        return "FLAGGED", "BLOCK"


def apply_safety_overrides(result, product_id, catalog):
    """
    Apply safety override layer AFTER normal trust scoring.
    
    This function is intentionally separate from the trust score calculation.
    The trust score measures how trustworthy a listing is based on historical data.
    Safety overrides are hard gates on whether the agent may transact, independent
    of the trust score itself.
    
    SAFETY OVERRIDE RULE:
    - If freshness (hours since last_updated) > 72 hours, force action to "REVIEW"
      UNLESS the score-based action was already "BLOCK", in which case keep BLOCK.
    - When override fires, add a note to reasons explaining the override.
    - Trust score remains unchanged — only action and status are affected.
    
    Args:
        result: Dictionary with keys: product_id, product_name, trust_score, status, action, reasons
        product_id: The product ID
        catalog: DataFrame with all catalog snapshots
    
    Returns:
        Modified result dictionary
    """
    
    # Get the latest snapshot for this product
    product_catalog = catalog[catalog["product_id"] == product_id]
    latest_row = product_catalog.loc[product_catalog["last_updated"].idxmax()]
    latest_catalog_timestamp = latest_row["last_updated"]
    
    # Find the latest timestamp in the entire dataset
    latest_dataset_timestamp = catalog["last_updated"].max()
    
    # Calculate hours since last update
    hours_since_update = (latest_dataset_timestamp - latest_catalog_timestamp).total_seconds() / 3600
    
    # Apply safety override: severe staleness forces REVIEW (unless already BLOCK)
    if hours_since_update > 72 and result["action"] != "BLOCK":
        result["action"] = "REVIEW"
        result["status"] = "FLAGGED"  # Ensure status is consistent with REVIEW action
        
        # Add override reason to reasons list
        reasons_list = result["reasons"] if isinstance(result["reasons"], list) else (result["reasons"].split("; ") if result["reasons"] else [])
        override_reason = "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score"
        if override_reason not in reasons_list:
            reasons_list.append(override_reason)
        result["reasons"] = reasons_list
    
    return result


def evaluate_product(product_id, catalog, orders):
    """
    Run the complete scoring pipeline for a single product.
    
    This is the main entry point for evaluating a product through all three
    checks plus safety overrides.
    
    Args:
        product_id: The product ID to evaluate
        catalog: DataFrame with catalog snapshots
        orders: DataFrame with order history
    
    Returns:
        Dictionary with keys:
        - product_id: str
        - product_name: str
        - trust_score: int (0-100)
        - status: str (VERIFIED or FLAGGED)
        - action: str (ALLOW, ALLOW_WITH_WARNING, REVIEW, or BLOCK)
        - reasons: list of str
    """
    
    # Check if product exists
    product_rows = catalog[catalog["product_id"] == product_id]
    if len(product_rows) == 0:
        return {"error": "product_id not found"}
    
    # Get product info from latest snapshot
    latest_row = product_rows.loc[product_rows["last_updated"].idxmax()]
    product_name = latest_row["product_name"]
    
    # Calculate trust score
    trust_score, reasons = calculate_trust_score(product_id, catalog, orders)
    
    # Map to status and action
    status, action = map_score_to_status_and_action(trust_score)
    
    result = {
        "product_id": product_id,
        "product_name": product_name,
        "trust_score": trust_score,
        "status": status,
        "action": action,
        "reasons": reasons,
    }
    
    # Apply safety overrides (separate decision layer from trust scoring)
    result = apply_safety_overrides(result, product_id, catalog)
    
    return result
