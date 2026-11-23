"""问答结构、实际输入和拒答行为的离线供应商测试。"""

import asyncio
import json

import httpx
import pytest

from citeweave_ai.model_provider import OpenAICompatibleProvider, ProviderConfig, ProviderError
from citeweave_ai.transcript_question import QuestionBatch, ask_question


def run(output):
    batch = QuestionBatch(question="缓存如何失效", segments=[
        {"evidenceId": identifier, "sourceId": "1", "sourceVersionId": "test", "locationType": "transcript",
         "startMs": 0, "endMs": 1000, "text": text}
        for identifier, text in [("matching", "缓存需要重建"), ("trimmed", "字" * 12_000)]
    ])

    def respond(request):
        prompt = json.loads(json.loads(request.content)["messages"][-1]["content"])
        assert prompt["question"] == "缓存如何失效"
        assert [item["id"] for item in prompt["evidence"]] == ["matching"]
        assert prompt["evidence"][0]["sourceId"] == "1"
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(output)}}]})

    async def ask():
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            return await ask_question(batch, OpenAICompatibleProvider(
                client, ProviderConfig("http://example.invalid/v1", "fake", "fake")
            ))

    return asyncio.run(ask())


def test_answer_returns_actual_evidence():
    result = run({"status": "ANSWERED", "answer": "需要重建", "evidenceIds": ["matching"]})
    assert result.inputEvidenceIds == ["matching"]
    assert result.evidenceIds == ["matching"]
    assert result.truncated


def test_refusal_contains_no_answer_or_citations():
    result = run({"status": "INSUFFICIENT_EVIDENCE", "answer": None, "evidenceIds": []})
    assert result.status == "INSUFFICIENT_EVIDENCE"
    assert result.inputEvidenceIds == ["matching"]
    assert result.answer is None


@pytest.mark.parametrize("location_type, field", [("paragraph", "paragraph"), ("page", "page")])
def test_document_model_input_has_one_location_and_no_time_range(location_type, field):
    batch = QuestionBatch(question="怎么续租", segments=[{
        "evidenceId": "paragraph-id", "sourceId": "2", "sourceVersionId": "v1",
        "locationType": location_type, field: 3, "text": "每 10 秒续租。",
    }])

    def respond(request):
        payload = json.loads(json.loads(request.content)["messages"][-1]["content"])
        assert payload["evidence"] == [{
            "id": "paragraph-id", "sourceId": "2", field: 3, "text": "每 10 秒续租。",
        }]
        output = {"status": "ANSWERED", "answer": "每 10 秒", "evidenceIds": ["paragraph-id"]}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(output)}}]})

    async def ask():
        async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
            return await ask_question(batch, OpenAICompatibleProvider(
                client, ProviderConfig("http://example.invalid/v1", "fake", "fake")
            ))

    assert asyncio.run(ask()).evidenceIds == ["paragraph-id"]


@pytest.mark.parametrize(("output", "code"), [
    ({"status": "ANSWERED", "answer": "错误", "evidenceIds": ["trimmed"]}, "SUMMARY_INVALID_CITATION"),
    ({"status": "INSUFFICIENT_EVIDENCE", "answer": "不应返回", "evidenceIds": []}, "SUMMARY_INVALID_OUTPUT"),
])
def test_rejects_invalid_model_outputs(output, code):
    with pytest.raises(ProviderError, match=code):
        run(output)
