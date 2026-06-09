"""MCP client — talks to Go gateway via HTTP."""

import os
import httpx

GATEWAY_URL = os.getenv("GATEWAY_URL", "http://localhost:8081")


def call_tool(tool_name: str, params: dict) -> dict:
    """Call an MCP tool on the gateway."""
    with httpx.Client(timeout=10.0) as client:
        resp = client.post(
            f"{GATEWAY_URL}/mcp/call",
            json={"tool": tool_name, "params": params},
        )
        resp.raise_for_status()
        return resp.json()


def get_hot_rank(region: str, k: int = 10) -> list[dict]:
    """Get current Top-K rankings for a region."""
    return call_tool("get_hot_rank", {"region": region, "k": k})


def dispatch_boost_exposure(
    target_content_id: str,
    weight: int,
    region: str,
    decision_source: str = "agent",
    idempotency_key: str = "",
    risk_tier: str = "standard",
) -> dict:
    """Dispatch a boost exposure command."""
    return call_tool(
        "dispatch_boost_exposure",
        {
            "target_content_id": target_content_id,
            "weight": weight,
            "region": region,
            "decision_source": decision_source,
            "idempotency_key": idempotency_key,
            "risk_tier": risk_tier,
        },
    )


def allocate_promo_stock(
    sku: str,
    qty: int,
    region: str = "CN",
    risk_tier: str = "standard",
    idempotency_key: str = "",
) -> dict:
    """Allocate promotional stock for a SKU."""
    return call_tool(
        "allocate_promo_stock",
        {
            "sku": sku,
            "qty": qty,
            "region": region,
            "risk_tier": risk_tier,
            "idempotency_key": idempotency_key,
        },
    )