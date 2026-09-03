#!/usr/bin/env python3
"""
Razorpay test-mode payment integration for catalog trust layer.

This module verifies a listing before allowing checkout, and only creates a Razorpay
order for listings that pass the trust-layer decision gate.
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path

import requests

# Ensure project root is importable
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from dotenv import load_dotenv
import razorpay

from audit.audit_log import log_verification
from engine.trust_scoring import load_data
from mcp_tool.verify_listing_trust import verify_listing_trust


def _load_env():
    """Load environment variables from the project .env file."""
    project_root = Path(__file__).resolve().parent.parent
    load_dotenv(project_root / ".env")


def _sanitize_receipt_name(name: str) -> str:
    """Convert a product name into a safe receipt suffix."""
    cleaned = name.lower().strip()
    cleaned = "".join(ch if ch.isalnum() or ch in "-_" else "-" for ch in cleaned)
    return cleaned or "product"


def create_test_order(product_name: str, amount_inr: float) -> dict:
    """
    Create a Razorpay test-mode order for a product.

    Args:
        product_name: Name of the product being purchased.
        amount_inr: price in Indian Rupees.

    Returns:
        A dict with order_id, amount, currency, status on success.
        Returns {"error": ...} on failure.
    """
    try:
        _load_env()

        key_id = os.getenv("RAZORPAY_KEY_ID")
        key_secret = os.getenv("RAZORPAY_KEY_SECRET")

        if not key_id or not key_secret:
            raise ValueError("RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set in the environment.")

        timeout_seconds = 15
        session = requests.Session()
        original_request = session.request

        def request_with_timeout(method, url, *args, **kwargs):
            kwargs.setdefault("timeout", timeout_seconds)
            return original_request(method, url, *args, **kwargs)

        session.request = request_with_timeout

        client = razorpay.Client(auth=(key_id, key_secret), session=session)

        amount_paise = int(round(float(amount_inr) * 100))
        receipt = f"trust-layer-{_sanitize_receipt_name(product_name)}-{datetime.utcnow().strftime('%Y%m%d%H%M%S%f')}"

        response = client.order.create(
            {
                "amount": amount_paise,
                "currency": "INR",
                "receipt": receipt,
            }
        )

        return {
            "order_id": response.get("id"),
            "amount": response.get("amount"),
            "currency": response.get("currency"),
            "status": response.get("status"),
        }
    except requests.exceptions.Timeout as exc:
        return {"error": f"Razorpay request timed out after {timeout_seconds} seconds: {exc}"}
    except Exception as exc:
        return {"error": str(exc)}


def process_verified_purchase(product_id: str) -> dict:
    """
    Verify a listing, block or review unsafe listings, and only create a Razorpay
    order for products that are allowed to proceed.
    """
    verification_result = verify_listing_trust(product_id)

    if "error" in verification_result:
        return verification_result

    action = verification_result.get("action")

    if action in {"BLOCK", "REVIEW"}:
        blocked_result = dict(verification_result)
        blocked_result["purchase_status"] = "BLOCKED_BY_TRUST_LAYER"
        log_verification(blocked_result)
        return blocked_result

    if action in {"ALLOW", "ALLOW_WITH_WARNING"}:
        catalog, _ = load_data()
        product_rows = catalog[catalog["product_id"] == product_id]

        if product_rows.empty:
            verification_result["purchase_status"] = "BLOCKED_BY_TRUST_LAYER"
            log_verification(verification_result)
            return verification_result

        latest_row = product_rows.loc[product_rows["last_updated"].idxmax()]
        product_name = latest_row["product_name"]
        product_price = float(latest_row["price"])

        order_result = create_test_order(product_name, product_price)

        combined = dict(verification_result)
        combined.update(order_result)
        combined["amount"] = product_price

        if "error" in order_result:
            combined["purchase_status"] = "ORDER_FAILED"
        else:
            combined["purchase_status"] = "ORDER_CREATED"

        log_verification(combined)
        return combined

    verification_result["purchase_status"] = "BLOCKED_BY_TRUST_LAYER"
    log_verification(verification_result)
    return verification_result


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python payment/razorpay_client.py <product_id>")
        print("Example: python payment/razorpay_client.py TEST-A")
        print("Example: python payment/razorpay_client.py TEST-D")
        sys.exit(1)

    product_id = sys.argv[1]
    result = process_verified_purchase(product_id)
    print(json.dumps(result, indent=2, default=str))
