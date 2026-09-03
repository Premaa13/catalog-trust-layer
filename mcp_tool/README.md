# verify_listing_trust MCP Tool

## Overview

`verify_listing_trust` is a single-product trust verification tool designed to be called by an AI buyer agent before it acts on a catalog listing. It implements the Catalog Trust Layer evaluation pipeline in a clean, stateless function following the MCP (Model Context Protocol) tool pattern used by systems like RazorAgent.

## Design Philosophy

This tool is intentionally focused and simple:
- **Single responsibility**: Verify trust for one product at a time
- **Stateless**: All state (catalog, orders data) is passed as immutable input
- **Structured contract**: Clear input/output shape with no ambiguity
- **Reusable**: Uses the same scoring engine as the batch trust_engine.py, ensuring consistency

## Usage

### Python API

```python
from mcp_tool.verify_listing_trust import verify_listing_trust

# Call with a product ID
result = verify_listing_trust("TEST-A")

print(result)
# Output:
# {
#   "product_id": "TEST-A",
#   "product_name": "Price Spike Widget",
#   "trust_score": 50,
#   "status": "FLAGGED",
#   "action": "REVIEW",
#   "reasons": [
#     "Price increased 70.0% in 2.0 hours (high-risk)",
#     "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score"
#   ]
# }
```

### Command Line

```bash
python mcp_tool/verify_listing_trust.py TEST-A
python mcp_tool/verify_listing_trust.py PROD-0001
python mcp_tool/verify_listing_trust.py TEST-B
```

## Input/Output Contract

### Input

- **product_id** (string): The product ID to verify
  - Examples: "TEST-A", "PROD-0001", "PROD-0050"
  - Required, case-sensitive

### Output

A dictionary with the following structure:

#### Success Case (product found)

```json
{
  "product_id": "TEST-A",
  "product_name": "Price Spike Widget",
  "trust_score": 50,
  "status": "FLAGGED",
  "action": "REVIEW",
  "reasons": [
    "Price increased 70.0% in 2.0 hours (high-risk)",
    "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score"
  ]
}
```

**Fields:**
- **product_id** (string): The queried product ID
- **product_name** (string): Product name from the latest catalog snapshot
- **trust_score** (integer): Score from 0-100
  - 90-100: Highly trustworthy
  - 60-89: Generally trustworthy with some concerns
  - 30-59: Moderate concerns
  - 0-29: Severe concerns
- **status** (string): One of `VERIFIED` or `FLAGGED`
  - VERIFIED: No critical issues (score 90+)
  - FLAGGED: One or more concerns detected
- **action** (string): One of `ALLOW`, `ALLOW_WITH_WARNING`, `REVIEW`, or `BLOCK`
  - ALLOW: Safe to transact
  - ALLOW_WITH_WARNING: Transact with caution
  - REVIEW: Manual review recommended before transacting
  - BLOCK: Do not transact
- **reasons** (array of strings): List of specific findings that led to penalties
  - Empty array if no issues detected
  - Each reason explains what triggered a penalty and by how much

#### Error Case (product not found)

```json
{
  "error": "product_id not found"
}
```

## Scoring Pipeline

The tool runs three checks on the product:

### 1. Price Velocity
Detects price spikes that might indicate fraud or manipulation.
- >50% increase within the window → -50 points (high-risk)
- >25% increase within the window → -25 points (suspicious)
- Only triggers if product has 2+ snapshots

### 2. Stock Consistency
Verifies that claimed stock makes sense relative to recent sales.
- Recent sales > claimed stock → -50 points (impossible to fulfill)
- Recent sales > 80% of claimed stock → -20 points (high velocity)
- "Recent" = completed orders in the last 2 hours

### 3. Freshness
Ensures the listing data is not stale.
- >72 hours old → -40 points (severe)
- >48 hours old → -20 points (aging)
- >24 hours old → -10 points (warning)

### Safety Overrides
Applied after the base scoring:
- If freshness > 72 hours AND action would be ALLOW or ALLOW_WITH_WARNING → force action to REVIEW
- This creates a hard gate: severely stale listings cannot be ALLOWed regardless of other factors

## Integration with AI Buyer Agents

This tool is designed to be called in your AI buyer agent's decision pipeline:

```
1. Agent finds listing in catalog
2. Agent calls: verify_listing_trust(product_id)
3. Agent inspects action field:
   - ALLOW: Proceed with transaction
   - ALLOW_WITH_WARNING: Proceed but log the warning
   - REVIEW: Request human review before proceeding
   - BLOCK: Do not proceed
4. Agent respects the reasons array for logging/transparency
```

## Implementation Notes

- Data files are loaded fresh on each call (no caching)
- Timestamps are relative to the latest entry in the dataset, not real current time
- This ensures reproducible results across multiple runs
- The tool handles missing products gracefully with an error dict
