"""OpenAI-compatible 文本模型供应商适配。"""

from dataclasses import dataclass, field
from math import isfinite
import re
from typing import Literal, Mapping, Sequence, TypedDict
from urllib.parse import urlsplit

import httpx


class ChatMessage(TypedDict):
    """文本模型消息的最小协议。"""

    role: Literal["system", "user", "assistant"]
    content: str


@dataclass(frozen=True)
class ToolCall:
    """模型提议的一次函数调用，参数在业务边界另行验证。"""

    id: str
    name: str
    arguments: str

    def assistant_message(self) -> dict[str, object]:
        """按 Chat Completions 协议保留调用 ID 供工具结果配对。"""
        return {"role": "assistant", "content": None, "tool_calls": [{
            "id": self.id, "type": "function",
            "function": {"name": self.name, "arguments": self.arguments},
        }]}


class ProviderError(RuntimeError):
    """保留稳定的错误码，避免把上游响应和凭据传给调用方。"""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class ProviderConfig:
    """供应商参数来自服务配置，API Key 不进入对象的调试表示。"""

    base_url: str
    model: str
    api_key: str = field(repr=False)
    timeout_seconds: float = 30.0

    def __post_init__(self) -> None:
        url = urlsplit(self.base_url)
        if (
            url.scheme not in {"http", "https"}
            or not url.hostname
            or url.username is not None
            or url.password is not None
            or url.query
            or url.fragment
        ):
            raise ValueError("供应商地址须为 HTTP(S) API 根地址，且不含认证信息、查询或片段。")
        if not self.model.strip() or not self.api_key.strip():
            raise ValueError("模型名称和 API Key 必须配置。")
        if not isfinite(self.timeout_seconds) or self.timeout_seconds <= 0:
            raise ValueError("模型调用超时必须为有限正数。")


class OpenAICompatibleProvider:
    """通过调用方管理的 HTTP 客户端执行非流式推理。"""

    def __init__(self, client: httpx.AsyncClient, config: ProviderConfig):
        self._client = client
        self._config = config
        self._endpoint = f"{config.base_url.rstrip('/')}/chat/completions"

    async def complete(self, messages: Sequence[ChatMessage]) -> str:
        """返回首选文本，区分传输故障、供应商拒绝与响应契约变更。"""
        message = await self._request(messages)
        return self._text(message)

    async def complete_with_tools(
        self, messages: Sequence[Mapping[str, object]], tools: Sequence[dict[str, object]]
    ) -> str | ToolCall:
        """读取一次工具提议或最终文本。首版每轮只处理一个函数。"""
        message = await self._request(messages, tools=tools, tool_choice="auto", max_tokens=800)
        calls = message.get("tool_calls")
        if calls is None or calls == []:
            return self._text(message)
        try:
            if not isinstance(calls, list) or len(calls) != 1:
                raise ValueError
            call = calls[0]
            identifier = call["id"]
            name = call["function"]["name"]
            arguments = call["function"]["arguments"]
            if (call["type"] != "function" or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", identifier)
                    or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", name)
                    or not isinstance(arguments, str) or len(arguments) > 2_000):
                raise ValueError
            return ToolCall(identifier, name, arguments)
        except (TypeError, ValueError, KeyError):
            raise ProviderError("MODEL_TOOL_CALL_INVALID") from None

    async def _request(
        self, messages: Sequence[Mapping[str, object]], **options: object,
    ) -> dict[str, object]:
        config = self._config
        try:
            # 认证只发往配置中的端点。显式关闭重定向，也不复用调用方可能设置的认证。
            response = await self._client.post(
                self._endpoint,
                headers={"Authorization": f"Bearer {config.api_key}"},
                auth=None,
                json={"model": config.model, "messages": list(messages), "stream": False, **options},
                timeout=config.timeout_seconds,
                follow_redirects=False,
            )
        except httpx.TimeoutException:
            raise ProviderError("MODEL_TIMEOUT") from None
        except httpx.RequestError:
            raise ProviderError("MODEL_UNAVAILABLE") from None
        if response.status_code in {401, 403}:
            raise ProviderError("MODEL_AUTH_FAILED")
        if response.status_code == 429:
            raise ProviderError("MODEL_RATE_LIMITED")
        if not response.is_success:
            raise ProviderError("MODEL_UPSTREAM_ERROR")

        try:
            choice = response.json()["choices"][0]
            message = choice["message"]
            if not isinstance(choice, dict) or not isinstance(message, dict):
                raise ValueError
        except (ValueError, TypeError, KeyError, IndexError, AttributeError):
            raise ProviderError("MODEL_RESPONSE_INVALID") from None
        if message.get("refusal"):
            raise ProviderError("MODEL_REFUSED")
        if choice.get("finish_reason") == "length":
            raise ProviderError("MODEL_OUTPUT_TRUNCATED")
        return message

    @staticmethod
    def _text(message: dict[str, object]) -> str:
        content = message.get("content")
        if not isinstance(content, str) or not content.strip():
            raise ProviderError("MODEL_RESPONSE_INVALID")
        return content
