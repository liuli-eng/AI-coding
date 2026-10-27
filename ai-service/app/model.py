import json
from collections.abc import AsyncIterator
import httpx
from .config import get_settings
from .schemas import Message

async def stream_completion(messages: list[Message], model: str | None = None, temperature: float = 0.2) -> AsyncIterator[str]:
    settings = get_settings()
    if settings.model_provider == "mock" or not settings.model_base_url:
        for word in "这是 AI 编程助手的演示回答。请配置模型服务后接入真实模型。":
            yield word
        return
    payload = {"model": model or settings.model_name, "messages": [m.model_dump() for m in messages], "temperature": temperature, "stream": True}
    headers = {"Authorization": f"Bearer {settings.model_api_key}"}
    async with httpx.AsyncClient(timeout=None) as client:
        async with client.stream("POST", settings.model_base_url, json=payload, headers=headers) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    delta = json.loads(data).get("choices", [{}])[0].get("delta", {}).get("content")
                    if delta:
                        yield delta
                except json.JSONDecodeError:
                    continue

