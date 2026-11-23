"""验证真实工作流对合成模型响应的校验边界。"""

import asyncio
import json

import httpx
import pytest

from citeweave_ai.app import app
from citeweave_ai.evidence import EvidenceBatch, EvidenceSegment
from citeweave_ai.model_provider import OpenAICompatibleProvider, ProviderConfig, ProviderError
from citeweave_ai.transcript_summary import generate_summary, provider_config


def batch(second_text="第二句"):
    return EvidenceBatch(segments=[
        EvidenceSegment(evidenceId=identifier, sourceId="fixture", sourceVersionId="v1",
                        locationType="transcript", startMs=0, endMs=1000, text=text)
        for identifier, text in [("first", "第一句"), ("second", second_text)]
    ])


async def generate(evidence, reference):
    def respond(request):
        messages = json.loads(request.content)["messages"]
        selected = json.loads(messages[-1]["content"])
        assert [item["id"] for item in selected] == ["first"]
        return httpx.Response(200, json={"choices": [{"message": {"content":
            json.dumps({"summary": "有效结论", "evidenceIds": [reference]})}}]})

    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        provider = OpenAICompatibleProvider(client, ProviderConfig("https://example.invalid/v1", "test", "fake"))
        return await generate_summary(evidence, provider)


def test_uses_actual_prompt_ids_and_computes_truncation():
    result = asyncio.run(generate(batch("字" * 12_000), "first"))
    assert result.truncated is True
    assert result.inputEvidenceIds == ["first"]
    assert result.evidenceIds == ["first"]


def test_rejects_citation_to_trimmed_segment():
    with pytest.raises(ProviderError, match="SUMMARY_INVALID_CITATION"):
        asyncio.run(generate(batch("字" * 12_000), "second"))


def test_summary_without_model_config_uses_stable_error():
    async def request():
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            return await client.post("/internal/v1/transcript-summaries", json=batch().model_dump())

    app.dependency_overrides[provider_config] = lambda: None
    try:
        response = asyncio.run(request())
        assert response.status_code == 503
        assert response.json() == {"code": "SUMMARY_MODEL_NOT_CONFIGURED"}
    finally:
        app.dependency_overrides.pop(provider_config)
