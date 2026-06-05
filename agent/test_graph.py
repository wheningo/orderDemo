"""Tests for the agent loop graph."""

from graph import AgentState, build_graph, get_compiled_graph, observe, decide, dispatch, verify


def test_observe_returns_top_k():
    state: AgentState = {"top_k": [{"content_id": "c1", "score": 100}]}
    result = observe(state)
    assert result["top_k"] == [{"content_id": "c1", "score": 100}]


def test_decide_picks_lowest_ranked():
    state: AgentState = {
        "top_k": [
            {"content_id": "c1", "score": 100},
            {"content_id": "c2", "score": 50},
        ]
    }
    result = decide(state)
    assert result["decision"] is not None
    assert result["decision"]["target"]["content_id"] == "c2"


def test_decide_returns_none_when_empty():
    state: AgentState = {"top_k": []}
    result = decide(state)
    assert result["decision"] is None


def test_dispatch_sends_when_decision_exists():
    state: AgentState = {"decision": {"target": {"content_id": "c1"}, "weight": 10}}
    result = dispatch(state)
    assert result["dispatched_cmd"]["sent"] is True


def test_dispatch_skips_when_no_decision():
    state: AgentState = {"decision": None}
    result = dispatch(state)
    assert result["dispatched_cmd"] is None


def test_verify_observes_effect():
    state: AgentState = {"dispatched_cmd": {"sent": True}}
    result = verify(state)
    assert result["effect"]["observed"] is True
    assert result["replan"] is False


def test_full_graph_execution():
    graph = get_compiled_graph()
    initial_state: AgentState = {
        "top_k": [
            {"content_id": "c1", "score": 100},
            {"content_id": "c2", "score": 50},
        ]
    }
    result = graph.invoke(initial_state)
    assert "decision" in result
    assert "dispatched_cmd" in result
    assert "effect" in result
    assert result["dispatched_cmd"]["sent"] is True


def test_full_graph_with_empty_rankings():
    graph = get_compiled_graph()
    initial_state: AgentState = {"top_k": []}
    result = graph.invoke(initial_state)
    assert result["decision"] is None
    assert result["dispatched_cmd"] is None