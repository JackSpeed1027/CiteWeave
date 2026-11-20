"""HTTP entry point for the local AI Engine."""

from fastapi import FastAPI

app = FastAPI(title="CiteWeave AI Engine", version="0.1.0")


@app.get("/healthz", tags=["system"])
def health() -> dict[str, str]:
    """Report that the engine's HTTP process can handle requests."""
    return {"service": "citeweave-ai", "status": "ok"}
