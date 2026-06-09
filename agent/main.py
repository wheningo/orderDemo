"""Agent service — LangGraph-based hot rank brain."""

from fastapi import FastAPI
from graph import get_compiled_graph, AgentState
from inventory_graph import get_compiled_inventory_graph, InventoryState

app = FastAPI(title="hotrank-agent")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/trigger")
def trigger(region: str = "CN"):
    """Manually trigger one agent loop cycle."""
    graph = get_compiled_graph()
    initial: AgentState = {"region": region, "top_k": []}
    result = graph.invoke(initial)
    return result


@app.post("/trigger/inventory")
def trigger_inventory(sku: str = "SKU-1", qty: int = 300, region: str = "CN"):
    """Trigger inventory allocation — demonstrates oversell->backoff->retry."""
    graph = get_compiled_inventory_graph()
    initial: InventoryState = {"sku": sku, "qty": qty, "region": region}
    result = graph.invoke(initial)
    return result


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8082)