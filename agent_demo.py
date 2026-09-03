#!/usr/bin/env python3
"""Autonomous shopping agent demo with trust gating."""

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from audit.audit_log import log_verification
from engine.trust_scoring import load_data
from mcp_tool.verify_listing_trust import verify_listing_trust
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
        "buy", "the", "cheapest", "trustworthy", "under", "upto", "up", "to", "a", "an", "for",
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
    catalog, orders = load_data()
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
        return catalog, orders, candidates

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
    return catalog, orders, candidates


def _base_agent_result(goal: str, demo_mode: bool = False):
    keyword, max_price = parse_goal(goal)
    if demo_mode:
        keyword = "wireless"
        max_price = 15000.0

    catalog, orders, candidates = find_candidates(keyword, max_price, demo_mode=demo_mode)
    if demo_mode:
        demo_order = ["TEST-A", "PROD-0001"]
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
    candidates.sort(key=lambda item: (item["price"], item["product_id"]))
    return {"goal": goal, "keyword": keyword, "max_price": max_price, "catalog": catalog, "orders": orders, "candidates": candidates, "demo_mode": demo_mode}


def run_agent_flow(goal: str, demo_mode: bool = False):
    agent_data = _base_agent_result(goal, demo_mode=demo_mode)
    events = [
        {"type": "goal", "message": f"User goal received: {goal}"},
        {"type": "search", "message": "Searching the catalog for matching products..."},
    ]

    candidates = agent_data["candidates"]
    events.append({"type": "search_result", "message": f"Found {len(candidates)} matching product(s)."})

    if not candidates:
        final_result = {
            "goal": goal,
            "keyword": agent_data["keyword"],
            "max_price": agent_data["max_price"],
            "purchase_status": "BLOCKED_BY_TRUST_LAYER",
            "message": "No matching products found for the requested goal.",
        }
        events.append({"type": "purchase_blocked", "message": "No matching products were found. Purchase not completed."})
        return {"goal": goal, "events": events, "final_result": final_result}

    for candidate in candidates:
        product_id = candidate["product_id"]
        product_name = candidate["product_name"]
        price = candidate["price"]
        events.append({
            "type": "candidate",
            "product_id": product_id,
            "product_name": product_name,
            "price": price,
            "message": f"Candidate: {product_name} ({product_id}) at ₹{price:,.2f}"
        })

        trust_result = verify_listing_trust(product_id)
        if "error" in trust_result:
            events.append({
                "type": "agent_decision",
                "message": f"Trust check error for {product_id}: {trust_result['error']}. Skipping candidate.",
            })
            continue

        action = trust_result.get("action", "BLOCK")
        reasons = trust_result.get("reasons", []) or ["No reason provided."]
        events.append({
            "type": "trust_check",
            "product_id": product_id,
            "product_name": product_name,
            "trust_score": trust_result.get("trust_score"),
            "action": action,
            "reasons": reasons,
            "message": f"Trust check completed for {product_id}. Action: {action}."
        })

        if action in {"BLOCK", "REVIEW"}:
            events.append({
                "type": "agent_decision",
                "message": "Unsafe listing detected. Skipping candidate and evaluating the next product.",
            })
            continue

        events.append({
            "type": "purchase",
            "message": "Creating Razorpay test-mode order for the trusted listing..."
        })
        order = create_test_order(product_name, price)
        if "error" in order:
            final_result = {**trust_result, **order, "purchase_status": "ORDER_FAILED", "message": order["error"]}
            events.append({
                "type": "purchase_failed",
                "message": f"Payment failed for {product_name}: {order['error']}",
            })
            return {"goal": goal, "events": events, "final_result": final_result}

        final_result = {**trust_result, **order, "purchase_status": "ORDER_CREATED", "message": f"Purchase verified. Razorpay order created successfully. Order ID: {order.get('order_id')}"}
        log_verification(final_result)
        events.append({
            "type": "purchase_complete",
            "product_id": product_id,
            "product_name": product_name,
            "order_id": order.get("order_id"),
            "amount": float(price),
            "trust_score": trust_result.get("trust_score"),
            "status": trust_result.get("status"),
            "message": f"Purchase complete for {product_name}. Razorpay Order ID: {order.get('order_id')}"
        })
        return {"goal": goal, "events": events, "final_result": final_result}

    final_result = {
        "goal": goal,
        "keyword": agent_data["keyword"],
        "max_price": agent_data["max_price"],
        "purchase_status": "BLOCKED_BY_TRUST_LAYER",
        "message": "The agent evaluated all matching listings and no trustworthy listing passed the Trust Layer.",
    }
    events.append({
        "type": "purchase_blocked",
        "message": "No trustworthy listing passed the Trust Layer. No payment was created.",
    })
    return {"goal": goal, "events": events, "final_result": final_result}


def run_agent(goal: str, demo_mode: bool = False):
    result = run_agent_flow(goal, demo_mode=demo_mode)
    print_section("=== USER GOAL ===")
    print(goal)
    print(f"Parsed keyword: '{result['final_result'].get('keyword', result.get('goal'))}' if available")

    for event in result["events"]:
        event_type = event.get("type")
        message = event.get("message", "")
        if event_type == "candidate":
            print(f"\nCandidate: {event.get('product_name')} — ₹{event.get('price', 0):,.2f} — {event.get('product_id')}")
        elif event_type == "trust_check":
            print(f"Trust Score: {event.get('trust_score')} | Action: {event.get('action')} | Reasons: {'; '.join(event.get('reasons', []))}")
        else:
            print(f"[{event_type}] {message}")

    final_result = result["final_result"]
    print_section("=== FINAL RESULT ===")
    print(final_result)
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Autonomous AI shopping agent demo")
    parser.add_argument("goal", nargs="*", help="Shopping goal, e.g. buy a trustworthy wireless product under 15000")
    parser.add_argument("--demo", action="store_true", help="Run the deterministic bad-listing-then-good-listing demo")
    args = parser.parse_args()

    goal_text = " ".join(args.goal).strip()
    if not goal_text and not args.demo:
        print("Usage: python agent_demo.py \"buy a trustworthy wireless product under 15000\"")
        sys.exit(1)

    if args.demo:
        goal_text = "buy a wireless product under 15000"

    run_agent(goal_text, demo_mode=args.demo)
