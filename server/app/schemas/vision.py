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


VISION_JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
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
                "additionalProperties": False,
                "properties": {
                    "name": {"type": "string", "minLength": 1, "maxLength": 100},
                    "material": {"type": ["string", "null"], "maxLength": 100},
                    "confidence": {
                        "type": ["number", "null"],
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
                            "additionalProperties": False,
                            "properties": {
                                "order": {"type": "integer", "minimum": 1},
                                "title": {"type": "string", "minLength": 1},
                                "instruction": {"type": "string", "minLength": 1},
                            },
                            "required": ["order", "title", "instruction"],
                        },
                    },
                    "cautions": {
                        "type": "array",
                        "maxItems": 8,
                        "items": {"type": "string", "maxLength": 400},
                    },
                    "rule_ids": {
                        "type": "array",
                        "maxItems": 6,
                        "items": {"type": "string", "minLength": 1, "maxLength": 100},
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
        "message": {"type": ["string", "null"], "maxLength": 400},
    },
    "required": ["status", "items", "message"],
}
