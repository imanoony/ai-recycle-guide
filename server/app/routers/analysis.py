from typing import Annotated
from uuid import UUID, uuid4

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.schemas.analysis import (
    AnalysisResponse,
    SortingStatus,
    WasteItemResult,
)
from app.schemas.errors import ErrorCode, ErrorDetail, ErrorResponse
from app.services.image_input import (
    MAX_IMAGE_PIXELS,
    MAX_UPLOAD_BYTES,
    ImageTooLargeError,
    InvalidImageError,
    PreparedImage,
    prepare_image,
)
from app.services.openrouter import (
    OpenRouterVisionProvider,
    VisionProviderOutputError,
    VisionProviderUnavailable,
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
        "The image is sent to the configured OpenRouter vision model; this endpoint returns identification only, while disposal guidance is handled by a separate rules step."
    ),
)
async def analyze_waste(
    image: Annotated[UploadFile, File(description="Waste photo to analyze.")],
    region: Annotated[str | None, Form(description="Optional region hint, e.g. Seoul, Mapo-gu.")] = None,
) -> AnalysisResponse | JSONResponse:
    request_id = uuid4()
    prepared_image: PreparedImage | None = None
    try:
        uploaded_data = await image.read(MAX_UPLOAD_BYTES + 1)
        if len(uploaded_data) > MAX_UPLOAD_BYTES:
            return _error_response(
                status_code=413,
                code=ErrorCode.IMAGE_TOO_LARGE,
                message="이미지 파일은 10 MiB 이하여야 합니다.",
                request_id=request_id,
            )

        try:
            prepared_image = prepare_image(uploaded_data)
        except ImageTooLargeError:
            return _error_response(
                status_code=413,
                code=ErrorCode.IMAGE_TOO_LARGE,
                message=f"이미지 해상도는 {MAX_IMAGE_PIXELS:,}픽셀 이하여야 합니다.",
                request_id=request_id,
            )
        except InvalidImageError:
            return _error_response(
                status_code=415,
                code=ErrorCode.UNSUPPORTED_IMAGE_TYPE,
                message="JPEG, PNG, WebP 형식의 정상적인 이미지를 업로드해 주세요.",
                request_id=request_id,
            )
    finally:
        await image.close()

    assert prepared_image is not None
    provider = OpenRouterVisionProvider(get_settings())
    try:
        provider_result = await provider.identify(prepared_image, request_id=request_id)
    except VisionProviderOutputError:
        return _error_response(
            status_code=422,
            code=ErrorCode.INVALID_ANALYSIS_OUTPUT,
            message="AI 분석 결과를 확인할 수 없습니다. 다시 시도해 주세요.",
            request_id=request_id,
        )
    except VisionProviderUnavailable as exc:
        return _error_response(
            status_code=503,
            code=ErrorCode.ANALYSIS_UNAVAILABLE,
            message=exc.public_message,
            retryable=exc.retryable,
            request_id=request_id,
        )

    analysis = provider_result.analysis
    response_items = [
        WasteItemResult(
            name=item.name,
            material=item.material,
            confidence=item.confidence,
            sorting_status=SortingStatus.UNKNOWN,
            steps=[],
            cautions=[],
            sources=[],
        )
        for item in analysis.items
    ]
    normalized_region = region.strip() if region and region.strip() else None
    return AnalysisResponse(
        request_id=request_id,
        status=analysis.status,
        region=normalized_region,
        items=response_items,
        message=analysis.message,
    )


def _error_response(
    *,
    status_code: int,
    code: ErrorCode,
    message: str,
    request_id: UUID,
    retryable: bool = False,
) -> JSONResponse:
    response = ErrorResponse(
        error=ErrorDetail(
            code=code,
            message=message,
            request_id=request_id,
            retryable=retryable,
        )
    )
    return JSONResponse(status_code=status_code, content=response.model_dump(mode="json"))