from typing import Annotated
from uuid import UUID, uuid4

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.schemas.analysis import (
    AnalysisResponse,
    AnalysisStatus,
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
from app.services.recycling_rules import (
    fallback_sources,
    region_name,
    resolve_rule_ids,
    rule_context_json,
)
from app.services.gemini import (
    GeminiAnalysisProvider,
    GeminiProviderOutputError,
    GeminiProviderUnavailable,
)

router = APIRouter()

_ERROR_RESPONSES = {
    400: {"model": ErrorResponse, "description": "Malformed request."},
    413: {"model": ErrorResponse, "description": "Image exceeds the byte or pixel limit."},
    415: {"model": ErrorResponse, "description": "Unsupported or invalid image."},
    422: {"model": ErrorResponse, "description": "Analysis output could not be validated."},
    429: {"model": ErrorResponse, "description": "Analysis provider rate limit reached."},
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
        "The image and the Gwanak-gu rules dataset are sent together to the configured Gemini model for identification and disposal guidance. "
        "The Gwanak-gu rules are always applied; the optional region field is retained for compatibility but does not change the applied region."
    ),
)
async def analyze_waste(
    image: Annotated[UploadFile, File(description="Waste photo to analyze.")],
    region: Annotated[str | None, Form(description="Optional compatibility field. This demo always applies Gwanak-gu rules.")] = None,
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
    provider = GeminiAnalysisProvider(get_settings())
    try:
        provider_result = await provider.analyze(
            prepared_image,
            request_id=request_id,
            rule_context=rule_context_json(),
        )
    except GeminiProviderOutputError:
        return _error_response(
            status_code=422,
            code=ErrorCode.INVALID_ANALYSIS_OUTPUT,
            message="AI 분석 결과를 확인할 수 없습니다. 다시 시도해 주세요.",
            request_id=request_id,
        )
    except GeminiProviderUnavailable as exc:
        return _error_response(
            status_code=exc.status_code,
            code=(
                ErrorCode.RATE_LIMITED
                if exc.status_code == 429
                else ErrorCode.ANALYSIS_UNAVAILABLE
            ),
            message=exc.public_message,
            retryable=exc.retryable,
            request_id=request_id,
        )

    analysis = provider_result.analysis
    response_items: list[WasteItemResult] = []
    for item in analysis.items:
        rules_valid, rule_status, sources = resolve_rule_ids(item.rule_ids)
        guidance_supported = (
            rules_valid
            and rule_status == item.sorting_status
            and bool(sources)
            and bool(item.steps)
            and item.sorting_status
            not in {SortingStatus.UNKNOWN, SortingStatus.LOCAL_CONFIRMATION_REQUIRED}
        )
        if guidance_supported:
            sorting_status = item.sorting_status
            steps = item.steps
            cautions = item.cautions
        else:
            sorting_status = SortingStatus.LOCAL_CONFIRMATION_REQUIRED
            steps = []
            cautions = []
            if not sources:
                sources = fallback_sources()

        if analysis.status != AnalysisStatus.IDENTIFIED:
            steps = []
            cautions = []

        response_items.append(
            WasteItemResult(
                name=item.name,
                material=item.material,
                confidence=item.confidence,
                sorting_status=sorting_status,
                steps=steps,
                cautions=cautions,
                sources=sources,
            )
        )

    return AnalysisResponse(
        request_id=request_id,
        status=analysis.status,
        region=region_name(),
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