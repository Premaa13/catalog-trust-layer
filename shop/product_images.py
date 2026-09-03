"""Curated product image URLs for the Trustmart storefront."""

from urllib.parse import quote

# Unsplash product photos (cropped, object-focused — not lifestyle/room scenes).
PRODUCT_IMAGE_URLS = {
    "4K Smart Display": "https://images.unsplash.com/photo-1593359675080-e513d9b9da1c?auto=format&fit=crop&w=480&h=480&q=80",
    "Air Purifier": "https://images.unsplash.com/photo-1585771724684-38269b663e63?auto=format&fit=crop&w=480&h=480&q=80",
    "Assorted Dry Fruits Pack": "https://images.unsplash.com/photo-1599599816157-7d4e9e4e8f2e?auto=format&fit=crop&w=480&h=480&q=80",
    "Bamboo Storage Basket": "https://images.unsplash.com/photo-1610701596007-11502861dcfa?auto=format&fit=crop&w=480&h=480&q=80",
    "Bluetooth Speaker": "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=480&h=480&q=80",
    "Ceramic Dinner Set": "https://images.unsplash.com/photo-1578749556568-bc2c40e68b24?auto=format&fit=crop&w=480&h=480&q=80",
    "Classic Formal Trouser": "https://images.unsplash.com/photo-1473966968604-fa801b869a1a?auto=format&fit=crop&w=480&h=480&q=80",
    "Coconut Water Pack": "https://images.unsplash.com/photo-1584464491033-06628f3a6b7b?auto=format&fit=crop&w=480&h=480&q=80",
    "Cold Pressed Coconut Oil": "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?auto=format&fit=crop&w=480&h=480&q=80",
    "Cotton Bedsheet Set": "https://images.unsplash.com/photo-1631049303724-416f837ad31b?auto=format&fit=crop&w=480&h=480&q=80",
    "Cotton Casual Shirt": "https://images.unsplash.com/photo-1596755094514-f87e34085b2c?auto=format&fit=crop&w=480&h=480&q=80",
    "Cotton Crew Socks": "https://images.unsplash.com/photo-1586350977773-b3b7ab4a7dcc?auto=format&fit=crop&w=480&h=480&q=80",
    "Denim Jacket": "https://images.unsplash.com/photo-1576871337634-77b59fa12c05?auto=format&fit=crop&w=480&h=480&q=80",
    "Electric Kettle": "https://images.unsplash.com/photo-1565688534245-05d6945d46b8?auto=format&fit=crop&w=480&h=480&q=80",
    "Gaming Controller": "https://images.unsplash.com/photo-1606144042614-b2417e99c4e3?auto=format&fit=crop&w=480&h=480&q=80",
    "Green Tea Bags": "https://images.unsplash.com/photo-1556678153-088d5e3b1c6a?auto=format&fit=crop&w=480&h=480&q=80",
    "HD Webcam": "https://images.unsplash.com/photo-1614624532983-4ce03382d63d?auto=format&fit=crop&w=480&h=480&q=80",
    "Herbal Chai Blend": "https://images.unsplash.com/photo-1563822249366-3efb23b8e0c9?auto=format&fit=crop&w=480&h=480&q=80",
    "Instant Oatmeal Box": "https://images.unsplash.com/photo-1517673132405-a56a62b18caf?auto=format&fit=crop&w=480&h=480&q=80",
    "Kitchen Organizer Rack": "https://images.unsplash.com/photo-1585428780875-6268be5ba4ba?auto=format&fit=crop&w=480&h=480&q=80",
    "LED Table Lamp": "https://images.unsplash.com/photo-1507473880911-e636856d34d4?auto=format&fit=crop&w=480&h=480&q=80",
    "Leather Sling Bag": "https://images.unsplash.com/photo-1548036328-c9fa89d128fa?auto=format&fit=crop&w=480&h=480&q=80",
    "Masala Spice Set": "https://images.unsplash.com/photo-1596040033229-a9821ebd058d?auto=format&fit=crop&w=480&h=480&q=80",
    "Mechanical Keyboard": "https://images.unsplash.com/photo-1511467687858-23d96c534e86?auto=format&fit=crop&w=480&h=480&q=80",
    "Memory Foam Pillow": "https://images.unsplash.com/photo-1631049035182-249067d2f8e3?auto=format&fit=crop&w=480&h=480&q=80",
    "Natural Honey Jar": "https://images.unsplash.com/photo-1587049351697-0314e4e54d5b?auto=format&fit=crop&w=480&h=480&q=80",
    "Noise Cancelling Headphones": "https://images.unsplash.com/photo-1505740428788-422f6a6a4e3d?auto=format&fit=crop&w=480&h=480&q=80",
    "Non-Stick Frying Pan": "https://images.unsplash.com/photo-1584990347499-78d6a6c2f4f8?auto=format&fit=crop&w=480&h=480&q=80",
    "Organic Basmati Rice 5kg": "https://images.unsplash.com/photo-1586201375774-297988c0020e?auto=format&fit=crop&w=480&h=480&q=80",
    "Portable SSD 1TB": "https://images.unsplash.com/photo-1597872200963-2b35d21fdbe5?auto=format&fit=crop&w=480&h=480&q=80",
    "Premium Coffee Beans": "https://images.unsplash.com/photo-1559056199-641a0ac8b55c?auto=format&fit=crop&w=480&h=480&q=80",
    "Running Shoes": "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=480&h=480&q=80",
    "Slim Fit Polo": "https://images.unsplash.com/photo-1625910515319-7911175f1fc6?auto=format&fit=crop&w=480&h=480&q=80",
    "Smart Fitness Band": "https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6?auto=format&fit=crop&w=480&h=480&q=80",
    "Smart Lamp": "https://images.unsplash.com/photo-1513506003901-1e6a229e2d15?auto=format&fit=crop&w=480&h=480&q=80",
    "Smart Watch": "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=480&h=480&q=80",
    "Spanish Almond Mix": "https://images.unsplash.com/photo-1508061257256-1df93d4b3a0b?auto=format&fit=crop&w=480&h=480&q=80",
    "Steel Water Bottle": "https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=480&h=480&q=80",
    "Trail Running Cap": "https://images.unsplash.com/photo-1588850561407-d30f7649d614?auto=format&fit=crop&w=480&h=480&q=80",
    "Travel Backpack": "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=480&h=480&q=80",
    "USB-C Fast Charger": "https://images.unsplash.com/photo-1591290618853-4ef27c0c6c90?auto=format&fit=crop&w=480&h=480&q=80",
    "Vacuum Flask": "https://images.unsplash.com/photo-1605473172644-bf8a1c908307?auto=format&fit=crop&w=480&h=480&q=80",
    "Whole Wheat Atta 5kg": "https://images.unsplash.com/photo-1574323347407-f5e1ad6d020b?auto=format&fit=crop&w=480&h=480&q=80",
    "Winter Knit Sweater": "https://images.unsplash.com/photo-1434389677669-e08b4cac3105?auto=format&fit=crop&w=480&h=480&q=80",
    "Wireless Earbuds Pro": "https://images.unsplash.com/photo-1590658268037-6bf12165a8df?auto=format&fit=crop&w=480&h=480&q=80",
    "Wireless Mouse": "https://images.unsplash.com/photo-1527814050087-3793815479db?auto=format&fit=crop&w=480&h=480&q=80",
    "Woven Belt": "https://images.unsplash.com/photo-1624222247344-550fb60583fd?auto=format&fit=crop&w=480&h=480&q=80",
}

PRODUCT_ID_IMAGE_URLS = {
    "PROD-0001": PRODUCT_IMAGE_URLS["Wireless Earbuds Pro"],
    "PROD-0002": PRODUCT_IMAGE_URLS["Running Shoes"],
    "PROD-0003": PRODUCT_IMAGE_URLS["Assorted Dry Fruits Pack"],
    "PROD-0004": PRODUCT_IMAGE_URLS["Steel Water Bottle"],
    "PROD-0006": PRODUCT_IMAGE_URLS["Trail Running Cap"],
    "PROD-0007": PRODUCT_IMAGE_URLS["Herbal Chai Blend"],
    "PROD-0008": PRODUCT_IMAGE_URLS["Electric Kettle"],
    "PROD-0013": PRODUCT_IMAGE_URLS["Smart Lamp"],
    "PROD-0015": PRODUCT_IMAGE_URLS["Cold Pressed Coconut Oil"],
    "PROD-0016": PRODUCT_IMAGE_URLS["Cotton Bedsheet Set"],
    "PROD-0028": PRODUCT_IMAGE_URLS["LED Table Lamp"],
    "PROD-0037": PRODUCT_IMAGE_URLS["Wireless Mouse"],
    "PROD-0043": PRODUCT_IMAGE_URLS["Green Tea Bags"],
    "PROD-0045": PRODUCT_IMAGE_URLS["Noise Cancelling Headphones"],
    "PROD-0047": PRODUCT_IMAGE_URLS["Natural Honey Jar"],
    "PROD-0049": PRODUCT_IMAGE_URLS["Gaming Controller"],
    "PROD-0050": PRODUCT_IMAGE_URLS["Travel Backpack"],
    "TEST-A": PRODUCT_IMAGE_URLS["Wireless Earbuds Pro"],
    "TEST-B": PRODUCT_IMAGE_URLS["Organic Basmati Rice 5kg"],
    "TEST-C": PRODUCT_IMAGE_URLS["Cotton Casual Shirt"],
    "TEST-D": PRODUCT_IMAGE_URLS["LED Table Lamp"],
    "PROD-0101": "https://images.unsplash.com/photo-1505740428788-422f6a6a4e3d?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0102": "https://images.unsplash.com/photo-1625842268584-8f3296236761?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0103": "https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0104": "https://images.unsplash.com/photo-1558008258-3256797b43f3?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0105": "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0106": "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0107": "https://images.unsplash.com/photo-1521369909029-2afed882baee?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0108": "https://images.unsplash.com/photo-1507473880911-e636856d34d4?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0109": "https://images.unsplash.com/photo-1514228742587-6b1558fcca3d?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0110": "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0111": "https://images.unsplash.com/photo-1587049351697-0314e4e54d5b?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0112": "https://images.unsplash.com/photo-1508061257256-1df93d4b3a0b?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0113": "https://images.unsplash.com/photo-1559056199-641a0ac8b55c?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0114": "https://images.unsplash.com/photo-1565688534245-05d6945d46b8?auto=format&fit=crop&w=480&h=480&q=80",
    "PROD-0115": "https://images.unsplash.com/photo-1590874105168-82f9da0673b9?auto=format&fit=crop&w=480&h=480&q=80",
}

CATEGORY_FALLBACK_IMAGES = {
    "Electronics": "https://images.unsplash.com/photo-1498049794561-7780e7231661?auto=format&fit=crop&w=480&h=480&q=80",
    "Fashion": "https://images.unsplash.com/photo-1445205170230-053b83016050?auto=format&fit=crop&w=480&h=480&q=80",
    "Grocery": "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=480&h=480&q=80",
    "Home & Kitchen": "https://images.unsplash.com/photo-1584990347499-78d6a6c2f4f8?auto=format&fit=crop&w=480&h=480&q=80",
}

PRODUCT_ICON_IMAGES = {
        "Glass Food Storage Jar": "🫙",
        "Leather Wallet": "👛",
        "Canvas Tote Bag": "👜",
        "Canvas Weekend Bag": "👜",
}


def product_icon_data_url(product_name: str) -> str:
        icon = PRODUCT_ICON_IMAGES[product_name]
        svg = f"""
            <svg xmlns="http://www.w3.org/2000/svg" width="480" height="480" viewBox="0 0 480 480">
                <defs>
                    <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
                        <stop offset="0%" stop-color="#dbeafe" />
                        <stop offset="100%" stop-color="#fef3c7" />
                    </linearGradient>
                </defs>
                <rect width="480" height="480" rx="28" fill="url(#bg)" />
                <text x="240" y="260" text-anchor="middle" font-size="160" dominant-baseline="middle">{icon}</text>
            </svg>
        """
        return "data:image/svg+xml;charset=UTF-8," + quote(svg)


def get_product_image_url(product_name: str, category: str = "", product_id: str = "") -> str:
    """Return a product-specific photo or icon, then a category fallback."""
    if product_name in PRODUCT_ICON_IMAGES:
        return product_icon_data_url(product_name)
    if product_id in PRODUCT_ID_IMAGE_URLS:
        return PRODUCT_ID_IMAGE_URLS[product_id]
    if product_name in PRODUCT_IMAGE_URLS:
        return PRODUCT_IMAGE_URLS[product_name]
    return CATEGORY_FALLBACK_IMAGES.get(category, CATEGORY_FALLBACK_IMAGES["Electronics"])
