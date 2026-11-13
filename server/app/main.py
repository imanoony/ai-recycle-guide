from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.routers.analysis import router as analysis_router
from app.schemas.errors import ErrorCode, ErrorDetail, ErrorResponse

app = FastAPI(
    title="AI Recycle Guide API",
    description="사진 속 폐기물을 식별하고 분리배출 방법을 안내하는 API",
    version="0.1.0",
)
app.include_router(analysis_router, prefix="/api/v1", tags=["analysis"])


@app.exception_handler(RequestValidationError)
async def request_validation_error_handler(
    request: Request, exc: RequestValidationError
) -> JSONResponse:
    error = ErrorResponse(
        error=ErrorDetail(
            code=ErrorCode.INVALID_REQUEST,
            message="요청 형식이 올바르지 않습니다.",
            request_id=uuid4(),
            retryable=False,
        )
    )
    return JSONResponse(status_code=400, content=error.model_dump(mode="json"))


@app.exception_handler(Exception)
async def unexpected_error_handler(request: Request, exc: Exception) -> JSONResponse:
    error = ErrorResponse(
        error=ErrorDetail(
            code=ErrorCode.INTERNAL_ERROR,
            message="서버 내부 오류가 발생했습니다.",
            request_id=uuid4(),
            retryable=False,
        )
    )
    return JSONResponse(status_code=500, content=error.model_dump(mode="json"))


@app.get("/health", tags=["health"])
def health_check() -> dict[str, str]:
    return {"status": "ok"}
