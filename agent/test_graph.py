"""Tests for the agent loop graph."""

from unittest.mock import patch
from graph import AgentState, build_graph, get_compiled_graph, observe, decide, dispatch, verify


def test_observe_calls_mcp():
    with patch("graph.get_hot_rank", return_value=[{"contentId": "c1", "score": 100}]):
        state: AgentState = {"region": "CN"}
        result = observe(state)
        assert result["top_k"] == [{"contentId": "c1", "score": 100}]


def test_observe_falls_back_on_error():
    with patch("graph.get_hot_rank", side_effect=Exception("network error")):
        state: AgentState = {"region": "CN", "top_k": [{"contentId": "c1", "score": 50}]}
        result = observe(state)
        assert result["top_k"] == [{"contentId": "c1", "score": 50}]


def test_decide_picks_lowest_ranked():
    state: AgentState = {
        "top_k": [
            {"contentId": "c1", "score": 100},
            {"contentId": "c2", "score": 50},
        ]
    }
    result = decide(state)
    assert result["decision"] is not None
    assert result["decision"]["target"]["contentId"] == "c2"
    assert result["decision"]["weight"] > 0


def test_decide_produces_aggressive_weight_for_large_gap():
    """When score gap is large, decide produces weight > 100 (domain will reject)."""
    state: AgentState = {
        "top_k": [
            {"contentId": "c1", "score": 500},
            {"contentId": "c2", "score": 100},
        ]
    }
    result = decide(state)
    assert result["decision"] is not None
    assert result["decision"]["weight"] > 100  # 400 * 0.8 = 320


def test_decide_moderate_weight_for_medium_gap():
    """Medium gap produces weight within bounds."""
    state: AgentState = {
        "top_k": [
            {"contentId": "c1", "score": 150},
            {"contentId": "c2", "score": 80},
        ]
    }
    result = decide(state)
    assert result["decision"]["weight"] <= 100
    assert result["decision"]["weight"] > 10


def test_decide_returns_none_when_empty():
    state: AgentState = {"top_k": []}
    result = decide(state)
    assert result["decision"] is None


def test_dispatch_calls_mcp():
    with patch("graph.dispatch_boost_exposure", return_value={"Accepted": True, "IdempotencyKey": "k1"}):
        state: AgentState = {
            "decision": {"target": {"contentId": "c1"}, "weight": 10},
            "region": "CN",
        }
        result = dispatch(state)
        assert result["dispatched_cmd"]["sent"] is True


def test_dispatch_skips_when_no_decision():
    state: AgentState = {"decision": None}
    result = dispatch(state)
    assert result["dispatched_cmd"] is None


def test_dispatch_handles_error():
    with patch("graph.dispatch_boost_exposure", side_effect=Exception("timeout")):
        state: AgentState = {
            "decision": {"target": {"contentId": "c1"}, "weight": 10},
            "region": "CN",
        }
        result = dispatch(state)
        assert result["dispatched_cmd"]["sent"] is False
        assert "timeout" in result["dispatched_cmd"]["error"]


def test_verify_detects_improvement():
    with patch("graph.get_hot_rank", return_value=[
        {"contentId": "c1", "score": 110},
        {"contentId": "c2", "score": 90},
    ]):
        state: AgentState = {
            "region": "CN",
            "top_k": [
                {"contentId": "c2", "score": 80},
                {"contentId": "c1", "score": 100},
            ],
            "dispatched_cmd": {"target": {"contentId": "c1"}, "sent": True},
        }
        result = verify(state)
        assert result["effect"]["improved"] is True


def test_full_graph_with_mocked_mcp():
    with patch("graph.get_hot_rank", return_value=[
        {"contentId": "c1", "score": 100},
        {"contentId": "c2", "score": 50},
    ]), patch("graph.dispatch_boost_exposure", return_value={"Accepted": True}):
        graph = get_compiled_graph()
        result = graph.invoke({"region": "CN"})
        assert result["dispatched_cmd"]["sent"] is True
        assert result["effect"] is not None