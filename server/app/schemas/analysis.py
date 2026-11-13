from datetime import date
from enum import StrEnum
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, HttpUrl


class AnalysisStatus(StrEnum):
    IDENTIFIED = "identified"
    NEEDS_CLARIFICATION = "needs_clarification"
    UNIDENTIFIED = "unidentified"


class SortingStatus(StrEnum):
    RECYCLABLE = "recyclable"
    GENERAL_WASTE = "general_waste"
    LOCAL_CONFIRMATION_REQUIRED = "local_confirmation_required"
    UNKNOWN = "unknown"


class SortingStep(BaseModel):
    order: Annotated[int, Field(ge=1)]
    title: Annotated[str, Field(min_length=1)]
    instruction: Annotated[str, Field(min_length=1)]


class GuidanceSource(BaseModel):
    title: Annotated[str, Field(min_length=1)]
    url: HttpUrl
    region: str | None = None
    checked_at: date | None = None


class WasteItemResult(BaseModel):
    name: Annotated[str, Field(min_length=1)]
    material: str | None = None
    confidence: Annotated[float, Field(ge=0.0, le=1.0)] | None = None
    sorting_status: SortingStatus
    steps: list[SortingStep] = Field(default_factory=list)
    cautions: list[str] = Field(default_factory=list)
    sources: list[GuidanceSource] = Field(default_factory=list)


class AnalysisResponse(BaseModel):
    model_config = ConfigDict(
        json_schema_extra={
            "examples": [
                {
                    "request_id": "b6ae1511-1bc3-4bc3-ae98-03ac2a0e0320",
                    "status": "identified",
                    "region": "Seoul, Mapo-gu",
                    "items": [
                        {
                            "name": "투명 페트병",
                            "material": "PET",
                            "confidence": 0.94,
                            "sorting_status": "recyclable",
                            "steps": [
                                {"order": 1, "title": "내용물 비우기", "instruction": "병 안의 내용물을 비워 주세요."},
                                {"order": 2, "title": "라벨 분리", "instruction": "비닐 라벨을 떼어 주세요."},
                                {"order": 3, "title": "배출하기", "instruction": "투명 페트병 전용 수거함에 배출하세요."},
                            ],
                            "cautions": [],
                            "sources": [
                                {
                                    "title": "분리배출 안내",
                                    "url": "https://example.org/recycling",
                                    "region": "Seoul, Mapo-gu",
                                    "checked_at": "2026-11-14",
                                }
                            ],
                        }
                    ],
                    "message": None,
                }
            ]
        }
    )

    request_id: UUID
    status: AnalysisStatus
    region: str | None = None
    items: list[WasteItemResult] = Field(default_factory=list)
    message: str | None = None
