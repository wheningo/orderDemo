"""Hot rank agent loop: observe → decide → dispatch → verify."""

from typing import TypedDict
from langgraph.graph import StateGraph, END
from mcp_client import get_hot_rank, dispatch_boost_exposure


class AgentState(TypedDict, total=False):
    region: str
    top_k: list[dict]
    decision: dict | None
    dispatched_cmd: dict | None
    effect: dict | None
    replan: bool


def observe(state: AgentState) -> AgentState:
    """Read current hot rank via MCP get_hot_rank tool."""
    region = state.get("region", "CN")
    try:
        top_k = get_hot_rank(region, k=10)
    except Exception:
        top_k = state.get("top_k", [])
    return {"top_k": top_k}


def decide(state: AgentState) -> AgentState:
    """Pick a target to boost based on current rankings."""
    top_k = state.get("top_k", [])
    if not top_k:
        return {"decision": None}

    # Strategy: boost the lowest-ranked item
    target = top_k[-1]
    top_score = top_k[0].get("score", 0) if top_k else 0
    target_score = target.get("score", 0)

    # Aggressive strategy: if gap is large, try a large weight
    # This will be rejected by the domain if > 100 — demonstrating the "wall"
    gap = top_score - target_score
    if gap > 200:
        weight = int(gap * 0.8)  # likely > 100, domain will reject
    elif gap > 50:
        weight = min(int(gap * 0.5), 100)
    else:
        weight = 10

    return {"decision": {"target": target, "weight": weight}}


def dispatch(state: AgentState) -> AgentState:
    """Send boost command via MCP dispatch_boost_exposure tool."""
    decision = state.get("decision")
    if decision is None:
        return {"dispatched_cmd": None}

    target = decision["target"]
    content_id = target.get("contentId") or target.get("content_id", "")
    region = state.get("region", "CN")

    risk_tier = "high" if decision["weight"] > 50 else "standard"

    try:
        result = dispatch_boost_exposure(
            target_content_id=content_id,
            weight=decision["weight"],
            region=region,
            decision_source="agent",
            risk_tier=risk_tier,
        )
        return {"dispatched_cmd": {"target": target, "weight": decision["weight"], "sent": True, "result": result}}
    except Exception as e:
        return {"dispatched_cmd": {"target": target, "weight": decision["weight"], "sent": False, "error": str(e)}}


def verify(state: AgentState) -> AgentState:
    """Re-read hot rank and compare with pre-dispatch state."""
    dispatched = state.get("dispatched_cmd")
    if not dispatched or not dispatched.get("sent"):
        return {"effect": None, "replan": False}

    region = state.get("region", "CN")
    try:
        new_top_k = get_hot_rank(region, k=10)
        # Compare: did the boosted item move up?
        target_id = dispatched["target"].get("contentId") or dispatched["target"].get("content_id", "")
        old_rank = next(
            (i + 1 for i, item in enumerate(state.get("top_k", []))
             if (item.get("contentId") or item.get("content_id")) == target_id),
            None,
        )
        new_rank = next(
            (i + 1 for i, item in enumerate(new_top_k)
             if (item.get("contentId") or item.get("content_id")) == target_id),
            None,
        )

        return {
            "effect": {
                "target": target_id,
                "old_rank": old_rank,
                "new_rank": new_rank,
                "improved": new_rank is not None and old_rank is not None and new_rank < old_rank,
            },
            "replan": new_rank is not None and old_rank is not None and new_rank >= old_rank,
        }
    except Exception:
        return {"effect": {"observed": False}, "replan": True}


def build_graph() -> StateGraph:
    """Construct the agent loop graph."""
    graph = StateGraph(AgentState)

    graph.add_node("observe", observe)
    graph.add_node("decide", decide)
    graph.add_node("dispatch", dispatch)
    graph.add_node("verify", verify)

    graph.set_entry_point("observe")
    graph.add_edge("observe", "decide")
    graph.add_edge("decide", "dispatch")
    graph.add_edge("dispatch", "verify")
    graph.add_edge("verify", END)

    return graph


def get_compiled_graph():
    """Return a compiled, ready-to-invoke graph."""
    return build_graph().compile()