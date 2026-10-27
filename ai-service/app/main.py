from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from .config import get_settings
from .model import stream_completion
from .rag import retrieve
from .schemas import ChatRequest, RetrievalRequest
from .tasks import process_document

settings = get_settings()
app = FastAPI(title=settings.app_name, version="1.0.0")

@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "service": "ai-service", "provider": settings.model_provider}

@app.post("/v1/chat/stream")
async def chat_stream(request: ChatRequest):
    messages = list(request.messages)
    if request.query:
        messages.append({"role": "user", "content": request.query})
    async def events():
        yield "event: start\ndata: {}\n\n"
        async for chunk in stream_completion(messages, request.model, request.temperature):
            yield f"event: delta\ndata: {chunk.replace(chr(10), ' ')}\n\n"
        yield "event: done\ndata: {}\n\n"
    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})

@app.post("/v1/rag/search")
async def rag_search(request: RetrievalRequest):
    return {"items": await retrieve(request)}

@app.post("/v1/documents/{document_id}/process")
async def queue_document(document_id: str):
    task = process_document.delay(document_id)
    return {"task_id": task.id, "status": "queued"}

