"""用真实模型适配器和离线 HTTP 夹具检验工具协议及读取边界。"""

import asyncio
import json

import httpx
import pytest

from citeweave_ai.app import app
from citeweave_ai.model_provider import OpenAICompatibleProvider, ProviderConfig, ProviderError
from citeweave_ai.research import (
    CheckpointTarget, ContextRequest, CoreEvidenceClient, ResearchRequest, rebuild_context, run_research,
)

EVIDENCE = {
    "evidenceId": "7:current:2",
    "sourceId": "7",
    "sourceVersionId": "current",
    "locationType": "paragraph",
    "paragraph": 2,
    "text": "  Cache rebuild follows expiry.  ",
}


def saved_context():
    return {
        "question": "Cache rebuild", "sourceIds": [7, 8],
        "trace": [
            {"callId": "call_search", "tool": "search_evidence", "evidenceIds": [EVIDENCE["evidenceId"]]},
            {"callId": "call_read", "tool": "read_evidence", "evidenceIds": [EVIDENCE["evidenceId"]]},
        ],
        "context": {"version": 1, "remainingToolSteps": 1, "actions": [
            {"query": "Cache rebuild", "evidenceId": None},
            {"sourceId": "7", "evidenceId": EVIDENCE["evidenceId"], "query": None},
        ]},
    }


def restore(payload, reply_status=200):
    calls = []

    def core(request):
        calls.append(request)
        assert request.method == "GET"
        assert request.url.path == "/api/sources/7/evidence"
        assert request.url.params["evidenceId"] == EVIDENCE["evidenceId"]
        return httpx.Response(reply_status, json=EVIDENCE)

    async def execute():
        async with httpx.AsyncClient(transport=httpx.MockTransport(core), base_url="http://127.0.0.1") as client:
            return await rebuild_context(ContextRequest.model_validate(payload), CoreEvidenceClient(client))

    return execute, calls


def test_rebuild_restores_tool_pairs_by_exact_version_with_one_read():
    execute, calls = restore(saved_context())
    rebuilt = asyncio.run(execute())
    assert len(calls) == 1
    assert rebuilt.remaining_steps == 1
    assert rebuilt.verified == {EVIDENCE["evidenceId"]}
    assert [step["role"] for step in rebuilt.messages] == ["system", "user", "assistant", "tool", "assistant", "tool"]
    for assistant, result in zip(rebuilt.messages[2::2], rebuilt.messages[3::2]):
        assert assistant["tool_calls"][0]["id"] == result["tool_call_id"]
    assert json.loads(rebuilt.messages[3]["content"]) == [EVIDENCE]
    assert json.loads(rebuilt.messages[5]["content"]) == EVIDENCE


def test_rebuild_stops_when_the_version_is_unavailable():
    execute, calls = restore(saved_context(), reply_status=404)
    with pytest.raises(ProviderError, match="RESEARCH_EVIDENCE_UNAVAILABLE"):
        asyncio.run(execute())
    assert len(calls) == 1


def test_rebuild_rejects_out_of_scope_and_wrong_budget_before_reads():
    request = saved_context()
    request["sourceIds"] = [8]
    execute, calls = restore(request)
    with pytest.raises(ProviderError, match="RESEARCH_EVIDENCE_OUT_OF_SCOPE"):
        asyncio.run(execute())
    assert not calls
    request = saved_context()
    request["context"]["remainingToolSteps"] = 2
    execute, calls = restore(request)
    with pytest.raises(ProviderError, match="RESEARCH_CONTEXT_INVALID"):
        asyncio.run(execute())
    assert not calls


@pytest.mark.parametrize("core_status", [200, 404])
def test_resume_passes_rebuilt_pairs_to_model_and_never_repeats_search(core_status):
    snapshot = saved_context()
    calls = []

    def respond_core(request):
        calls.append("read")
        assert request.method == "GET"
        return httpx.Response(core_status, json=EVIDENCE)

    def respond_model(request):
        calls.append("model")
        messages = json.loads(request.content)["messages"]
        assert [item["tool_call_id"] for item in messages if item["role"] == "tool"] == ["call_search", "call_read"]
        return httpx.Response(200, json={"choices": [{"message": answer(EVIDENCE["evidenceId"])}]})

    async def execute():
        async with (
            httpx.AsyncClient(transport=httpx.MockTransport(respond_core), base_url="http://127.0.0.1") as core,
            httpx.AsyncClient(transport=httpx.MockTransport(respond_model)) as model,
        ):
            request = ResearchRequest.model_validate({
                "question": snapshot["question"], "sourceIds": snapshot["sourceIds"],
                "checkpoint": {"requestKey": "1adb2edc-3ebb-48a7-8de8-3f784da377c1", "token": "x" * 36},
                "resume": {"trace": snapshot["trace"], "context": snapshot["context"]},
            })
            provider = OpenAICompatibleProvider(model, ProviderConfig("http://127.0.0.1/v1", "fixture", "fake"))
            return await run_research(request, provider, CoreEvidenceClient(core), [])

    if core_status == 200:
        result = asyncio.run(execute())
        assert result.status == "ANSWERED" and len(result.trace) == 2
        assert calls == ["read", "model"]
    else:
        with pytest.raises(ProviderError, match="RESEARCH_EVIDENCE_UNAVAILABLE"):
            asyncio.run(execute())
        assert calls == ["read"]


def tool(call_id, name, arguments):
    return {"tool_calls": [{"type": "function", "id": call_id, "function": {
        "name": name, "arguments": json.dumps(arguments),
    }}], "content": None}


def answer(evidence_id):
    return {"content": json.dumps({
        "status": "ANSWERED", "answer": "缓存过期后重建。", "evidenceIds": [evidence_id],
    })}


def run(decisions, change_read=False, checkpoint=None, checkpoint_status=204):
    trace, http_calls, model_payloads = [], [], []

    def model_handler(request):
        payload = json.loads(request.content)
        if checkpoint:
            assert checkpoint.token.get_secret_value() not in request.content.decode()
            assert checkpoint.requestKey not in request.content.decode()
            assert http_calls.count(f"/internal/v1/research/{checkpoint.requestKey}/checkpoints") == len(model_payloads)
        model_payloads.append(payload)
        return httpx.Response(200, json={"choices": [{"message": next(decisions)}]})

    def core_handler(request):
        http_calls.append(request.url.path)
        if checkpoint and request.url.path == f"/internal/v1/research/{checkpoint.requestKey}/checkpoints":
            assert request.headers["X-CiteWeave-Checkpoint-Token"] == checkpoint.token.get_secret_value()
            checkpoint_body = json.loads(request.content)
            steps = checkpoint_body["trace"]
            assert len(steps) == len(model_payloads)
            context = checkpoint_body["context"]
            assert context["version"] == 1 and context["remainingToolSteps"] == 3 - len(steps)
            assert context["actions"][0] == {"query": "Cache rebuild"}
            if len(steps) == 2:
                assert context["actions"][1] == {"sourceId": "7", "evidenceId": EVIDENCE["evidenceId"]}
            assert EVIDENCE["text"].strip() not in request.content.decode()
            return httpx.Response(checkpoint_status)
        if request.url.path == "/api/source-evidence/search":
            assert json.loads(request.content) == {"sourceIds": [7, 8], "query": "Cache rebuild"}
            return httpx.Response(200, json=[EVIDENCE])
        assert request.url.path == "/api/sources/7/evidence"
        assert request.url.params["evidenceId"] == EVIDENCE["evidenceId"]
        return httpx.Response(200, json=EVIDENCE | ({"text": "Changed"} if change_read else {}))

    async def execute():
        async with (
            httpx.AsyncClient(transport=httpx.MockTransport(model_handler)) as model,
            httpx.AsyncClient(transport=httpx.MockTransport(core_handler), base_url="http://127.0.0.1") as core,
        ):
            provider = OpenAICompatibleProvider(model, ProviderConfig("http://127.0.0.1/v1", "fixture", "fake"))
            return await run_research(
                ResearchRequest(question="Cache rebuild", sourceIds=[7, 8], checkpoint=checkpoint),
                provider, CoreEvidenceClient(core), trace,
            )

    return execute, http_calls, model_payloads, trace


def test_search_read_and_answer_pair_each_tool_result_to_its_call():
    execute, calls, messages, trace = run(iter([
        tool("call_search", "search_evidence", {"query": "Cache rebuild"}),
        tool("call_read", "read_evidence", {"sourceId": "7", "evidenceId": EVIDENCE["evidenceId"]}),
        answer(EVIDENCE["evidenceId"]),
    ]))
    response = asyncio.run(execute())
    assert response.status == "ANSWERED"
    assert response.evidenceIds == [EVIDENCE["evidenceId"]]
    assert calls == ["/api/source-evidence/search", "/api/sources/7/evidence"]
    assert [step.callId for step in trace] == ["call_search", "call_read"]
    assert messages[-1]["max_tokens"] == 800
    for assistant, result in zip(messages[-1]["messages"][2::2], messages[-1]["messages"][3::2]):
        assert assistant["tool_calls"][0]["id"] == result["tool_call_id"]
    assert json.loads(messages[-1]["messages"][-1]["content"]) == EVIDENCE


@pytest.mark.parametrize(("decision", "code"), [
    (tool("call_bad", "write_note", {}), "RESEARCH_TOOL_NOT_ALLOWED"),
    (tool("call_bad", "search_evidence", {"query": "Cache rebuild", "sourceIds": [999]}),
     "RESEARCH_TOOL_ARGUMENTS_INVALID"),
    (tool("call_bad", "read_evidence", {"sourceId": "999", "evidenceId": "999:current:1"}),
     "RESEARCH_EVIDENCE_OUT_OF_SCOPE"),
    (tool("call_bad", "read_evidence", {"sourceId": "8", "evidenceId": EVIDENCE["evidenceId"]}),
     "RESEARCH_EVIDENCE_OUT_OF_SCOPE"),
    (tool("call_search", "search_evidence", {"query": "Cache rebuild"}), "RESEARCH_TOOL_DUPLICATE"),
    (answer(EVIDENCE["evidenceId"]), "RESEARCH_CITATION_INVALID"),
])
def test_rejected_decision_never_dispatches_an_extra_call(decision, code):
    execute, calls, _, trace = run(iter([
        tool("call_search", "search_evidence", {"query": "Cache rebuild"}),
        decision,
    ]))
    with pytest.raises(ProviderError, match=code):
        asyncio.run(execute())
    assert calls == ["/api/source-evidence/search"]
    assert len(trace) == 1


def test_changed_evidence_cannot_be_returned_to_model():
    execute, calls, messages, trace = run(iter([
        tool("call_search", "search_evidence", {"query": "Cache rebuild"}),
        tool("call_read", "read_evidence", {"sourceId": "7", "evidenceId": EVIDENCE["evidenceId"]}),
    ]), change_read=True)
    with pytest.raises(ProviderError, match="RESEARCH_EVIDENCE_CHANGED"):
        asyncio.run(execute())
    assert len(calls) == len(messages) == 2
    assert len(trace) == 1


def test_budget_allows_three_tools_and_does_not_dispatch_the_fourth():
    execute, calls, messages, trace = run(iter(
        tool(f"call_{step}", "search_evidence", {"query": "Cache rebuild"}) for step in range(4)
    ))
    with pytest.raises(ProviderError, match="RESEARCH_STEP_LIMIT"):
        asyncio.run(execute())
    assert len(calls) == len(trace) == 3
    assert len(messages) == 4


def test_parallel_tool_response_is_rejected_before_java_reads():
    decision = tool("call_a", "search_evidence", {"query": "Cache rebuild"})
    decision["tool_calls"].extend(tool("call_b", "search_evidence", {"query": "Cache rebuild"})["tool_calls"])
    execute, calls, _, trace = run(iter([decision]))
    with pytest.raises(ProviderError, match="MODEL_TOOL_CALL_INVALID"):
        asyncio.run(execute())
    assert not calls and not trace


@pytest.mark.parametrize("status", [204, 409])
def test_checkpoints_are_confirmed_before_next_model_call(status):
    target = CheckpointTarget(
        requestKey="1adb2edc-3ebb-48a7-8de8-3f784da377c1",
        token="3ea5f0e5-9911-447d-8482-8b8334a77818",
    )
    execute, calls, messages, trace = run(iter([
        tool("call_search", "search_evidence", {"query": "Cache rebuild"}),
        tool("call_read", "read_evidence", {"sourceId": "7", "evidenceId": EVIDENCE["evidenceId"]}),
        answer(EVIDENCE["evidenceId"]),
    ]), checkpoint=target, checkpoint_status=status)
    callback = f"/internal/v1/research/{target.requestKey}/checkpoints"
    if status == 204:
        assert asyncio.run(execute()).status == "ANSWERED"
        assert calls == ["/api/source-evidence/search", callback, "/api/sources/7/evidence", callback]
        assert len(messages) == 3 and len(trace) == 2
    else:
        with pytest.raises(ProviderError, match="RESEARCH_CORE_INVALID"):
            asyncio.run(execute())
        assert calls == ["/api/source-evidence/search", callback]
        assert len(messages) == 1 and not trace


def test_endpoint_disabled_by_default_and_rejects_duplicate_scope(monkeypatch):
    monkeypatch.delenv("CITEWEAVE_RESEARCH_ENABLED", raising=False)

    async def verify():
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app), base_url="http://127.0.0.1") as client:
            endpoint = "/internal/v1/research"
            reply = await client.post(endpoint, json={"sourceIds": [7], "question": "Cache rebuild"})
            assert reply.status_code == 503
            assert reply.json() == {"code": "RESEARCH_DISABLED", "trace": []}
            reply = await client.post(endpoint, json={"sourceIds": [7, 7], "question": "Cache rebuild"})
            assert reply.status_code == 422

    asyncio.run(verify())
