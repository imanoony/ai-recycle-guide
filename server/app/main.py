from fastapi import FastAPI

app = FastAPI(
    title="AI Recycle Guide API",
    description="사진 속 폐기물을 식별하고 분리배출 방법을 안내하는 API",
    version="0.1.0",
)


@app.get("/health", tags=["health"])
def health_check() -> dict[str, str]:
    return {"status": "ok"}
