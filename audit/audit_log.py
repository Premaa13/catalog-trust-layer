#!/usr/bin/env python3
"""
Audit logging for Catalog Trust Layer verification decisions.

Records all trust verifications with timestamps for accountability and auditing.
"""

import csv
import pandas as pd
from datetime import datetime, timezone
from pathlib import Path


def get_audit_log_path():
    """Get the path to the audit log CSV."""
    here = Path(__file__).resolve().parent
    return here / "audit_log.csv"


def log_verification(result: dict) -> None:
    """
    Log a verification decision to the audit log.
    
    Appends a new row to audit/audit_log.csv with the verification result.
    Creates the file with headers if it doesn't exist yet.
    
    Args:
        result: Dictionary returned by verify_listing_trust() or evaluate_product()
                Must contain: product_id, product_name, trust_score, status, action, reasons
    """
    
    # Skip logging if there's an error result
    if "error" in result:
        return
    
    log_path = get_audit_log_path()
    
    # Prepare the row
    timestamp = datetime.now(timezone.utc).isoformat()
    
    # Convert reasons list to semicolon-separated string
    reasons_str = ""
    if isinstance(result.get("reasons"), list):
        reasons_str = "; ".join(result["reasons"])
    elif isinstance(result.get("reasons"), str):
        reasons_str = result["reasons"]
    
    row = {
        "timestamp": timestamp,
        "product_id": result.get("product_id", ""),
        "product_name": result.get("product_name", ""),
        "trust_score": result.get("trust_score", ""),
        "status": result.get("status", ""),
        "action": result.get("action", ""),
        "reasons": reasons_str,
    }
    
    # Check if file exists
    file_exists = log_path.exists()
    
    # Append to CSV
    with open(log_path, "a", newline="", encoding="utf-8") as f:
        fieldnames = ["timestamp", "product_id", "product_name", "trust_score", "status", "action", "reasons"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        
        # Write header if file is new
        if not file_exists:
            writer.writeheader()
        
        # Write the row
        writer.writerow(row)
