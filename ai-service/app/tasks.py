from celery import Celery
from .config import get_settings

celery_app = Celery("aicoding", broker=get_settings().redis_url, backend=get_settings().redis_url)

@celery_app.task(name="documents.process")
def process_document(document_id: str) -> dict:
    return {"document_id": document_id, "status": "queued"}

