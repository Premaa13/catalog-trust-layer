#!/usr/bin/env python3
"""
Generate synthetic catalog and orders datasets for the Catalog Trust Layer.

Creates two CSV files in the same folder as this script:
- catalog.csv
- orders.csv

Follows the specification including four planted test cases.
"""

import csv
import random
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

# Fixed seed for reproducibility
SEED = 12345
random.seed(SEED)

# Try to use Faker for realistic product names if available
USE_FAKER = False
try:
    from faker import Faker

    faker = Faker()
    faker.seed_instance(SEED)
    USE_FAKER = True
except Exception:
    USE_FAKER = False

# Fallback product names if Faker is not available
FALLBACK_PRODUCT_NAMES = [
    "Wireless Mouse",
    "Bluetooth Headphones",
    "USB-C Charger",
    "Portable SSD 1TB",
    "Mechanical Keyboard",
    "Smartphone Case",
    "Noise Cancelling Earbuds",
    "4K Monitor 27in",
    "Fitness Tracker",
    "Stainless Steel Water Bottle",
    "Instant Pot",
    "Air Fryer",
    "LED Desk Lamp",
    "Electric Kettle",
    "Gaming Chair",
    "HD Webcam",
    "Smart Light Bulb",
    "Backpack",
    "Travel Suitcase",
    "Running Shoes",
    "Cotton T-Shirt",
    "Denim Jeans",
    "Scented Candle",
    "Coffee Maker",
    "Wireless Router",
    "Action Camera",
    "Bluetooth Speaker",
    "Portable Projector",
    "VR Headset",
    "Graphic Tablet",
    "Waterproof Jacket",
    "Sunglasses",
    "Formal Shirt",
    "Electric Toothbrush",
    "Hair Dryer",
    "Board Game",
    "Cookware Set",
    "Office Chair",
    "Laptop Stand",
    "HDMI Cable",
    "Smartwatch",
    "Baby Stroller",
    "Pet Food 10kg",
    "Garden Hose",
    "LED Strip Lights",
    "Memory Foam Pillow",
    "Camping Tent",
    "Hiking Backpack",
    "Yoga Mat",
    "Noise Machine",
]

# Output files (next to this script)
HERE = Path(__file__).resolve().parent
CATALOG_CSV = HERE / "catalog.csv"
ORDERS_CSV = HERE / "orders.csv"

# Timestamps reference
NOW = datetime.now(timezone.utc)

# Helpers

def iso(ts: datetime) -> str:
    """Return ISO 8601 string for a timezone-aware datetime."""
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=timezone.utc)
    return ts.isoformat()


def product_name(i: int) -> str:
    if USE_FAKER:
        # Use faker commerce or word combinations
        return faker.catch_phrase()
    else:
        # Cycle through fallback list to get variety
        return FALLBACK_PRODUCT_NAMES[i % len(FALLBACK_PRODUCT_NAMES)]


# Collections to be written
catalog_rows = []  # dicts with keys product_id, product_name, category, price, stock, last_updated, date_added
order_rows = []  # dicts with keys order_id, product_id, quantity, timestamp, status

CATEGORY_CONFIG = {
    "Electronics": {
        "min_price": 800,
        "max_price": 15000,
        "names": [
            "Wireless Earbuds Pro",
            "Smart Fitness Band",
            "Bluetooth Speaker",
            "USB-C Fast Charger",
            "4K Smart Display",
            "Noise Cancelling Headphones",
            "Portable SSD 1TB",
            "Mechanical Keyboard",
            "Smart Watch",
            "Gaming Controller",
            "Wireless Mouse",
            "HD Webcam",
            "Smart Lamp",
        ],
    },
    "Fashion": {
        "min_price": 300,
        "max_price": 4000,
        "names": [
            "Cotton Casual Shirt",
            "Running Shoes",
            "Leather Wallet",
            "Denim Jacket",
            "Classic Formal Trouser",
            "Trail Running Cap",
            "Canvas Tote Bag",
            "Woven Belt",
            "Slim Fit Polo",
            "Winter Knit Sweater",
            "Travel Backpack",
            "Leather Sling Bag",
            "Cotton Crew Socks",
        ],
    },
    "Grocery": {
        "min_price": 50,
        "max_price": 800,
        "names": [
            "Organic Basmati Rice 5kg",
            "Cold Pressed Coconut Oil",
            "Assorted Dry Fruits Pack",
            "Green Tea Bags",
            "Whole Wheat Atta 5kg",
            "Spanish Almond Mix",
            "Herbal Chai Blend",
            "Natural Honey Jar",
            "Premium Coffee Beans",
            "Coconut Water Pack",
            "Instant Oatmeal Box",
            "Turmeric & Ginger Tea",
            "Masala Spice Set",
        ],
    },
    "Home & Kitchen": {
        "min_price": 200,
        "max_price": 5000,
        "names": [
            "Non-Stick Frying Pan",
            "LED Table Lamp",
            "Cotton Bedsheet Set",
            "Steel Water Bottle",
            "Glass Food Storage Jar",
            "Ceramic Dinner Set",
            "Air Purifier",
            "Electric Kettle",
            "Memory Foam Pillow",
            "Kitchen Organizer Rack",
            "Vacuum Flask",
            "Bamboo Storage Basket",
            "Portable Blender",
        ],
    },
}


def category_for_index(idx):
    distribution = [
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
        "Electronics", "Fashion", "Grocery", "Home & Kitchen",
    ]
    return distribution[idx % len(distribution)]


def make_date_added(is_new_arrival=False):
    if is_new_arrival:
        # Within the last 3 days
        days_ago = random.randint(0, 2)
        hours_ago = random.randint(0, 23)
        minutes_ago = random.randint(0, 59)
        return NOW - timedelta(days=days_ago, hours=hours_ago, minutes=minutes_ago)

    # Older than 3 days, spread further back
    days_ago = random.randint(4, 180)
    hours_ago = random.randint(0, 23)
    minutes_ago = random.randint(0, 59)
    return NOW - timedelta(days=days_ago, hours=hours_ago, minutes=minutes_ago)


def random_price_for_category(category):
    cfg = CATEGORY_CONFIG[category]
    return round(random.uniform(cfg["min_price"], cfg["max_price"]), 2)


def product_name_for_category(category, idx):
    names = CATEGORY_CONFIG[category]["names"]
    return names[idx % len(names)]

# 1) Generate 50 normal/random products
NORMAL_COUNT = 50
normal_products = []
new_arrival_index = sorted(random.sample(range(NORMAL_COUNT), 9))

for i in range(1, NORMAL_COUNT + 1):
    pid = f"PROD-{i:04d}"
    category = category_for_index(i - 1)
    name = product_name_for_category(category, i - 1)
    price = random_price_for_category(category)
    stock = random.randint(10, 200)
    is_new_arrival = (i - 1) in new_arrival_index
    last_updated = make_date_added(is_new_arrival)
    date_added = last_updated

    normal_products.append({
        "product_id": pid,
        "product_name": name,
        "category": category,
        "price": price,
        "stock": stock,
        "last_updated": last_updated,
        "date_added": date_added,
    })

# Add normal products to catalog_rows
for p in normal_products:
    catalog_rows.append({
        "product_id": p["product_id"],
        "product_name": p["product_name"],
        "category": p["category"],
        "price": f"{p['price']:.2f}",
        "stock": str(p["stock"]),
        "last_updated": iso(p["last_updated"]),
        "date_added": iso(p["date_added"]),
    })

# 2) PLANTED TEST CASES
# ==================== PLANTED TEST CASES ====================
# Create four specific products (TEST-A .. TEST-D) as described

# TEST-A — Price Spike Test
# product_id: TEST-A
# category: Electronics, realistic price range
old_ts_a = NOW - timedelta(hours=2)
new_ts_a = old_ts_a + timedelta(hours=2)
price_old_a = round(random.uniform(800.0, 15000.0), 2)
price_new_a = round(price_old_a * 1.7, 2)
stock_a = random.randint(10, 200)
category_a = "Electronics"
new_arrival_a = random.choice([True, False])

catalog_rows.append({
    "product_id": "TEST-A",
    "product_name": "Wireless Earbuds Pro",
    "category": category_a,
    "price": f"{price_old_a:.2f}",
    "stock": str(stock_a),
    "last_updated": iso(old_ts_a),
    "date_added": iso(make_date_added(new_arrival_a)),
})

catalog_rows.append({
    "product_id": "TEST-A",
    "product_name": "Wireless Earbuds Pro",
    "category": category_a,
    "price": f"{price_new_a:.2f}",
    "stock": str(stock_a),
    "last_updated": iso(new_ts_a),
    "date_added": iso(make_date_added(new_arrival_a)),
})

# TEST-B — Stock Mismatch Test
# category: Grocery
catalog_rows.append({
    "product_id": "TEST-B",
    "product_name": "Organic Basmati Rice 5kg",
    "category": "Grocery",
    "price": f"{round(random.uniform(50.0, 800.0), 2):.2f}",
    "stock": "50",
    "last_updated": iso(NOW - timedelta(minutes=30)),
    "date_added": iso(make_date_added(random.choice([True, False]))),
})

# Create multiple orders summing to 65 (e.g., 20, 20, 25)
test_b_order_quantities = [20, 20, 25]
for q in test_b_order_quantities:
    seconds_ago = random.randint(0, 2 * 3600)
    ts = NOW - timedelta(seconds=seconds_ago)
    order_rows.append({
        "order_id": f"ORD-{uuid.uuid4().hex[:8].upper()}",
        "product_id": "TEST-B",
        "quantity": str(q),
        "timestamp": iso(ts),
        "status": "completed",
    })

# TEST-C — Stale Listing Test
# category: Fashion
catalog_rows.append({
    "product_id": "TEST-C",
    "product_name": "Cotton Casual Shirt",
    "category": "Fashion",
    "price": f"{round(random.uniform(300.0, 4000.0), 2):.2f}",
    "stock": str(random.randint(10, 200)),
    "last_updated": iso(NOW - timedelta(days=5)),
    "date_added": iso(make_date_added(False)),
})

# TEST-D — Normal Control
# category: Home & Kitchen
catalog_rows.append({
    "product_id": "TEST-D",
    "product_name": "LED Table Lamp",
    "category": "Home & Kitchen",
    "price": f"{round(random.uniform(200.0, 5000.0), 2):.2f}",
    "stock": str(random.randint(10, 200)),
    "last_updated": iso(NOW - timedelta(hours=random.randint(0, 23), minutes=random.randint(0, 59))),
    "date_added": iso(make_date_added(random.choice([True, False]))),
})

# ==================== END PLANTED TEST CASES ====================

# 3) Generate realistic completed orders for the normal products
order_counter = 0

for p in normal_products:
    pid = p["product_id"]
    stock = p["stock"]
    # generate between 0 and 4 orders, but ensure total ordered <= stock
    max_orders = random.randint(0, 4)
    remaining = stock
    for _ in range(max_orders):
        if remaining <= 0:
            break
        # quantity between 1 and min(5, remaining)
        q = random.randint(1, min(5, remaining))
        remaining -= q
        # spread orders across last 7 days
        seconds_ago = random.randint(0, 7 * 24 * 3600)
        ts = NOW - timedelta(seconds=seconds_ago)
        order_rows.append({
            "order_id": f"ORD-{uuid.uuid4().hex[:8].upper()}",
            "product_id": pid,
            "quantity": str(q),
            "timestamp": iso(ts),
            "status": "completed",
        })

# Ensure TEST-D has no anomalous orders (no orders created above for it). If it was accidentally included in normal_products, override: remove its orders.
order_rows = [o for o in order_rows if o["product_id"] != "TEST-D"]

# Shuffle rows a bit so catalog snapshots are not perfectly ordered
random.shuffle(catalog_rows)
random.shuffle(order_rows)

# Write CSV files
CATALOG_FIELDS = ["product_id", "product_name", "category", "price", "stock", "last_updated", "date_added"]
ORDER_FIELDS = ["order_id", "product_id", "quantity", "timestamp", "status"]

HERE.mkdir(parents=True, exist_ok=True)

with CATALOG_CSV.open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=CATALOG_FIELDS)
    writer.writeheader()
    for row in catalog_rows:
        writer.writerow(row)

with ORDERS_CSV.open("w", newline="", encoding="utf-8") as f:
    writer = csv.DictWriter(f, fieldnames=ORDER_FIELDS)
    writer.writeheader()
    for row in order_rows:
        writer.writerow(row)

# Summary
unique_product_ids = set(r["product_id"] for r in catalog_rows)
normal_count = NORMAL_COUNT
planted_count = 4

print("Synthetic dataset generated successfully.\n")
print(f"Normal products: {normal_count}")
print(f"Planted test products: {planted_count}")
print(f"Total unique products: {len(unique_product_ids)}")
print(f"Catalog rows: {len(catalog_rows)}")
print(f"Order rows: {len(order_rows)}\n")
print("PLANTED TEST CASES:")
print("✓ TEST-A — Price Spike Test")
print("✓ TEST-B — Stock Mismatch Test")
print("✓ TEST-C — Stale Listing Test")
print("✓ TEST-D — Normal Control\n")

print("Output files:")
print(str(CATALOG_CSV.resolve()))
print(str(ORDERS_CSV.resolve()))

# Quick validation checks (informational; do not abort generation)
try:
    # TEST-A two snapshots exactly 2 hours apart
    ta_rows = [r for r in catalog_rows if r["product_id"] == "TEST-A"]
    if len(ta_rows) != 2:
        print("WARNING: TEST-A does not have exactly 2 catalog snapshots.")
    else:
        t0 = datetime.fromisoformat(ta_rows[0]["last_updated"])  # aware
        t1 = datetime.fromisoformat(ta_rows[1]["last_updated"])  # aware
        diff = abs((t1 - t0).total_seconds())
        if diff != 2 * 3600:
            print("WARNING: TEST-A snapshots are not exactly 2 hours apart.")

    # TEST-B stock mismatch
    tb_catalog = [r for r in catalog_rows if r["product_id"] == "TEST-B"]
    if tb_catalog:
        stock_b = int(tb_catalog[0]["stock"])
        tb_orders = [int(o["quantity"]) for o in order_rows if o["product_id"] == "TEST-B" and o["status"] == "completed"]
        if sum(tb_orders) != 65 or stock_b != 50:
            print("WARNING: TEST-B planted quantities/stock do not match the requested values.")

    # TEST-C stale listing exactly 5 days old
    tc = [r for r in catalog_rows if r["product_id"] == "TEST-C"]
    if tc:
        ttc = datetime.fromisoformat(tc[0]["last_updated"]) 
        if (NOW - ttc).days != 5:
            print("WARNING: TEST-C last_updated is not exactly 5 days before generation time.")
except Exception:
    # Do not fail generation for these checks
    pass
