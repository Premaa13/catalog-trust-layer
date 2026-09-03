#!/usr/bin/env python3
"""
MCP Tool: Verify Listing Trust

A single-product trust scoring tool designed to be called by an AI buyer agent
before acting on a catalog listing. Follows the MCP pattern with a clear
structured input/output contract.

Usage:
    from mcp_tool.verify_listing_trust import verify_listing_trust
    
    result = verify_listing_trust("TEST-A")
    print(result)
    
Or from command line:
    python mcp_tool/verify_listing_trust.py TEST-A
"""

import sys
import json
from pathlib import Path

# Add parent directory to path so we can import from engine/
sys.path.insert(0, str(Path(__file__).parent.parent))

from engine.trust_scoring import load_data, evaluate_product
from audit.audit_log import log_verification


def verify_listing_trust(product_id: str) -> dict:
    """
    Verify the trust score for a single product listing.
    
    This function loads the catalog and orders data, then runs the complete
    trust scoring pipeline (all 3 checks + safety overrides) for the specified
    product.
    
    Args:
        product_id: The product ID to verify (e.g., "TEST-A", "PROD-0001")
    
    Returns:
        A dictionary with the exact shape:
        {
            "product_id": str,
            "product_name": str,
            "trust_score": int (0-100),
            "status": "VERIFIED" or "FLAGGED",
            "action": "ALLOW" | "ALLOW_WITH_WARNING" | "REVIEW" | "BLOCK",
            "reasons": [str, str, ...]
        }
        
        Or if product not found:
        {
            "error": "product_id not found"
        }
    """
    
    # Load catalog and orders data
    catalog, orders = load_data()
    
    # Evaluate the product using the shared scoring pipeline
    result = evaluate_product(product_id, catalog, orders)
    
    # Log the verification result for audit trail
    log_verification(result)
    
    return result


if __name__ == "__main__":
    # Simple CLI interface for testing
    if len(sys.argv) < 2:
        print("Usage: python verify_listing_trust.py <product_id>")
        print("Example: python verify_listing_trust.py TEST-A")
        sys.exit(1)
    
    product_id = sys.argv[1]
    result = verify_listing_trust(product_id)
    
    # Pretty print the result
    print(json.dumps(result, indent=2, default=str))
