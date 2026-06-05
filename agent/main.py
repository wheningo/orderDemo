"""Agent service — LangGraph-based hot rank brain."""

from fastapi import FastAPI
from graph import get_compiled_graph, AgentState

app = FastAPI(title="hotrank-agent")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/trigger")
def trigger(state: dict | None = None):
    """Manually trigger one agent loop cycle."""
    graph = get_compiled_graph()
    initial: AgentState = state or {"top_k": []}
    result = graph.invoke(initial)
    return result


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8082)