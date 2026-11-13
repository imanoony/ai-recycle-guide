from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse

from app.schemas.analysis import AnalysisResponse
from app.schemas.errors import ErrorCode, ErrorDetail, ErrorResponse

router = APIRouter()

_ERROR_RESPONSES = {
    400: {"model": ErrorResponse, "description": "Malformed request."},
    413: {"model": ErrorResponse, "description": "Image exceeds the upload limit."},
    415: {"model": ErrorResponse, "description": "Unsupported or invalid image."},
    422: {"model": ErrorResponse, "description": "Analysis output could not be validated."},
    500: {"model": ErrorResponse, "description": "Unexpected server error."},
    503: {"model": ErrorResponse, "description": "Analysis provider is unavailable."},
}


@router.post(
    "/analyze",
    response_model=AnalysisResponse,
    responses=_ERROR_RESPONSES,
    summary="Analyze a waste photo",
    description=(
        "Accepts one JPEG, PNG, or WebP image up to 10 MiB as multipart field `image` and an optional human-readable `region`. "
        "The upload contract allows JPEG, PNG, or WebP up to 10 MiB. Input validation is pending. The analysis provider is not connected yet; requests currently return `503 analysis_unavailable`."
    ),
)
async def analyze_waste(
    image: Annotated[UploadFile, File(description="Waste photo to analyze.")],
    region: Annotated[str | None, Form(description="Optional region hint, e.g. Seoul, Mapo-gu.")] = None,
) -> JSONResponse:
    # Keep the upload contract visible in OpenAPI while provider integration is pending.
    await image.close()
    error = ErrorResponse(
        error=ErrorDetail(
            code=ErrorCode.ANALYSIS_UNAVAILABLE,
            message="이미지 분석 서비스를 아직 사용할 수 없습니다.",
            request_id=uuid4(),
            retryable=True,
        )
    )
    return JSONResponse(status_code=503, content=error.model_dump(mode="json"))


