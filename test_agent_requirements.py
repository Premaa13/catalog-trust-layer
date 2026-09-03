from agent.intent_parser import parse_intent
from agent.shopping_agent import search_candidates


def test_parse_intent_basic_cases():
    assert parse_intent("find headphones below 5000") == {"keyword": "headphones", "max_price": 5000}
    assert parse_intent("I need a wireless mouse under 2000") == {"keyword": "wireless mouse", "max_price": 2000}
    assert parse_intent("cheap kitchen product") == {"keyword": "kitchen product", "max_price": None}


def test_search_candidates_filters_and_matches_tokens():
    matches = search_candidates("wireless mouse", 20000)
    assert matches
    assert any("mouse" in item["product_name"].lower() for item in matches)
    assert all(item["price"] <= 20000 for item in matches)

    headphone_matches = search_candidates("headphones", 20000)
    assert headphone_matches
    assert any("headphone" in item["product_name"].lower() or "earbud" in item["product_name"].lower() for item in headphone_matches)


if __name__ == "__main__":
    test_parse_intent_basic_cases()
    test_search_candidates_filters_and_matches_tokens()
    print("Agent parser and search validation passed.")
