import asyncio

from httpx import ASGITransport, AsyncClient

from citeweave_ai.app import app


def test_health_reports_http_process_status() -> None:
    async def request_health():
        transport = ASGITransport(app=app)
        async with AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.get("/healthz")

    response = asyncio.run(request_health())
    assert response.status_code == 200
    assert response.json() == {"service": "citeweave-ai", "status": "ok"}
