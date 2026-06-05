"""Hot rank agent loop: observe → decide → dispatch → verify."""

from typing import TypedDict
from langgraph.graph import StateGraph, END


class AgentState(TypedDict, total=False):
    top_k: list[dict]
    decision: dict | None
    dispatched_cmd: dict | None
    effect: dict | None
    replan: bool


def observe(state: AgentState) -> AgentState:
    """Read current hot rank via MCP get_hot_rank tool."""
    # Stub: will call MCP client in Card 5.2
    return {"top_k": state.get("top_k", [])}


def decide(state: AgentState) -> AgentState:
    """Pick a target to boost based on current rankings."""
    # Stub: will implement strategy in Card 5.3
    top_k = state.get("top_k", [])
    if top_k:
        target = top_k[-1]  # pick lowest-ranked item to boost
        return {"decision": {"target": target, "weight": 10}}
    return {"decision": None}


def dispatch(state: AgentState) -> AgentState:
    """Send boost command via MCP dispatch_boost_exposure tool."""
    # Stub: will call MCP client in Card 5.3
    decision = state.get("decision")
    if decision is None:
        return {"dispatched_cmd": None}
    return {"dispatched_cmd": {"target": decision["target"], "weight": decision["weight"], "sent": True}}


def verify(state: AgentState) -> AgentState:
    """Re-read hot rank and compare with pre-dispatch state."""
    # Stub: will call MCP and compare in Card 5.4
    dispatched = state.get("dispatched_cmd")
    if dispatched and dispatched.get("sent"):
        return {"effect": {"observed": True}, "replan": False}
    return {"effect": None, "replan": False}


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