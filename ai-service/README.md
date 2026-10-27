# AI 服务

FastAPI 服务负责模型适配、流式回答、RAG 检索入口和文档异步任务。

```bash
cp .env.example .env
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

接口：`GET /health`、`POST /v1/chat/stream`、`POST /v1/rag/search`、`POST /v1/documents/{id}/process`。

