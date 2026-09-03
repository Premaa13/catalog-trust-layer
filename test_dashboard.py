#!/usr/bin/env python3
"""Quick test of the dashboard app."""

import sys
from pathlib import Path

# Test imports
try:
    from dashboard.app import app
    print("✓ Dashboard app imported successfully")
except Exception as e:
    print(f"✗ Failed to import dashboard: {e}")
    sys.exit(1)

# Test that routes exist
try:
    routes = [r.path for r in app.routes]
    print(f"✓ Found {len(routes)} routes")
    for path in ["/", "/api/products", "/api/audit-log"]:
        if path in routes:
            print(f"  ✓ {path}")
        else:
            print(f"  ✗ {path} NOT FOUND")
except Exception as e:
    print(f"✗ Failed to check routes: {e}")
    sys.exit(1)

print("\nDashboard is ready to run with:")
print("  uvicorn dashboard.app:app --host 127.0.0.1 --port 8000")
