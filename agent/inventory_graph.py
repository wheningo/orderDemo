"""Inventory allocation agent loop: observe → decide → dispatch → verify (with retryable backoff)."""

from typing import TypedDict
from langgraph.graph import StateGraph, END
from mcp_client import allocate_promo_stock

MAX_ITERATIONS = 3


class InventoryState(TypedDict, total=False):
    sku: str
    qty: int
    region: str
    available: int | None
    attempts: list[dict]
    iterations: int
    result: dict | None
    replan: bool


def observe(state: InventoryState) -> InventoryState:
    """Initial state — qty and sku come from caller."""
    return {"attempts": [], "iterations": 0, "available": None}


def decide(state: InventoryState) -> InventoryState:
    """Decide qty to allocate. On replan: if not retryable, reduce qty to fit available."""
    qty = state.get("qty", 0)

    if state.get("replan"):
        last_attempt = state.get("attempts", [])[-1] if state.get("attempts") else None
        if last_attempt and not last_attempt.get("retryable"):
            # Permanent rejection — reduce qty to available
            available = state.get("available")
            if available is not None and available > 0:
                qty = available
            else:
                qty = 0
        # If retryable (CAS conflict), keep same qty and retry

    return {"qty": qty}


def dispatch(state: InventoryState) -> InventoryState:
    """Call allocate_promo_stock via MCP."""
    from preflight import preflight_check_inventory, on_dispatch_result

    qty = state.get("qty", 0)
    if qty <= 0:
        return {"result": {"accepted": False, "reason": "qty reduced to 0, giving up", "retryable": False}}

    sku = state.get("sku", "")
    region = state.get("region", "CN")
    risk_tier = "high" if qty > 50 else "standard"

    # Agent self-check (Level 1)
    check = preflight_check_inventory(qty, sku)
    if not check.passed:
        on_dispatch_result(False, check.reason)
        return {"result": {"accepted": False, "reason": "[preflight] " + check.reason, "retryable": False}}

    try:
        result = allocate_promo_stock(sku=sku, qty=qty, region=region, risk_tier=risk_tier)
        accepted = result.get("accepted", False)
        on_dispatch_result(accepted, result.get("reason", ""))
        return {"result": result}
    except Exception as e:
        on_dispatch_result(False, str(e))
        return {"result": {"accepted": False, "reason": str(e), "retryable": True}}


def verify(state: InventoryState) -> InventoryState:
    """Check result, record attempt, decide replan."""
    result = state.get("result", {})
    attempts = list(state.get("attempts", []))
    iterations = state.get("iterations", 0) + 1

    accepted = result.get("accepted", False)
    reason = result.get("reason", "")
    retryable = result.get("retryable", False)

    attempts.append({
        "qty": state.get("qty"),
        "accepted": accepted,
        "reason": reason,
        "retryable": retryable,
    })

    if accepted:
        return {"attempts": attempts, "iterations": iterations, "replan": False}

    # Parse available from reason if possible (format: "...available=100")
    available = state.get("available")
    if "available=" in reason:
        try:
            available = int(reason.split("available=")[-1].strip())
        except ValueError:
            pass

    should_replan = iterations < MAX_ITERATIONS and (retryable or (available is not None and available > 0))
    return {"attempts": attempts, "iterations": iterations, "replan": should_replan, "available": available}


def should_replan(state: InventoryState) -> str:
    if state.get("replan") and state.get("iterations", 0) < MAX_ITERATIONS:
        return "decide"
    return "end"


def build_inventory_graph() -> StateGraph:
    graph = StateGraph(InventoryState)
    graph.add_node("observe", observe)
    graph.add_node("decide", decide)
    graph.add_node("dispatch", dispatch)
    graph.add_node("verify", verify)

    graph.set_entry_point("observe")
    graph.add_edge("observe", "decide")
    graph.add_edge("decide", "dispatch")
    graph.add_edge("dispatch", "verify")
    graph.add_conditional_edges("verify", should_replan, {"decide": "decide", "end": END})

    return graph


def get_compiled_inventory_graph():
    return build_inventory_graph().compile()