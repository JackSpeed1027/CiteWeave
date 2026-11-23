"""供应商适配的离线协议和失败边界测试。"""

import asyncio
import json

import httpx
import pytest

from citeweave_ai.model_provider import OpenAICompatibleProvider, ProviderConfig, ProviderError


def complete(handler):
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(handler), follow_redirects=True) as client:
            provider = OpenAICompatibleProvider(
                client, ProviderConfig("https://provider.example/v1/", "test-model", "test-key", 2.0)
            )
            return await provider.complete([{"role": "user", "content": "样例问题"}])

    return asyncio.run(run())


def test_completion_body_and_credentials():
    def respond(request):
        assert request.url == httpx.URL("https://provider.example/v1/chat/completions")
        assert request.headers["authorization"] == "Bearer test-key"
        assert request.extensions["timeout"]["read"] == 2.0
        assert json.loads(request.content) == {
            "model": "test-model",
            "messages": [{"role": "user", "content": "样例问题"}],
            "stream": False,
        }
        return httpx.Response(200, json={"choices": [{"message": {"content": "样例回答"}}]})

    assert complete(respond) == "样例回答"


@pytest.mark.parametrize(
    ("status", "body", "code"),
    [
        (401, {}, "MODEL_AUTH_FAILED"),
        (403, {}, "MODEL_AUTH_FAILED"),
        (429, {}, "MODEL_RATE_LIMITED"),
        (500, {}, "MODEL_UPSTREAM_ERROR"),
        (200, {"choices": []}, "MODEL_RESPONSE_INVALID"),
        (200, {"choices": [{"message": {"content": ["bad"]}}]}, "MODEL_RESPONSE_INVALID"),
        (200, {"choices": [{"message": {"refusal": "denied"}}]}, "MODEL_REFUSED"),
        (
            200,
            {"choices": [{"message": {"content": "partial"}, "finish_reason": "length"}]},
            "MODEL_OUTPUT_TRUNCATED",
        ),
    ],
)
def test_provider_errors(status, body, code):
    with pytest.raises(ProviderError, match=code) as error:
        complete(lambda request: httpx.Response(status, json=body))
    assert error.value.code == code


@pytest.mark.parametrize(
    ("error_type", "code"),
    [(httpx.ReadTimeout, "MODEL_TIMEOUT"), (httpx.ConnectError, "MODEL_UNAVAILABLE")],
)
def test_transport_errors(error_type, code):
    def fail(request):
        raise error_type("sample failure", request=request)

    with pytest.raises(ProviderError, match=code):
        complete(fail)


def test_redirect_is_never_followed():
    def redirect(request):
        assert request.url.host == "provider.example"
        return httpx.Response(307, headers={"Location": "https://other.example/new"})

    with pytest.raises(ProviderError, match="MODEL_UPSTREAM_ERROR"):
        complete(redirect)


@pytest.mark.parametrize("base_url", ["ftp://example.com", "https://user@example.com", "https://example.com?x=1"])
def test_provider_configuration_rejects_unsupported_base_url(base_url):
    with pytest.raises(ValueError):
        ProviderConfig(base_url, "test-model", "test-key")
