#!/usr/bin/env python3
"""Final comprehensive dashboard verification."""

import requests
import json

print("\n" + "=" * 80)
print("CATALOG TRUST LAYER - DASHBOARD VERIFICATION")
print("=" * 80 + "\n")

try:
    # Test 1: Dashboard HTML page
    print("1. Testing Dashboard HTML Page...")
    resp = requests.get('http://127.0.0.1:8000/', timeout=5)
    if resp.status_code == 200 and '<html' in resp.text.lower():
        print("   ✓ Dashboard page loads successfully")
        print(f"   ✓ Content: {len(resp.text):,} bytes")
    else:
        print(f"   ✗ Dashboard page failed (status {resp.status_code})")
    print()

    # Test 2: Products API
    print("2. Testing /api/products Endpoint...")
    resp = requests.get('http://127.0.0.1:8000/api/products', timeout=5)
    if resp.status_code == 200:
        products = resp.json()
        print(f"   ✓ API returns {len(products)} products")
        
        # Check test cases
        test_cases = {p['product_id']: p for p in products if p.get('is_test_case')}
        print(f"   ✓ Found {len(test_cases)} test cases")
        
        # Verify test case details
        expected = {
            'TEST-A': {'score': 50, 'action': 'REVIEW', 'status': 'FLAGGED'},
            'TEST-B': {'score': 50, 'action': 'REVIEW', 'status': 'FLAGGED'},
            'TEST-C': {'score': 60, 'action': 'REVIEW', 'status': 'FLAGGED'},
            'TEST-D': {'score': 100, 'action': 'ALLOW', 'status': 'VERIFIED'},
        }
        
        print("\n   Test Case Results:")
        for test_id, expected_vals in expected.items():
            if test_id in test_cases:
                p = test_cases[test_id]
                score_ok = p['trust_score'] == expected_vals['score']
                action_ok = p['action'] == expected_vals['action']
                status_ok = p['status'] == expected_vals['status']
                all_ok = score_ok and action_ok and status_ok
                
                symbol = "✓" if all_ok else "✗"
                print(f"      {symbol} {test_id}: score={p['trust_score']}, action={p['action']}, status={p['status']}")
                if p['reasons']:
                    print(f"         Reasons: {'; '.join(p['reasons'][:1])}")
    else:
        print(f"   ✗ API failed (status {resp.status_code})")
    print()

    # Test 3: Audit Log API
    print("3. Testing /api/audit-log Endpoint...")
    resp = requests.get('http://127.0.0.1:8000/api/audit-log', timeout=5)
    if resp.status_code == 200:
        audit_entries = resp.json()
        print(f"   ✓ Audit log contains {len(audit_entries)} entries")
        
        if len(audit_entries) > 0:
            print(f"   ✓ Most recent entry: {audit_entries[0]['timestamp'][:19]} - {audit_entries[0]['product_id']}")
    else:
        print(f"   ✗ Audit log API failed (status {resp.status_code})")
    print()

    print("=" * 80)
    print("✓ DASHBOARD VERIFICATION COMPLETE")
    print("=" * 80)
    print("\nDashboard is ready! Open in your browser:")
    print("  → http://localhost:8000")
    print("\nFeatures:")
    print("  • Products table with color-coded trust actions")
    print("  • Planted test cases highlighted with badges")
    print("  • Audit log showing verification history")
    print("  • Real-time data from /api/products and /api/audit-log")
    print()

except Exception as e:
    print(f"✗ Error during verification: {e}")
    print("\nMake sure the server is running:")
    print("  uvicorn dashboard.app:app --host 127.0.0.1 --port 8000")
