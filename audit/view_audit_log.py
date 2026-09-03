#!/usr/bin/env python3
"""
View the audit log in a formatted table.

Reads audit/audit_log.csv and displays it as a clean, readable table.
"""

import pandas as pd
from pathlib import Path


def view_audit_log():
    """Read and display the audit log as a formatted table."""
    
    here = Path(__file__).resolve().parent
    log_path = here / "audit_log.csv"
    
    if not log_path.exists():
        print("No audit log found. Run some verifications first.")
        return
    
    # Read the CSV
    df = pd.read_csv(log_path)
    
    # Replace NaN values in reasons column with empty string for clean display
    df["reasons"] = df["reasons"].fillna("")
    
    print("=" * 150)
    print("CATALOG TRUST LAYER - AUDIT LOG")
    print("=" * 150)
    print()
    
    # Display the table
    print(df.to_string(index=False))
    print()
    print("=" * 150)
    print(f"Total entries: {len(df)}")
    print("=" * 150)


if __name__ == "__main__":
    view_audit_log()
