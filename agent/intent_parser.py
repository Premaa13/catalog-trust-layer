import re


FILLER_WORDS = {
    "find",
    "me",
    "a",
    "an",
    "i",
    "need",
    "want",
    "cheap",
    "show",
    "looking",
    "for",
    "buy",
    "please",
    "the",
    "my",
    "shop",
    "get",
    "search",
    "best",
    "good",
    "decent",
    "budget",
    "under",
    "below",
    "less",
    "than",
    "within",
    "up",
    "to",
    "inr",
    "rs",
    "rupees",
    "price",
    "max",
    "budget",
    "with",
    "without",
}


def _extract_price(user_query: str):
    text = user_query.strip()
    patterns = [
        r"(?:under|below|less\s+than|within|upto|up\s+to)\s*₹?\s*(\d+(?:,\d{3})*(?:\.\d+)?)",
        r"(?:under|below|less\s+than|within|upto|up\s+to)\s*(\d+(?:,\d{3})*(?:\.\d+)?)",
        r"(\d+(?:,\d{3})*(?:\.\d+)?)\s*(?:rupees?|inr|rs)",
    ]

    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return float(match.group(1).replace(",", ""))
    return None


def _strip_price_phrase(user_query: str, price_value: float | None):
    text = user_query.strip()
    if price_value is None:
        return text

    normalized = re.sub(r"\s+", " ", text)
    patterns = [
        r"(?:under|below|less\s+than|within|upto|up\s+to)\s*₹?\s*\d+(?:,\d{3})*(?:\.\d+)?\s*(?:rupees?|inr|rs)?",
        r"\d+(?:,\d{3})*(?:\.\d+)?\s*(?:rupees?|inr|rs)",
    ]
    for pattern in patterns:
        normalized = re.sub(pattern, " ", normalized, flags=re.IGNORECASE)
    return normalized.strip()


def parse_intent(user_query: str) -> dict:
    text = (user_query or "").strip()
    if not text:
        return {"keyword": "", "max_price": None}

    max_price = _extract_price(text)
    remaining = _strip_price_phrase(text, max_price)
    remaining = re.sub(r"[^a-zA-Z0-9\s]", " ", remaining)
    remaining = re.sub(r"\s+", " ", remaining).strip().lower()

    tokens = [token for token in remaining.split() if token and token not in FILLER_WORDS]
    keyword = " ".join(tokens).strip()

    if not keyword:
        keyword = "product"

    return {"keyword": keyword, "max_price": max_price}
