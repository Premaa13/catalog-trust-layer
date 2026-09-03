#!/usr/bin/env python3
"""
Evaluation: Trust Layer vs. No Trust Layer

This script compares two scenarios across the full 54-product dataset:

BASELINE (No Trust Layer): All products assumed "allowed" — no filtering applied.
WITH TRUST LAYER: Full trust scoring pipeline applied, using actual actions returned.

Uses 4 planted test cases as ground truth:
  - TEST-A, TEST-B, TEST-C: Anomalies that should be caught (action != ALLOW)
  - TEST-D + 50 normal products: Clean data that should pass through (action == ALLOW)

Metrics calculated:
  - True Positives (TP): Anomalies correctly flagged
  - False Negatives (FN): Anomalies missed
  - False Positives (FP): Normal products incorrectly flagged
  - True Negatives (TN): Normal products correctly passed
  - Precision, Recall, False Positive Rate
"""

import sys
from pathlib import Path

# Add parent directory to path so we can import engine modules
sys.path.insert(0, str(Path(__file__).parent.parent))

from engine.trust_scoring import load_data, evaluate_product


def run_evaluation():
    """Run full evaluation comparing baseline vs. trust layer."""
    
    print("\n" + "=" * 90)
    print("CATALOG TRUST LAYER EVALUATION")
    print("=" * 90 + "\n")
    
    # Load data
    print("Loading data...")
    catalog, orders = load_data()
    print(f"✓ Loaded {len(catalog)} products and {len(orders)} orders\n")
    
    # Define ground truth
    ANOMALIES = {"TEST-A", "TEST-B", "TEST-C"}  # Should be caught
    CONTROL = {"TEST-D"}  # Should pass through
    
    # Separate products
    all_product_ids = catalog["product_id"].unique()
    anomaly_ids = [pid for pid in all_product_ids if pid in ANOMALIES]
    control_ids = [pid for pid in all_product_ids if pid in CONTROL]
    normal_ids = [pid for pid in all_product_ids if pid not in ANOMALIES and pid not in CONTROL]
    
    print(f"Ground Truth Breakdown:")
    print(f"  Planted Anomalies (should be FLAGGED):  {len(anomaly_ids)} products {anomaly_ids}")
    print(f"  Control (should PASS):                  1 product {control_ids}")
    print(f"  Normal Products (should PASS):          {len(normal_ids)} products")
    print(f"  TOTAL:                                  {len(all_product_ids)} products\n")
    
    # Run trust layer evaluation on all products
    print("Running Trust Layer evaluation on all products...")
    results = {}
    for product_id in all_product_ids:
        result = evaluate_product(product_id, catalog, orders)
        results[product_id] = result
    print(f"✓ Evaluated all {len(results)} products\n")
    
    # Calculate metrics
    print("Calculating metrics...\n")
    
    tp = 0  # Anomalies correctly flagged (action != ALLOW)
    fn = 0  # Anomalies missed (action == ALLOW)
    fp = 0  # Normal products incorrectly flagged (action != ALLOW)
    tn = 0  # Normal products correctly passed (action == ALLOW)
    
    # Process anomalies
    print("ANOMALY DETECTION:")
    for product_id in anomaly_ids:
        action = results[product_id]["action"]
        status = "✓ CAUGHT" if action != "ALLOW" else "✗ MISSED"
        print(f"  {product_id}: action={action} {status}")
        
        if action != "ALLOW":
            tp += 1
        else:
            fn += 1
    print()
    
    # Process control + normal products
    print("CLEAN PRODUCTS (Control + Normal):")
    for product_id in control_ids + normal_ids:
        action = results[product_id]["action"]
        status = "✓ PASSED" if action == "ALLOW" else "✗ FLAGGED"
        print(f"  {product_id}: action={action} {status}")
        
        if action == "ALLOW":
            tn += 1
        else:
            fp += 1
    print()
    
    # Calculate derived metrics
    total_clean = len(control_ids) + len(normal_ids)
    total_anomalies = len(anomaly_ids)
    
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0
    
    # Build summary
    summary_lines = []
    summary_lines.append("=" * 90)
    summary_lines.append("EVALUATION RESULTS")
    summary_lines.append("=" * 90)
    summary_lines.append("")
    
    # Detailed metrics table
    summary_lines.append("Confusion Matrix:")
    summary_lines.append(f"  True Positives (TP):   {tp:2d}  (anomalies correctly flagged)")
    summary_lines.append(f"  False Negatives (FN):  {fn:2d}  (anomalies missed)")
    summary_lines.append(f"  False Positives (FP):  {fp:2d}  (normal products incorrectly flagged)")
    summary_lines.append(f"  True Negatives (TN):   {tn:2d}  (normal products correctly passed)")
    summary_lines.append("")
    
    summary_lines.append("Performance Metrics:")
    summary_lines.append(f"  Precision:             {precision:.1%}  (TP / (TP + FP))")
    summary_lines.append(f"  Recall:                {recall:.1%}  (TP / (TP + FN))")
    summary_lines.append(f"  False Positive Rate:   {fpr:.1%}  (FP / (FP + TN))")
    summary_lines.append("")
    
    # Executive summary
    summary_lines.append("=" * 90)
    summary_lines.append("EXECUTIVE SUMMARY")
    summary_lines.append("=" * 90)
    summary_lines.append("")
    summary_lines.append(f"Without Trust Layer: {len(all_product_ids)}/{len(all_product_ids)} listings would reach the AI buyer unfiltered.")
    summary_lines.append("")
    summary_lines.append(f"With Trust Layer: {tp + fp} listings blocked/flagged for review, {tn + fn} passed through clean.")
    summary_lines.append("")
    summary_lines.append(f"Correctly caught {tp}/{total_anomalies} planted anomalies ({recall:.0%} recall).")
    summary_lines.append(f"Incorrectly flagged {fp}/{total_clean} normal listings ({fpr:.1%} false positive rate).")
    summary_lines.append("")
    summary_lines.append("=" * 90)
    summary_lines.append("")
    
    # Print to console
    summary_text = "\n".join(summary_lines)
    print(summary_text)
    
    # Save to file
    output_file = Path(__file__).parent / "results_summary.txt"
    with open(output_file, "w") as f:
        f.write(summary_text)
    print(f"✓ Summary saved to {output_file}")
    
    return {
        "tp": tp,
        "fn": fn,
        "fp": fp,
        "tn": tn,
        "precision": precision,
        "recall": recall,
        "fpr": fpr,
    }


if __name__ == "__main__":
    run_evaluation()
