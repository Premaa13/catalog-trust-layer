#!/usr/bin/env python3
"""Test the dashboard HTML page."""

import requests

print("Testing dashboard HTML page...")
resp = requests.get('http://127.0.0.1:8000/', timeout=5)
print(f"Status: {resp.status_code}")
print(f"Content length: {len(resp.text)} bytes")
print(f"Is HTML: {'<html' in resp.text.lower()}")

if resp.status_code == 200:
    print("\n✓ Dashboard HTML page loads successfully!")
    print("\nYou can now open the dashboard in your browser:")
    print("  → http://localhost:8000")
else:
    print(f"\n✗ Dashboard returned status {resp.status_code}")
