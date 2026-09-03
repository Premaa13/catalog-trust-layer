import re
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from agent.intent_parser import parse_intent
from engine.trust_scoring import load_data
from mcp_tool.verify_listing_trust import verify_listing_trust


def _normalize_text(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", (value or "").lower()).strip()


def _candidate_relevance(keyword: str, product_name: str, category: str) -> int:
    keyword_text = _normalize_text(keyword)
    product_text = _normalize_text(product_name)
    category_text = _normalize_text(category)
    keyword_tokens = keyword_text.split()
    product_tokens = product_text.split()

    if keyword_text and keyword_text in product_text:
        return 4
    if keyword_tokens and all(token in product_tokens for token in keyword_tokens):
        return 3
    if keyword_tokens and any(token in category_text for token in keyword_tokens):
        return 2
    if keyword_tokens and any(token in product_text or token in category_text for token in keyword_tokens):
        return 1
    return 0


def search_candidates(keyword: str, max_price: float | None) -> list[dict]:
    keyword = (keyword or "").strip()
    tokens = [token for token in re.split(r"[^a-z0-9]+", keyword.lower()) if token]
    if not tokens:
        return []

    catalog, _ = load_data()
    matches = []

    for product_id in catalog["product_id"].drop_duplicates():
        rows = catalog[catalog["product_id"] == product_id]
        latest_row = rows.loc[rows["last_updated"].idxmax()]
        product_name = str(latest_row.get("product_name", ""))
        category = str(latest_row.get("category", ""))
        price = float(latest_row.get("price", 0.0))

        if max_price is not None and price > float(max_price):
            continue

        haystack = _normalize_text(f"{product_name} {category}")
        relevance = _candidate_relevance(keyword, product_name, category)
        if relevance:
            matches.append({
                "product_id": product_id,
                "product_name": product_name,
                "price": price,
                "_relevance": relevance,
            })

    matches.sort(key=lambda item: (-item["_relevance"], item["price"], item["product_id"]))
    for item in matches:
        item.pop("_relevance", None)
    return matches


def run_agent(user_query: str):
    query = user_query or ""
    yield {"step": "understanding", "message": f"Understanding request: '{query}'"}

    intent = parse_intent(query)
    yield {"step": "parsed", "keyword": intent.get("keyword"), "max_price": intent.get("max_price")}
    candidates = search_candidates(intent.get("keyword", ""), intent.get("max_price"))
    yield {
        "step": "search_results",
        "count": len(candidates),
        "candidates": [
            {"product_name": item["product_name"], "price": item["price"], "product_id": item["product_id"]}
            for item in candidates
        ],
    }

    if not candidates:
        yield {"step": "no_results", "message": "No matching products found"}
        return

    safe_candidates = []
    excluded_count = 0

    for index, candidate in enumerate(candidates, start=1):
        product_name = candidate["product_name"]
        price = candidate["price"]
        product_id = candidate["product_id"]

        yield {"step": "checking", "product_name": product_name, "price": price, "product_id": product_id, "index": index}

        print(f"[AGENT] VERIFYING {product_id}", flush=True)
        trust_result = verify_listing_trust(product_id)
        print(f"[AGENT] TRUST RESULT: {trust_result}", flush=True)
        action = trust_result.get("action", "BLOCK")
        reasons = trust_result.get("reasons", []) or ["No reason provided."]

        yield {
            "step": "trust_result",
            "product_id": product_id,
            "product_name": product_name,
            "trust_score": trust_result.get("trust_score"),
            "action": action,
            "reasons": reasons,
        }

        if action in {"REVIEW", "BLOCK"}:
            excluded_count += 1
            yield {"step": "skipping", "message": f"Unsafe listing ({action}), skipping..."}
            continue

        safe_candidates.append({
            "product_id": product_id,
            "product_name": product_name,
            "price": price,
            "trust_score": trust_result.get("trust_score"),
            "status": trust_result.get("status"),
            "action": action,
            "reasons": reasons,
        })

    if safe_candidates:
        yield {
            "step": "search_complete",
            "count": len(candidates),
            "excluded_count": excluded_count,
            "recommendations": safe_candidates,
        }
    else:
        yield {"step": "no_safe_options", "message": "No trustworthy listings found matching your request", "excluded_count": excluded_count}
