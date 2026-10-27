from typing import Literal
from pydantic import BaseModel, Field

class Message(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str

class ChatRequest(BaseModel):
    messages: list[Message] = Field(default_factory=list)
    query: str = ""
    model: str | None = None
    knowledge_base_id: str | None = None
    temperature: float = Field(default=0.2, ge=0, le=2)

class RetrievalRequest(BaseModel):
    query: str
    knowledge_base_id: str | None = None
    top_k: int = Field(default=5, ge=1, le=20)

