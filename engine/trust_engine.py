#!/usr/bin/env python3
"""
Trust Engine for Catalog Trust Layer.

Batch processing script that evaluates all products and writes results.
Uses the reusable scoring functions from trust_scoring.py.
"""

import pandas as pd
from pathlib import Path
from trust_scoring import load_data, evaluate_product, get_data_paths


def main():
    """Main function to run the trust engine batch processing."""
    
    # Get paths
    paths = get_data_paths()
    RESULTS_CSV = paths["results"]
    
    # Load data
    catalog, orders = load_data()
    
    # Get unique product IDs
    unique_products = catalog["product_id"].unique()
    
    # Calculate trust scores for all products
    results = []
    
    for product_id in unique_products:
        result = evaluate_product(product_id, catalog, orders)
        
        # Convert reasons list to semicolon-separated string for CSV output
        if "error" not in result:
            result["reasons"] = "; ".join(result["reasons"]) if result["reasons"] else ""
        
        results.append(result)
    
    # Convert to DataFrame
    results_df = pd.DataFrame(results)
    
    # Sort by product_id for consistent output
    results_df = results_df.sort_values("product_id").reset_index(drop=True)
    
    # Write results.csv
    results_df.to_csv(RESULTS_CSV, index=False)
    
    # Print summary table
    print("=" * 120)
    print("TRUST ENGINE RESULTS SUMMARY")
    print("=" * 120)
    print()
    
    # Separate test cases from others
    test_cases = ["TEST-A", "TEST-B", "TEST-C", "TEST-D"]
    test_results = results_df[results_df["product_id"].isin(test_cases)].copy()
    normal_results = results_df[~results_df["product_id"].isin(test_cases)].copy()
    
    # Sort test cases in expected order
    test_order = {tc: i for i, tc in enumerate(test_cases)}
    test_results["sort_key"] = test_results["product_id"].map(test_order)
    test_results = test_results.sort_values("sort_key").drop("sort_key", axis=1)
    
    # Print test cases first
    print("PLANTED TEST CASES (HIGHLIGHTED):")
    print("-" * 120)
    print_results_table(test_results)
    print()
    
    # Print normal products
    print("NORMAL PRODUCTS:")
    print("-" * 120)
    print_results_table(normal_results)
    print()
    
    # Print summary statistics
    print("=" * 120)
    print("SUMMARY STATISTICS")
    print("-" * 120)
    print(f"Total products evaluated: {len(results_df)}")
    print(f"VERIFIED (ALLOW): {len(results_df[results_df['status'] == 'VERIFIED'])}")
    print(f"FLAGGED - ALLOW_WITH_WARNING: {len(results_df[results_df['action'] == 'ALLOW_WITH_WARNING'])}")
    print(f"FLAGGED - REVIEW: {len(results_df[results_df['action'] == 'REVIEW'])}")
    print(f"FLAGGED - BLOCK: {len(results_df[results_df['action'] == 'BLOCK'])}")
    print("=" * 120)
    print()
    print(f"Results written to: {RESULTS_CSV.resolve()}")


def print_results_table(df):
    """Print results in a formatted table."""
    # Format for display
    display_df = df.copy()
    
    # Print header
    print(f"{'product_id':<12} {'trust_score':<12} {'status':<12} {'action':<20}")
    print("-" * 120)
    
    # Print rows
    for _, row in display_df.iterrows():
        print(f"{row['product_id']:<12} {row['trust_score']:<12.0f} {row['status']:<12} {row['action']:<20}")
    
    # Print reasons for flagged items
    flagged = display_df[display_df["reasons"] != ""]
    if len(flagged) > 0:
        print()
        print("REASONS FOR FLAGGED PRODUCTS:")
        print("-" * 120)
        for _, row in flagged.iterrows():
            if row["reasons"]:
                print(f"{row['product_id']}: {row['reasons']}")


if __name__ == "__main__":
    main()
