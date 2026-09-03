#!/usr/bin/env python3
"""Fetch and cache product thumbnails once from DummyJSON."""

import json
from pathlib import Path
from urllib.parse import quote

import requests

ROOT = Path(__file__).resolve().parent.parent
CACHE_PATH = ROOT / "data" / "image_cache.json"

PRODUCT_SEARCHES = {
    "Trail Running Cap": "cap",
    "Travel Backpack": "backpack",
    "Cotton Casual Shirt": "shirt",
    "Gaming Controller": "controller",
    "Leather Sling Bag": "bag",
    "Organic Basmati Rice 5kg": "rice",
    "Cotton Bedsheet Set": "bedsheet",
    "Electric Kettle": "kettle",
    "Assorted Dry Fruits Pack": "nuts",
    "Cold Pressed Coconut Oil": "oil",
    "LED Table Lamp": "lamp",
    "Woven Belt": "belt",
    "Herbal Chai Blend": "tea",
    "Steel Water Bottle": "bottle",
    "Mechanical Keyboard": "keyboard",
    "Memory Foam Pillow": "pillow",
    "Bamboo Storage Basket": "basket",
    "Smart Fitness Band": "watch",
    "Wireless Earbuds Pro": "airpods",
    "Ceramic Dinner Set": "plate",
    "Noise Cancelling Headphones": "headphones",
    "Denim Jacket": "jacket",
    "Air Purifier": "purifier",
    "Classic Formal Trouser": "trouser",
    "Green Tea Bags": "tea",
    "Natural Honey Jar": "honey",
    "Glass Food Storage Jar": "jar",
    "Wireless Mouse": "mouse",
    "Non-Stick Frying Pan": "pan",
    "Portable SSD 1TB": "ssd",
    "Winter Knit Sweater": "sweater",
    "Bluetooth Speaker": "speaker",
    "Running Shoes": "sports shoes",
    "HD Webcam": "webcam",
    "Masala Spice Set": "spice",
    "Leather Wallet": "wallet",
    "Kitchen Organizer Rack": "rack",
    "Whole Wheat Atta 5kg": "flour",
    "USB-C Fast Charger": "charger",
    "Instant Oatmeal Box": "oatmeal",
    "Canvas Tote Bag": "tote bag",
    "4K Smart Display": "tv",
    "Cotton Crew Socks": "socks",
    "Premium Coffee Beans": "coffee",
    "Smart Lamp": "lamp",
    "Spanish Almond Mix": "almond",
    "Slim Fit Polo": "polo shirt",
    "Vacuum Flask": "flask",
    "Smart Watch": "watch",
    "Coconut Water Pack": "coconut water",
}


def search_dummyjson(query: str):
    encoded = quote(query)
    url = f"https://dummyjson.com/products/search?q={encoded}&limit=1"
    response = requests.get(url, timeout=20)
    response.raise_for_status()
    payload = response.json()
    products = payload.get("products") or []
    if not products:
        return None
    first = products[0]
    return first.get("thumbnail") or first.get("images", [None])[0]


def product_search_variants(product_name: str, fallback_term: str):
    seen = set()
    candidates = []
    for candidate in [product_name, fallback_term, product_name.split()[0], product_name.split()[-1]]:
        if not candidate:
            continue
        key = candidate.strip().lower()
        if key and key not in seen:
            seen.add(key)
            candidates.append(candidate.strip())
    return candidates


def resolve_image(product_name: str, fallback_term: str):
    for query in product_search_variants(product_name, fallback_term):
        try:
            image_url = search_dummyjson(query)
        except Exception:
            image_url = None
        if image_url:
            return image_url
    return None


def main():
    image_cache = {}
    failed = []

    for product_name, fallback_term in PRODUCT_SEARCHES.items():
        image_url = resolve_image(product_name, fallback_term)
        if image_url:
            image_cache[product_name] = image_url
        else:
            failed.append(product_name)

    CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
    with CACHE_PATH.open("w", encoding="utf-8") as cache_file:
        json.dump(image_cache, cache_file, ensure_ascii=False, indent=2)
        cache_file.write("\n")

    total_products = len(PRODUCT_SEARCHES)
    success_count = len(image_cache)
    print(f"Saved {success_count}/{total_products} real product thumbnails to {CACHE_PATH.name}")
    print(f"Matched successfully: {success_count}")
    print(f"Failed to find a match: {len(failed)}")
    if failed:
        print("Failed products:")
        for product_name in failed:
            print(f"  - {product_name}")


if __name__ == "__main__":
    main()
