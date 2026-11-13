from enum import StrEnum
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class ErrorCode(StrEnum):
    INVALID_REQUEST = "invalid_request"
    IMAGE_TOO_LARGE = "image_too_large"
    UNSUPPORTED_IMAGE_TYPE = "unsupported_image_type"
    INVALID_ANALYSIS_OUTPUT = "invalid_analysis_output"
    RATE_LIMITED = "rate_limited"
    ANALYSIS_UNAVAILABLE = "analysis_unavailable"
    INTERNAL_ERROR = "internal_error"


class ErrorDetail(BaseModel):
    code: ErrorCode
    message: Annotated[str, Field(min_length=1)]
    request_id: UUID
    retryable: bool


class ErrorResponse(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "error": {
                        "code": "unsupported_image_type",
                        "message": "지원하지 않는 이미지 형식입니다.",
                        "request_id": "b6ae1511-1bc3-4bc3-ae98-03ac2a0e0320",
                        "retryable": False,
                    }
                }
            ]
        }
    )

    error: ErrorDetail
