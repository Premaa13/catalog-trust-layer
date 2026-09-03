#!/usr/bin/env python3
"""Autonomous shopping agent demo without the trust layer safety gate."""

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from audit.audit_log import log_verification
from engine.trust_scoring import load_data
from payment.razorpay_client import create_test_order


def print_section(title: str) -> None:
    print("\n" + "=" * 80)
    print(title)
    print("=" * 80)


def parse_goal(goal: str):
    text = goal.lower().strip()
    if not text:
        raise ValueError("No purchase goal provided.")

    price_match = re.search(r"(?:under|upto|up to|<=|less than|budget|max|max price)\s*₹?\s*(\d+(?:,\d{3})*(?:\.\d+)?)", text)
    if not price_match:
        price_match = re.search(r"(?:under|upto|up to|<=|less than|budget|max|max price)\s*(\d+(?:,\d{3})*(?:\.\d+)?)", text)
    if not price_match:
        price_match = re.search(r"(\d+(?:,\d{3})*(?:\.\d+)?)\s*(?:rupees|inr|rs)", text)

    max_price = 0.0
    if price_match:
        max_price = float(price_match.group(1).replace(",", ""))

    stop_words = {
        "buy", "the", "cheapest", "under", "upto", "up", "to", "a", "an", "for",
        "with", "without", "please", "goal", "want", "find", "get", "i", "need",
        "inr", "rs", "rupees", "price", "max", "budget", "less", "than",
        "cheap", "best", "purchase", "product", "products"
    }
    tokens = re.findall(r"[a-z0-9]+", text)
    keyword_tokens = [token for token in tokens if token not in stop_words and not re.fullmatch(r"\d+", token)]
    keyword = " ".join(keyword_tokens).strip() or "product"

    return keyword, max_price


def normalize_match_text(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def goal_matches(goal_keyword: str, product_name: str, category: str) -> bool:
    if not goal_keyword or goal_keyword == "product":
        return True

    goal_tokens = [token for token in normalize_match_text(goal_keyword).split() if token]
    if not goal_tokens:
        return True

    haystack = normalize_match_text(f"{product_name} {category}")
    return all(token in haystack for token in goal_tokens)


def find_candidates(goal_keyword: str, max_price: float, demo_mode: bool = False):
    catalog, _ = load_data()
    candidates = []

    if demo_mode:
        demo_ids = ["TEST-A", "PROD-0001"]
        for product_id in demo_ids:
            rows = catalog[catalog["product_id"] == product_id]
            if rows.empty:
                continue
            latest_row = rows.loc[rows["last_updated"].idxmax()]
            product_name = str(latest_row.get("product_name", ""))
            category = str(latest_row.get("category", ""))
            price = float(latest_row.get("price", 0.0))
            if not goal_matches(goal_keyword, product_name, category):
                continue
            candidates.append({
                "product_id": product_id,
                "product_name": product_name,
                "category": category,
                "price": price,
            })
        return candidates

    for product_id in catalog["product_id"].drop_duplicates():
        rows = catalog[catalog["product_id"] == product_id]
        latest_row = rows.loc[rows["last_updated"].idxmax()]
        product_name = str(latest_row.get("product_name", ""))
        category = str(latest_row.get("category", ""))
        price = float(latest_row.get("price", 0.0))

        if max_price and price > max_price:
            continue
        if not goal_matches(goal_keyword, product_name, category):
            continue

        candidates.append({
            "product_id": product_id,
            "product_name": product_name,
            "category": category,
            "price": price,
        })

    candidates.sort(key=lambda item: (item["price"], item["product_id"]))
    return candidates


def run_agent_without_trust(goal: str, demo_mode: bool = False):
    print_section("=== USER GOAL (NO TRUST LAYER) ===")
    print(goal)

    keyword, max_price = parse_goal(goal)
    print(f"Parsed keyword: '{keyword}'")
    print(f"Price cap: ₹{max_price:,.2f}" if max_price else "Price cap: no explicit max")

    if demo_mode:
        print_section("=== DEMO MODE ===")
        print("Deterministic comparison: trust layer skipped, cheapest valid match wins.")
        print("Goal: buy a wireless product under 15000")
        keyword = "wireless"
        max_price = 15000.0

    candidates = find_candidates(keyword, max_price, demo_mode=demo_mode)

    if demo_mode:
        demo_order = ["PROD-0001", "TEST-A"]
        demo_candidates = []
        seen = set()
        for product_id in demo_order:
            for candidate in candidates:
                if candidate["product_id"] == product_id:
                    demo_candidates.append(candidate)
                    seen.add(product_id)
                    break
        for candidate in candidates:
            if candidate["product_id"] not in seen:
                demo_candidates.append(candidate)
        candidates = demo_candidates

    if not candidates:
        print_section("=== AGENT SEARCH ===")
        print("No matching products found under the goal constraints.")
        return

    print_section("=== AGENT SEARCH ===")
    print(f"Found {len(candidates)} matching candidate(s), ranked by price.")

    chosen = candidates[0]
    product_id = chosen["product_id"]
    product_name = chosen["product_name"]
    price = chosen["price"]

    print(f"\nCandidate: {product_name}")
    print(f"Price: ₹{price:,.2f}")
    print(f"Product ID: {product_id}")
    print('Agent decision: "Trust layer is disabled for this baseline comparison, so I will buy the cheapest matching listing."')

    order = create_test_order(product_name, price)
    if "error" in order:
        print(f"\n⚠️ Payment failed: {order['error']}")
        return

    log_verification({
        "product_id": product_id,
        "product_name": product_name,
        "trust_score": 0,
        "status": "VERIFIED",
        "action": "ALLOW",
        "reasons": ["TRUST LAYER DISABLED FOR DEMO COMPARISON"],
        "purchase_status": "ORDER_CREATED",
        "order_id": order.get("order_id"),
        "message": "Trust layer skipped for the no-trust demo run.",
    })

    print_section("=== 💳 PURCHASE COMPLETE ===")
    print(f"Product: {product_name}")
    print(f"Amount: ₹{price:,.2f}")
    print(f"Razorpay Test Order ID: {order.get('order_id')}")
    print(f"Status: {order.get('status')}")
    print("==================================================")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Autonomous shopping agent baseline without trust checks")
    parser.add_argument("goal", nargs="*", help="Shopping goal, e.g. buy the cheapest wireless earbuds under 5000")
    parser.add_argument("--demo", action="store_true", help="Run the deterministic no-trust comparison demo")
    args = parser.parse_args()

    goal_text = " ".join(args.goal).strip()
    if not goal_text and not args.demo:
        print("Usage: python agent_demo_no_trust_layer.py \"buy the cheapest wireless earbuds under 5000\"")
        sys.exit(1)

    if args.demo:
        goal_text = "buy a wireless product under 15000"

    run_agent_without_trust(goal_text, demo_mode=args.demo)
