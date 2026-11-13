from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse

from app.schemas.analysis import AnalysisResponse
from app.schemas.errors import ErrorCode, ErrorDetail, ErrorResponse
from app.services.image_input import (
    MAX_IMAGE_PIXELS,
    MAX_UPLOAD_BYTES,
    ImageTooLargeError,
    InvalidImageError,
    prepare_image,
)

router = APIRouter()

_ERROR_RESPONSES = {
    400: {"model": ErrorResponse, "description": "Malformed request."},
    413: {"model": ErrorResponse, "description": "Image exceeds the byte or pixel limit."},
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
        f"Images are limited to {MAX_IMAGE_PIXELS:,} pixels, decoded from their contents, orientation-corrected, and normalized to JPEG. "
        "The upload is not retained after the request. The analysis provider is not connected yet; valid requests currently return `503 analysis_unavailable`."
    ),
)
async def analyze_waste(
    image: Annotated[UploadFile, File(description="Waste photo to analyze.")],
    region: Annotated[str | None, Form(description="Optional region hint, e.g. Seoul, Mapo-gu.")] = None,
) -> JSONResponse:
    try:
        uploaded_data = await image.read(MAX_UPLOAD_BYTES + 1)
        if len(uploaded_data) > MAX_UPLOAD_BYTES:
            return _error_response(
                status_code=413,
                code=ErrorCode.IMAGE_TOO_LARGE,
                message="이미지 파일은 10 MiB 이하여야 합니다.",
            )

        try:
            prepare_image(uploaded_data)
        except ImageTooLargeError:
            return _error_response(
                status_code=413,
                code=ErrorCode.IMAGE_TOO_LARGE,
                message=f"이미지 해상도는 {MAX_IMAGE_PIXELS:,}픽셀 이하여야 합니다.",
            )
        except InvalidImageError:
            return _error_response(
                status_code=415,
                code=ErrorCode.UNSUPPORTED_IMAGE_TYPE,
                message="JPEG, PNG, WebP 형식의 정상적인 이미지를 업로드해 주세요.",
            )
    finally:
        await image.close()

    # The provider adapter will consume the normalized image in the next implementation step.
    return _error_response(
        status_code=503,
        code=ErrorCode.ANALYSIS_UNAVAILABLE,
        message="이미지 분석 서비스를 아직 사용할 수 없습니다.",
        retryable=True,
    )


def _error_response(
    *,
    status_code: int,
    code: ErrorCode,
    message: str,
    retryable: bool = False,
) -> JSONResponse:
    response = ErrorResponse(
        error=ErrorDetail(
            code=code,
            message=message,
            request_id=uuid4(),
            retryable=retryable,
        )
    )
    return JSONResponse(status_code=status_code, content=response.model_dump(mode="json"))