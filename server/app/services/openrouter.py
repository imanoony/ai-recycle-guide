from __future__ import annotations

import asyncio
import base64
import logging
import time
from dataclasses import dataclass
from typing import Any
from uuid import UUID

import httpx
from pydantic import ValidationError

from app.config import Settings
from app.schemas.analysis import AnalysisStatus
from app.schemas.vision import VISION_JSON_SCHEMA, VisionAnalysis
from app.services.image_input import PreparedImage

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = """당신은 사진 속 폐기물 후보를 식별하는 비전 분석기입니다.
사진에 실제로 보이는 폐기물 품목과 재질만 판단하고, 품목별 확신도를 0에서 1 사이로 반환하세요.
분리배출 방법, 지역별 규칙, 근거 URL이나 출처는 만들지 마세요. 이는 별도 규칙 시스템이 처리합니다.
품목을 확실히 식별할 수 없으면 status를 unidentified로, 후보를 확인해야 하면 needs_clarification으로 설정하고 이유를 짧게 적으세요.
여러 품목이 보이면 서로 구분해 최대 6개까지 반환하세요. 배경의 불확실한 물체는 품목으로 단정하지 마세요.
응답은 지정된 JSON Schema만 따르세요."""

USER_PROMPT = "사진 속 분리배출 대상 품목을 식별해 주세요. 불확실하면 확답하지 마세요."


class VisionProviderUnavailable(RuntimeError):
    def __init__(self, message: str, *, retryable: bool) -> None:
        super().__init__(message)
        self.retryable = retryable
        self.public_message = message


class VisionProviderOutputError(RuntimeError):
    """OpenRouter returned output that does not match the vision result schema."""


@dataclass(frozen=True)
class VisionProviderResult:
    analysis: VisionAnalysis
    usage: dict[str, int]
    actual_model: str


class OpenRouterVisionProvider:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    async def identify(
        self,
        image: PreparedImage,
        *,
        request_id: UUID,
    ) -> VisionProviderResult:
        api_key = self._settings.openrouter_api_key
        if api_key is None or not api_key.get_secret_value().strip():
            logger.error(
                "openrouter_key_missing request_id=%s model=%s",
                request_id,
                self._settings.openrouter_model,
            )
            raise VisionProviderUnavailable(
                "서버의 AI 분석 키가 설정되지 않았습니다.",
                retryable=False,
            )

        image_data_url = (
            f"data:{image.content_type};base64,{base64.b64encode(image.data).decode('ascii')}"
        )
        request_body = {
            "model": self._settings.openrouter_model,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": USER_PROMPT},
                        {
                            "type": "image_url",
                            "image_url": {"url": image_data_url},
                        },
                    ],
                },
            ],
            "max_tokens": 1024,
            "usage": {"include": True},
            "temperature": 0.1,
            "provider": {"require_parameters": True},
            "response_format": {
                "type": "json_schema",
                "json_schema": {
                    "name": "waste_vision_result",
                    "strict": True,
                    "schema": VISION_JSON_SCHEMA,
                },
            },
        }
        endpoint = f"{self._settings.openrouter_base_url.rstrip('/')}/chat/completions"
        timeout = httpx.Timeout(self._settings.openrouter_timeout_seconds)
        started_at = time.monotonic()
        response: httpx.Response | None = None

        async with httpx.AsyncClient(timeout=timeout) as client:
            for attempt in range(self._settings.openrouter_max_retries + 1):
                try:
                    response = await client.post(
                        endpoint,
                        headers={
                            "Authorization": f"Bearer {api_key.get_secret_value()}",
                            "Content-Type": "application/json",
                        },
                        json=request_body,
                    )
                except httpx.RequestError as exc:
                    if attempt < self._settings.openrouter_max_retries:
                        await asyncio.sleep(0.5 * (2**attempt))
                        continue
                    elapsed_ms = int((time.monotonic() - started_at) * 1000)
                    logger.warning(
                        "openrouter_transport_error request_id=%s model=%s elapsed_ms=%d error_type=%s",
                        request_id,
                        self._settings.openrouter_model,
                        elapsed_ms,
                        type(exc).__name__,
                    )
                    raise VisionProviderUnavailable(
                        "AI 분석 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                        retryable=True,
                    ) from exc

                if response.is_success:
                    break

                retryable, delay = _classify_http_error(response)
                if retryable and attempt < self._settings.openrouter_max_retries and delay is not None:
                    await asyncio.sleep(delay)
                    continue

                elapsed_ms = int((time.monotonic() - started_at) * 1000)
                logger.warning(
                    "openrouter_http_error request_id=%s model=%s status=%d elapsed_ms=%d retryable=%s",
                    request_id,
                    self._settings.openrouter_model,
                    response.status_code,
                    elapsed_ms,
                    retryable,
                )
                raise VisionProviderUnavailable(
                    _public_provider_error(response.status_code),
                    retryable=retryable,
                )

        assert response is not None
        elapsed_ms = int((time.monotonic() - started_at) * 1000)
        try:
            payload = response.json()
            analysis = _parse_analysis(payload)
        except (ValueError, ValidationError, KeyError, IndexError, TypeError) as exc:
            logger.warning(
                "openrouter_invalid_output request_id=%s model=%s elapsed_ms=%d",
                request_id,
                self._settings.openrouter_model,
                elapsed_ms,
            )
            raise VisionProviderOutputError from exc

        actual_model = str(payload.get("model") or self._settings.openrouter_model)
        usage = _usage_summary(payload.get("usage"))
        logger.info(
            "openrouter_request_succeeded request_id=%s model=%s elapsed_ms=%d usage=%s",
            request_id,
            actual_model,
            elapsed_ms,
            usage,
        )
        return VisionProviderResult(analysis=analysis, usage=usage, actual_model=actual_model)


def _parse_analysis(payload: Any) -> VisionAnalysis:
    choices = payload["choices"]
    message = choices[0]["message"]
    content = message.get("content")
    if isinstance(content, list):
        content = "".join(
            part.get("text", "") for part in content if isinstance(part, dict)
        )
    if not isinstance(content, str) or not content.strip():
        raise ValueError("The model response did not contain text JSON.")

    analysis = VisionAnalysis.model_validate_json(content)
    if analysis.status == AnalysisStatus.IDENTIFIED and not analysis.items:
        return analysis.model_copy(
            update={
                "status": AnalysisStatus.UNIDENTIFIED,
                "message": analysis.message or "사진에서 품목을 식별하지 못했습니다.",
            }
        )
    if analysis.status == AnalysisStatus.UNIDENTIFIED and analysis.items:
        return analysis.model_copy(
            update={
                "status": AnalysisStatus.NEEDS_CLARIFICATION,
                "message": analysis.message or "식별 후보를 확인해야 합니다.",
            }
        )
    if analysis.status == AnalysisStatus.NEEDS_CLARIFICATION and not analysis.message:
        return analysis.model_copy(
            update={
                "message": "사진 속 품목을 정확히 판단하기 어렵습니다. 더 가까이 촬영해 주세요."
            }
        )
    if analysis.status == AnalysisStatus.UNIDENTIFIED and not analysis.message:
        return analysis.model_copy(update={"message": "사진에서 폐기물 품목을 확인하지 못했습니다."})
    return analysis


def _classify_http_error(response: httpx.Response) -> tuple[bool, float | None]:
    status = response.status_code
    retryable = status in {408, 429, 500, 502, 503, 524, 529}
    if status == 402:
        try:
            error = response.json().get("error", {})
            limit_source = error.get("metadata", {}).get("limit_source")
        except (ValueError, AttributeError):
            limit_source = None
        retryable = limit_source == "openrouter_in_flight_budget"

    if not retryable:
        return False, None

    retry_after = response.headers.get("retry-after")
    if retry_after is not None:
        try:
            seconds = float(retry_after)
        except ValueError:
            seconds = 1.0
        if seconds > 5:
            return True, None
        return True, max(0.0, seconds)
    return True, 0.5


def _public_provider_error(status_code: int) -> str:
    if status_code in {401, 403}:
        return "AI 분석 키 또는 권한 설정을 확인해 주세요."
    if status_code == 402:
        return "AI 분석 사용 한도나 크레딧을 확인해 주세요."
    if status_code == 404:
        return "AI 분석 모델 설정을 확인해 주세요."
    if status_code == 429:
        return "AI 분석 요청이 많습니다. 잠시 후 다시 시도해 주세요."
    if status_code in {408, 500, 502, 503, 524, 529}:
        return "AI 분석 서버를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."
    return "AI 분석 요청을 처리하지 못했습니다. 서버 설정을 확인해 주세요."


def _usage_summary(usage: Any) -> dict[str, int]:
    if not isinstance(usage, dict):
        return {}
    allowed_fields = ("prompt_tokens", "completion_tokens", "total_tokens")
    return {
        field: value
        for field in allowed_fields
        if isinstance((value := usage.get(field)), int)
    }