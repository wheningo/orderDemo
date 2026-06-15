"""Tests for the agent loop graph."""

import pytest
from unittest.mock import patch
from preflight import PreflightResult
from graph import AgentState, build_graph, get_compiled_graph, observe, decide, dispatch, verify


def _patch_preflight_pass():
    """Context managers to bypass preflight in tests that focus on domain behavior."""
    return patch("preflight.preflight_check_boost", return_value=PreflightResult.ok())


def test_observe_calls_mcp():
    with patch("graph.get_hot_rank", return_value=[{"contentId": "c1", "score": 100}]):
        state: AgentState = {"region": "CN"}
        result = observe(state)
        assert result["top_k"] == [{"contentId": "c1", "score": 100}]
        assert result["attempts"] == []


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
    state: AgentState = {
        "top_k": [
            {"contentId": "c1", "score": 500},
            {"contentId": "c2", "score": 100},
        ]
    }
    result = decide(state)
    assert result["decision"] is not None
    assert result["decision"]["weight"] > 100


def test_decide_backs_off_on_replan():
    state: AgentState = {
        "top_k": [
            {"contentId": "c1", "score": 500},
            {"contentId": "c2", "score": 100},
        ],
        "replan": True,
    }
    result = decide(state)
    assert result["decision"]["weight"] <= 100


def test_decide_returns_none_when_empty():
    state: AgentState = {"top_k": []}
    result = decide(state)
    assert result["decision"] is None


def test_dispatch_calls_mcp():
    with patch("graph.dispatch_boost_exposure", return_value={"Accepted": True, "IdempotencyKey": "k1"}):
        state: AgentState = {
            "decision": {"target": {"contentId": "c1"}, "weight": 10, "risk_tier": "standard"},
            "region": "CN",
        }
        result = dispatch(state)
        assert result["dispatched_cmd"]["sent"] is True


def test_dispatch_skips_when_no_decision():
    state: AgentState = {"decision": None}
    result = dispatch(state)
    assert result["dispatched_cmd"] is None


def test_dispatch_handles_error():
    with _patch_preflight_pass(), \
         patch("graph.dispatch_boost_exposure", side_effect=Exception("timeout")):
        state: AgentState = {
            "decision": {"target": {"contentId": "c1"}, "weight": 10, "risk_tier": "standard"},
            "region": "CN",
        }
        result = dispatch(state)
        assert result["dispatched_cmd"]["sent"] is False
        assert "timeout" in result["dispatched_cmd"]["error"]


def test_verify_records_rejected_attempt():
    state: AgentState = {
        "region": "CN",
        "top_k": [{"contentId": "c1", "score": 100}],
        "dispatched_cmd": {
            "target": {"contentId": "c1"},
            "weight": 236,
            "sent": True,
            "result": {"Accepted": False, "Reason": "Weight must be between 1 and 100, got: 236"},
        },
        "attempts": [],
        "iterations": 0,
    }
    result = verify(state)
    assert result["replan"] is True
    assert len(result["attempts"]) == 1
    assert result["attempts"][0]["accepted"] is False
    assert result["iterations"] == 1


def test_verify_records_accepted_attempt():
    with patch("graph.get_hot_rank", return_value=[{"contentId": "c1", "score": 105}]):
        state: AgentState = {
            "region": "CN",
            "top_k": [{"contentId": "c1", "score": 5}],
            "dispatched_cmd": {
                "target": {"contentId": "c1"},
                "weight": 100,
                "sent": True,
                "result": {"Accepted": True, "Reason": ""},
            },
            "attempts": [{"weight": 236, "accepted": False, "reason": "Weight must be between 1 and 100, got: 236"}],
            "iterations": 1,
        }
        result = verify(state)
        assert result["replan"] is False
        assert result["effect"]["applied"] is True
        assert result["effect"]["new_score"] == 105
        assert len(result["attempts"]) == 2
        assert result["attempts"][1]["accepted"] is True


def test_verify_stops_replan_at_max_iterations():
    state: AgentState = {
        "region": "CN",
        "dispatched_cmd": {
            "target": {"contentId": "c1"},
            "weight": 236,
            "sent": True,
            "result": {"Accepted": False, "Reason": "rejected"},
        },
        "attempts": [],
        "iterations": 2,
    }
    result = verify(state)
    assert result["replan"] is False


def test_full_graph_reject_then_succeed():
    """Full graph: first attempt rejected by domain, replan backs off, second succeeds."""
    call_count = {"n": 0}

    def mock_get_hot_rank(region, k=10):
        call_count["n"] += 1
        if call_count["n"] <= 1:
            return [{"contentId": "hot-1", "score": 300}, {"contentId": "cold-1", "score": 5}]
        return [{"contentId": "hot-1", "score": 300}, {"contentId": "cold-1", "score": 105}]

    def mock_dispatch(**kwargs):
        weight = kwargs.get("weight", 0)
        if weight > 100:
            return {"Accepted": False, "Reason": f"Weight must be between 1 and 100, got: {weight}"}
        return {"Accepted": True, "Reason": "", "IdempotencyKey": "k-ok"}

    with _patch_preflight_pass(), \
         patch("graph.get_hot_rank", side_effect=mock_get_hot_rank), \
         patch("graph.dispatch_boost_exposure", side_effect=mock_dispatch):
        graph = get_compiled_graph()
        result = graph.invoke({"region": "CN"})

        assert result["iterations"] == 2
        assert len(result["attempts"]) == 2
        assert result["attempts"][0]["accepted"] is False
        assert result["attempts"][1]["accepted"] is True
        assert result["replan"] is False
        assert result["effect"]["applied"] is True


def test_full_graph_empty_rankings():
    with patch("graph.get_hot_rank", return_value=[]):
        graph = get_compiled_graph()
        result = graph.invoke({"region": "CN"})
        assert result["decision"] is None
        assert result["dispatched_cmd"] is None