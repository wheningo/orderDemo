"""Hot rank agent loop: observe → decide → dispatch → verify (with replan loop)."""

from typing import TypedDict
from langgraph.graph import StateGraph, END
from mcp_client import get_hot_rank, dispatch_boost_exposure

MAX_ITERATIONS = 3


class AgentState(TypedDict, total=False):
    region: str
    top_k: list[dict]
    decision: dict | None
    dispatched_cmd: dict | None
    attempts: list[dict]
    effect: dict | None
    iterations: int
    replan: bool


def observe(state: AgentState) -> AgentState:
    """Read current hot rank via MCP get_hot_rank tool."""
    region = state.get("region", "CN")
    try:
        top_k = get_hot_rank(region, k=10)
    except Exception:
        top_k = state.get("top_k", [])
    return {"top_k": top_k, "attempts": [], "iterations": 0}


def decide(state: AgentState) -> AgentState:
    """Pick a target to boost based on current rankings."""
    top_k = state.get("top_k", [])
    if not top_k:
        return {"decision": None}

    target = top_k[-1]
    gap = top_k[0].get("score", 0) - target.get("score", 0)

    # First attempt: gap large → aggressive weight (likely > 100, domain will reject)
    if gap > 200:
        weight = int(gap * 0.8)
    elif gap > 50:
        weight = min(int(gap * 0.5), 100)
    else:
        weight = 10

    # Replan: previous attempt was rejected → back off to legal upper bound
    if state.get("replan"):
        weight = min(weight, 100)

    risk_tier = "high" if weight > 50 else "standard"
    return {"decision": {"target": target, "weight": weight, "risk_tier": risk_tier}}


def dispatch(state: AgentState) -> AgentState:
    """Send boost command via MCP dispatch_boost_exposure tool."""
    decision = state.get("decision")
    if decision is None:
        return {"dispatched_cmd": None}

    target = decision["target"]
    content_id = target.get("contentId") or target.get("content_id", "")
    region = state.get("region", "CN")
    risk_tier = decision.get("risk_tier", "standard")

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
    """Check dispatch result, record attempt, decide whether to replan."""
    dispatched = state.get("dispatched_cmd")
    attempts = list(state.get("attempts", []))
    iterations = state.get("iterations", 0) + 1

    if not dispatched or not dispatched.get("sent"):
        return {"effect": None, "replan": False, "iterations": iterations}

    result = dispatched.get("result", {})
    accepted = result.get("Accepted", result.get("accepted", False))
    reason = result.get("Reason", result.get("reason", ""))

    attempts.append({
        "weight": dispatched["weight"],
        "accepted": accepted,
        "reason": reason,
    })

    if accepted:
        # Success — read new state to confirm effect
        region = state.get("region", "CN")
        target_id = dispatched["target"].get("contentId") or dispatched["target"].get("content_id", "")
        try:
            new_top_k = get_hot_rank(region, k=10)
            new_score = next(
                (item.get("score", 0) for item in new_top_k
                 if (item.get("contentId") or item.get("content_id")) == target_id),
                None,
            )
            effect = {"applied": True, "target": target_id, "new_score": new_score}
        except Exception:
            effect = {"applied": True, "target": target_id, "new_score": None}
        return {"effect": effect, "replan": False, "attempts": attempts, "iterations": iterations}
    else:
        # Rejected — signal replan if under iteration limit
        return {"effect": None, "replan": iterations < MAX_ITERATIONS, "attempts": attempts, "iterations": iterations}


def should_replan(state: AgentState) -> str:
    """Conditional edge: replan → decide, else → END."""
    if state.get("replan") and state.get("iterations", 0) < MAX_ITERATIONS:
        return "decide"
    return "end"


def build_graph() -> StateGraph:
    """Construct the agent loop graph with replan loop."""
    graph = StateGraph(AgentState)

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


def get_compiled_graph():
    """Return a compiled, ready-to-invoke graph."""
    return build_graph().compile()