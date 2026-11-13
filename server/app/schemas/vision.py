from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.analysis import AnalysisStatus, SortingStatus, SortingStep


class VisionItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: Annotated[str, Field(min_length=1, max_length=100)]
    material: Annotated[str, Field(max_length=100)] | None
    confidence: Annotated[float, Field(ge=0.0, le=1.0)] | None
    sorting_status: SortingStatus
    steps: Annotated[list[SortingStep], Field(max_length=8)]
    cautions: Annotated[list[str], Field(max_length=8)]
    rule_ids: Annotated[list[Annotated[str, Field(min_length=1, max_length=100)]], Field(max_length=6)]


class VisionAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: AnalysisStatus
    items: Annotated[list[VisionItem], Field(max_length=6)]
    message: Annotated[str, Field(max_length=400)] | None


GEMINI_RESPONSE_JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "status": {
            "type": "string",
            "enum": ["identified", "needs_clarification", "unidentified"],
        },
        "items": {
            "type": "array",
            "maxItems": 6,
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "material": {
                        "anyOf": [{"type": "string"}, {"type": "null"}]
                    },
                    "confidence": {
                        "anyOf": [{"type": "number"}, {"type": "null"}],
                        "minimum": 0.0,
                        "maximum": 1.0,
                    },
                    "sorting_status": {
                        "type": "string",
                        "enum": [
                            "recyclable",
                            "general_waste",
                            "food_waste",
                            "local_confirmation_required",
                            "unknown",
                        ],
                    },
                    "steps": {
                        "type": "array",
                        "maxItems": 8,
                        "items": {
                            "type": "object",
                            "properties": {
                                "order": {"type": "integer", "minimum": 1},
                                "title": {"type": "string"},
                                "instruction": {"type": "string"},
                            },
                            "required": ["order", "title", "instruction"],
                        },
                    },
                    "cautions": {
                        "type": "array",
                        "maxItems": 8,
                        "items": {"type": "string"},
                    },
                    "rule_ids": {
                        "type": "array",
                        "maxItems": 6,
                        "items": {"type": "string"},
                    },
                },
                "required": [
                    "name",
                    "material",
                    "confidence",
                    "sorting_status",
                    "steps",
                    "cautions",
                    "rule_ids",
                ],
            },
        },
        "message": {
            "anyOf": [{"type": "string"}, {"type": "null"}]
        },
    },
    "required": ["status", "items", "message"],
}