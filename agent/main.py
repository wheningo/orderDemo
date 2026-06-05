"""Agent service — LangGraph-based hot rank brain."""

from fastapi import FastAPI

app = FastAPI(title="hotrank-agent")


@app.get("/health")
def health():
    return {"status": "UP"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8082)