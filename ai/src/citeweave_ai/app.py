"""HTTP entry point for the local AI Engine."""

from fastapi import FastAPI

from .transcript_summary import router
from .transcript_question import router as question_router
from .research import router as research_router

app = FastAPI(title="CiteWeave AI Engine", version="0.1.0")
app.include_router(router)
app.include_router(question_router)
app.include_router(research_router)


@app.get("/healthz", tags=["system"])
def health() -> dict[str, str]:
    """Report that the engine's HTTP process can handle requests."""
    return {"service": "citeweave-ai", "status": "ok"}
