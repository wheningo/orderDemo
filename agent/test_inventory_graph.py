"""Tests for inventory allocation agent loop."""

from unittest.mock import patch
from inventory_graph import InventoryState, get_compiled_inventory_graph, observe, decide, dispatch, verify


def test_decide_keeps_qty_on_first_attempt():
    state: InventoryState = {"sku": "SKU-1", "qty": 300, "region": "CN"}
    result = decide(state)
    assert result["qty"] == 300


def test_decide_reduces_qty_on_non_retryable_rejection():
    state: InventoryState = {
        "sku": "SKU-1",
        "qty": 300,
        "region": "CN",
        "replan": True,
        "available": 100,
        "attempts": [{"qty": 300, "accepted": False, "retryable": False}],
    }
    result = decide(state)
    assert result["qty"] == 100


def test_decide_keeps_qty_on_retryable_rejection():
    state: InventoryState = {
        "sku": "SKU-1",
        "qty": 30,
        "region": "CN",
        "replan": True,
        "attempts": [{"qty": 30, "accepted": False, "retryable": True}],
    }
    result = decide(state)
    assert result["qty"] == 30


def test_verify_parses_available_from_reason():
    state: InventoryState = {
        "sku": "SKU-1",
        "qty": 300,
        "result": {"accepted": False, "reason": "Oversell rejected: sku=SKU-1, requested=300, available=100", "retryable": False},
        "attempts": [],
        "iterations": 0,
    }
    result = verify(state)
    assert result["available"] == 100
    assert result["replan"] is True


def test_verify_stops_when_available_zero():
    state: InventoryState = {
        "sku": "SKU-1",
        "qty": 100,
        "result": {"accepted": False, "reason": "Oversell rejected: available=0", "retryable": False},
        "attempts": [],
        "iterations": 0,
        "available": 0,
    }
    result = verify(state)
    assert result["replan"] is False


def test_full_graph_oversell_then_backoff_succeed():
    """Full cycle: 300->rejected(available=100)->backoff to 100->accepted."""
    def mock_allocate(**kwargs):
        qty = kwargs.get("qty", 0)
        if qty > 100:
            return {"accepted": False, "reason": f"Oversell rejected: sku=SKU-1, requested={qty}, available=100", "retryable": False}
        return {"accepted": True, "reason": "", "retryable": False}

    with patch("inventory_graph.allocate_promo_stock", side_effect=mock_allocate):
        graph = get_compiled_inventory_graph()
        result = graph.invoke({"sku": "SKU-1", "qty": 300, "region": "CN"})

        assert result["iterations"] == 2
        assert len(result["attempts"]) == 2
        assert result["attempts"][0]["accepted"] is False
        assert result["attempts"][1]["accepted"] is True
        assert result["attempts"][1]["qty"] == 100
        assert result["replan"] is False


def test_full_graph_retryable_conflict_same_qty():
    """CAS conflict -> retry with same qty."""
    call_count = {"n": 0}

    def mock_allocate(**kwargs):
        call_count["n"] += 1
        if call_count["n"] == 1:
            return {"accepted": False, "reason": "Concurrent conflict", "retryable": True}
        return {"accepted": True, "reason": "", "retryable": False}

    with patch("inventory_graph.allocate_promo_stock", side_effect=mock_allocate):
        graph = get_compiled_inventory_graph()
        result = graph.invoke({"sku": "SKU-1", "qty": 30, "region": "CN"})

        assert result["iterations"] == 2
        assert result["attempts"][0]["retryable"] is True
        assert result["attempts"][1]["accepted"] is True
        assert result["attempts"][1]["qty"] == 30  # same qty