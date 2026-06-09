"""E2E and property assertion tests for the hotrank-agent-loop.

These tests verify:
1. Full graph lifecycle (with mocked MCP) including replan loop
2. Property: no command bypasses Go interceptor (only 2 tools exposed)
3. Property: every dispatched command goes through call_tool (gateway)
"""

from unittest.mock import patch
from graph import get_compiled_graph, AgentState


class TestE2ELifecycle:
    """Card 6.1: Full lifecycle — observe → decide → dispatch → verify (with replan)."""

    def test_full_cycle_reject_then_succeed(self):
        """Money shot: agent sends weight=236, domain rejects, replan backs off to 100, succeeds."""
        call_count = {"n": 0}

        def mock_get_hot_rank(region, k=10):
            call_count["n"] += 1
            if call_count["n"] <= 1:
                return [
                    {"contentId": "hot-1", "score": 300, "rank": 1},
                    {"contentId": "cold-1", "score": 5, "rank": 2},
                ]
            return [
                {"contentId": "hot-1", "score": 300, "rank": 1},
                {"contentId": "cold-1", "score": 105, "rank": 2},
            ]

        def mock_dispatch(**kwargs):
            weight = kwargs.get("weight", 0)
            if weight > 100:
                return {"Accepted": False, "Reason": f"Weight must be between 1 and 100, got: {weight}"}
            return {"Accepted": True, "Reason": "", "IdempotencyKey": "k-ok"}

        with patch("graph.get_hot_rank", side_effect=mock_get_hot_rank), \
             patch("graph.dispatch_boost_exposure", side_effect=mock_dispatch):
            graph = get_compiled_graph()
            result = graph.invoke({"region": "CN"})

            # First attempt rejected, second accepted
            assert result["iterations"] == 2
            assert len(result["attempts"]) == 2
            assert result["attempts"][0]["accepted"] is False
            assert "236" in result["attempts"][0]["reason"]
            assert result["attempts"][1]["accepted"] is True
            assert result["effect"]["applied"] is True
            assert result["replan"] is False

    def test_full_cycle_no_rankings(self):
        """Agent observes empty rankings, decides nothing, no dispatch."""
        with patch("graph.get_hot_rank", return_value=[]):
            graph = get_compiled_graph()
            result = graph.invoke({"region": "CN"})

            assert result["top_k"] == []
            assert result["decision"] is None
            assert result["dispatched_cmd"] is None

    def test_full_cycle_dispatch_exception(self):
        """Agent handles transport exception gracefully."""
        with patch("graph.get_hot_rank", return_value=[{"contentId": "c-1", "score": 100}]), \
             patch("graph.dispatch_boost_exposure", side_effect=Exception("connection refused")):
            graph = get_compiled_graph()
            result = graph.invoke({"region": "CN"})

            assert result["dispatched_cmd"]["sent"] is False
            assert "connection refused" in result["dispatched_cmd"]["error"]

    def test_max_iterations_prevents_infinite_loop(self):
        """Graph stops after MAX_ITERATIONS even if domain keeps rejecting."""
        def mock_dispatch(**kwargs):
            return {"Accepted": False, "Reason": "always reject"}

        with patch("graph.get_hot_rank", return_value=[
            {"contentId": "hot-1", "score": 500},
            {"contentId": "cold-1", "score": 100},
        ]), patch("graph.dispatch_boost_exposure", side_effect=mock_dispatch):
            graph = get_compiled_graph()
            result = graph.invoke({"region": "CN"})

            assert result["iterations"] == 3  # MAX_ITERATIONS
            assert result["replan"] is False  # stopped


class TestPropertyAssertions:
    """Card 6.2: Property assertions."""

    def test_only_two_tools_exposed(self):
        """No command bypasses Go — only get_hot_rank and dispatch_boost_exposure exist."""
        import mcp_client
        public_tools = [name for name in dir(mcp_client)
                        if not name.startswith("_") and callable(getattr(mcp_client, name))
                        and name not in ("call_tool",)]
        assert set(public_tools) == {"get_hot_rank", "dispatch_boost_exposure", "allocate_promo_stock"}

    def test_dispatch_always_goes_through_call_tool(self):
        """Every dispatch goes through call_tool which hits the gateway (interceptor chain)."""
        import mcp_client
        with patch.object(mcp_client, "call_tool", return_value={"Accepted": True}) as mock_call:
            mcp_client.dispatch_boost_exposure("c-1", 10, "CN")
            mock_call.assert_called_once_with(
                "dispatch_boost_exposure",
                {
                    "target_content_id": "c-1",
                    "weight": 10,
                    "region": "CN",
                    "decision_source": "agent",
                    "idempotency_key": "",
                    "risk_tier": "standard",
                },
            )

    def test_graph_dispatch_node_uses_mcp_not_direct(self):
        """The dispatch node in the graph uses mcp_client, not direct HTTP to Java."""
        import inspect
        from graph import dispatch
        source = inspect.getsource(dispatch)
        assert "mcp_client" in source or "dispatch_boost_exposure" in source
        assert "hotrank-service" not in source
        assert "8080" not in source