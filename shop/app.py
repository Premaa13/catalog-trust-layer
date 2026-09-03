#!/usr/bin/env python3
"""Simple storefront app for the trust-aware product catalog."""

import asyncio
import json
import sys
from pathlib import Path

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from agent.shopping_agent import run_agent
from audit.audit_log import log_verification
from agent_demo import run_agent_flow
from engine.trust_scoring import evaluate_product, load_data
from mcp_tool.verify_listing_trust import verify_listing_trust
from payment.razorpay_client import create_test_order, process_verified_purchase

try:
    from shop.product_images import get_product_image_url
except ImportError:
    from product_images import get_product_image_url

ROOT_DIR = Path(__file__).resolve().parent.parent
IMAGE_CACHE_PATH = ROOT_DIR / "data" / "image_cache.json"


class BuyRequest(BaseModel):
    product_id: str


class AgentShopRequest(BaseModel):
    goal: str


def _load_image_cache():
    try:
        with IMAGE_CACHE_PATH.open("r", encoding="utf-8") as fh:
            return json.load(fh)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}

app = FastAPI(title="Trustmart Storefront")

static_dir = Path(__file__).resolve().parent / "static"
if static_dir.exists():
    app.mount("/static", StaticFiles(directory=str(static_dir)), name="static")


@app.get("/")
async def index():
    return FileResponse(static_dir / "index.html")


async def _build_shop_products_response():
    catalog, orders = load_data()
    unique_products = catalog["product_id"].unique()
    results = []
    test_cases = {"TEST-A", "TEST-B", "TEST-C", "TEST-D"}

    for product_id in unique_products:
        result = evaluate_product(product_id, catalog, orders)
        if "error" in result:
            continue

        product_rows = catalog[catalog["product_id"] == product_id]
        latest_row = product_rows.loc[product_rows["last_updated"].idxmax()]

        category = str(latest_row.get("category", "Uncategorized"))
        product_name = str(latest_row.get("product_name", result.get("product_name", "")))

        result["category"] = category
        result["date_added"] = str(latest_row.get("date_added", ""))
        result["price"] = float(latest_row["price"])
        result["stock"] = int(latest_row["stock"])
        result["image_url"] = get_product_image_url(product_name, category, product_id)
        result["is_test_case"] = product_id in test_cases
        results.append(result)

    test_case_order = {pid: i for i, pid in enumerate(["TEST-A", "TEST-B", "TEST-C", "TEST-D"])}
    results.sort(
        key=lambda x: (
            x["is_test_case"] == False,
            test_case_order.get(x["product_id"], 999),
            x["product_id"],
        )
    )

    return results


@app.get("/api/products")
async def get_products():
    return await _build_shop_products_response()


@app.get("/api/shop/products")
async def get_shop_products():
    return await _build_shop_products_response()


@app.post("/api/buy")
async def buy_product(payload: BuyRequest):
    product_id = payload.product_id.strip()
    catalog, orders = load_data()

    result = evaluate_product(product_id, catalog, orders)
    if "error" in result:
        raise HTTPException(status_code=404, detail=result["error"])

    action = result.get("action", "BLOCK")
    result["purchase_status"] = "BLOCKED_BY_TRUST_LAYER"

    if action in {"ALLOW", "ALLOW_WITH_WARNING"}:
        product_rows = catalog[catalog["product_id"] == product_id]
        if product_rows.empty:
            result["purchase_status"] = "BLOCKED_BY_TRUST_LAYER"
            log_verification(result)
            return {
                "product_id": product_id,
                "status": result.get("status", "FLAGGED"),
                "action": action,
                "trust_score": result.get("trust_score"),
                "reasons": result.get("reasons", []),
                "purchase_status": "BLOCKED_BY_TRUST_LAYER",
                "order_id": None,
                "message": "Product not found. Purchase blocked.",
            }

        latest_row = product_rows.loc[product_rows["last_updated"].idxmax()]
        product_name = str(latest_row.get("product_name", result.get("product_name", product_id)))
        product_price = float(latest_row.get("price", 0.0))

        order_result = await asyncio.to_thread(create_test_order, product_name, product_price)
        result.update(order_result)
        result["purchase_status"] = "ORDER_CREATED" if "order_id" in order_result and order_result.get("order_id") else "ORDER_FAILED"
        result["order_id"] = order_result.get("order_id")
        result["message"] = (
            f"Purchase verified. Razorpay order created successfully. Order ID: {order_result.get('order_id')}"
            if "order_id" in order_result and order_result.get("order_id")
            else "Purchase verified but Razorpay order creation failed."
        )
        log_verification(result)
        return {
            "product_id": product_id,
            "product_name": product_name,
            "status": result.get("status", "VERIFIED"),
            "action": action,
            "trust_score": result.get("trust_score"),
            "reasons": result.get("reasons", []),
            "purchase_status": result["purchase_status"],
            "order_id": result.get("order_id"),
            "message": result["message"],
        }

    result["purchase_status"] = "BLOCKED_BY_TRUST_LAYER"
    result["message"] = "Purchase blocked by the trust layer review policy."
    log_verification(result)
    return {
        "product_id": product_id,
        "product_name": result.get("product_name"),
        "status": result.get("status", "FLAGGED"),
        "action": action,
        "trust_score": result.get("trust_score"),
        "reasons": result.get("reasons", []),
        "purchase_status": "BLOCKED_BY_TRUST_LAYER",
        "order_id": None,
        "message": result["message"],
    }


@app.post("/api/agent/shop")
async def agent_shop(payload: AgentShopRequest):
    goal = (payload.goal or "").strip()
    if not goal:
        raise HTTPException(status_code=400, detail="Goal is required.")
    return await asyncio.to_thread(run_agent_flow, goal, False)


_SENTINEL = object()


def _safe_next(steps):
    try:
        return next(steps)
    except StopIteration:
        return _SENTINEL
    except Exception as exc:
        return {"step": "error", "message": f"Agent execution failed: {exc}"}


async def _stream_steps(steps):
    while True:
        try:
            step = await asyncio.wait_for(asyncio.to_thread(_safe_next, steps), timeout=30)
        except asyncio.TimeoutError:
            yield f"data: {json.dumps({'step': 'error', 'message': 'Agent step timed out while verifying the listing.'})}\n\n"
            break
        except Exception as exc:
            yield f"data: {json.dumps({'step': 'error', 'message': f'Agent execution failed: {exc}'})}\n\n"
            break

        if step is _SENTINEL:
            break

        yield f"data: {json.dumps(step)}\n\n"
        await asyncio.sleep(0.6)


@app.get("/api/agent/run")
async def agent_run(query: str = Query(default="", alias="query")):
    user_query = query or ""

    async def event_stream():
        async for payload in _stream_steps(run_agent(user_query)):
            yield payload

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@app.get("/api/agent/demo")
async def agent_demo():
    async def event_stream():
        demo_steps = [
            {"step": "understanding", "message": "Understanding request: 'demo: skip unsafe then buy safe'"},
            {"step": "parsed", "keyword": "wireless earbuds", "max_price": 15000},
            {"step": "search_results", "count": 2, "candidates": [{"product_name": "Wireless Earbuds Pro", "price": 14683.72, "product_id": "TEST-A"}, {"product_name": "LED Table Lamp", "price": 2310.15, "product_id": "TEST-D"}]},
            {"step": "checking", "product_name": "Wireless Earbuds Pro", "price": 14683.72, "product_id": "TEST-A"},
            {"step": "trust_result", "product_id": "TEST-A", "product_name": "Wireless Earbuds Pro", "trust_score": 50, "action": "REVIEW", "reasons": ["Price increased 70.0% in 2.0 hours (high-risk)"]},
            {"step": "skipping", "message": "Unsafe listing (REVIEW), skipping..."},
            {"step": "checking", "product_name": "LED Table Lamp", "price": 2310.15, "product_id": "TEST-D"},
            {"step": "trust_result", "product_id": "TEST-D", "product_name": "LED Table Lamp", "trust_score": 100, "action": "ALLOW", "reasons": ["Listing is consistent and up to date."]},
            {"step": "purchasing", "message": "Listing verified. Purchasing..."},
            {"step": "purchase_complete", "order_id": "demo-order-001", "product_name": "LED Table Lamp", "amount": 2310.15},
        ]
        async for payload in _stream_steps(iter(demo_steps)):
            yield payload

    return StreamingResponse(event_stream(), media_type="text/event-stream")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8001)
