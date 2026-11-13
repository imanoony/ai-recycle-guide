from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.analysis import AnalysisStatus


class VisionItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: Annotated[str, Field(min_length=1, max_length=100)]
    material: Annotated[str, Field(max_length=100)] | None
    confidence: Annotated[float, Field(ge=0.0, le=1.0)] | None


class VisionAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: AnalysisStatus
    items: Annotated[list[VisionItem], Field(max_length=6)]
    message: Annotated[str, Field(max_length=400)] | None


# Inline strict JSON Schema keeps the OpenRouter request compatible with providers
# that require every object property to be listed as required.
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
                },
                "required": ["name", "material", "confidence"],
            },
        },
        "message": {"type": ["string", "null"], "maxLength": 400},
    },
    "required": ["status", "items", "message"],
}