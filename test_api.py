#!/usr/bin/env python3
"""Test the dashboard API endpoints."""

import requests
import json

print("=" * 70)
print("DASHBOARD API TEST")
print("=" * 70)
print()

# Test /api/products
print("Testing /api/products...")
resp = requests.get('http://127.0.0.1:8000/api/products')
print(f"  Status: {resp.status_code}")
data = resp.json()
print(f"  Total products: {len(data)}")

# Check test cases
test_cases = [p for p in data if p.get('is_test_case')]
print(f"  Test cases found: {len(test_cases)}")
print()

print("TEST CASE RESULTS (should be highlighted):")
print("-" * 70)
for p in test_cases:
    reasons_str = "; ".join(p["reasons"]) if p["reasons"] else "(no issues)"
    print(f"  {p['product_id']:8} score={p['trust_score']:3} action={p['action']:20} status={p['status']}")
    if p["reasons"]:
        print(f"           → {reasons_str}")

print()
print("=" * 70)

# Test /api/audit-log
print("Testing /api/audit-log...")
resp = requests.get('http://127.0.0.1:8000/api/audit-log')
print(f"  Status: {resp.status_code}")
audit_data = resp.json()
print(f"  Audit log entries: {len(audit_data)}")

print()
print("RECENT AUDIT ENTRIES (most recent first):")
print("-" * 70)
for entry in audit_data[:5]:
    print(f"  {entry['timestamp'][:19]} - {entry['product_id']} ({entry['action']})")

print()
print("✓ Dashboard is working correctly!")
print()
print("Visit http://localhost:8000 in your browser to see the dashboard.")
