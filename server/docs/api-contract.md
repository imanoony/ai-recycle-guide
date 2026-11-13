# API Contract: Waste Photo Analysis

Version: `v1`  
Status: Initial contract for Android/server implementation

## Endpoint

`POST /api/v1/analyze`

Accepts one photo and sends it together with the complete Gwanak-gu rules JSON to Gemini API and Gemini 3.8 Flash. The model identifies visible waste items and creates step-by-step disposal guidance from those rules. The server validates the model-selected rule IDs and attaches source metadata from the JSON. Gwanak-gu rules are always applied in this demo.

## Request

Content type: `multipart/form-data`

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `image` | file | Yes | One waste photo. JPEG, PNG, and WebP are accepted based on decoded image contents (not the MIME header). Maximum file size is 10 MiB and maximum image size is 24,000,000 pixels. Valid images are orientation-corrected, resized to a maximum side of 4,096 pixels, and normalized to JPEG in memory. |
| `region` | string | No | Retained for API compatibility. The value does not select rules; this demo always applies and returns `서울특별시 관악구`. |

The server does not retain the original upload or normalized image after processing. The multipart parser may spool uploads to a temporary file; the upload handle is closed after validation, and the normalized image is held only for the request.

## Successful response (`200`)

```json
{
  "request_id": "b6ae1511-1bc3-4bc3-ae98-03ac2a0e0320",
  "status": "identified",
  "region": "서울특별시 관악구",
  "items": [
    {
      "name": "투명 페트병",
      "material": "PET",
      "confidence": 0.94,
      "sorting_status": "recyclable",
      "steps": [
        { "order": 1, "title": "내용물 비우기", "instruction": "병 안의 내용물을 비워 주세요." },
        { "order": 2, "title": "라벨 분리", "instruction": "비닐 라벨을 떼어 주세요." },
        { "order": 3, "title": "배출하기", "instruction": "투명 페트병 전용 수거함에 배출하세요." }
      ],
      "cautions": [],
      "sources": [
        {
          "title": "분리배출 안내",
          "url": "https://www.gwanak.go.kr/site/gwanak/07/10703010200002016051206.jsp",
          "region": "서울특별시 관악구",
          "checked_at": "2026-11-14"
        }
      ]
    }
  ],
  "message": null
}
```

The sample source points to the official Gwanak-gu recycling guidance page.

## Response fields

- `request_id`: UUID for support and log correlation; it must not contain user or image data.
- `status`: `identified`, `needs_clarification`, or `unidentified`.
- `region`: always `서울특별시 관악구` in this demo; the optional request `region` field is ignored.
- `items`: zero or more detected items. Multiple objects in one photo can be represented independently.
- `name`: user-facing item name.
- `material`: recognized material when known; otherwise `null`.
- `confidence`: model confidence from `0.0` to `1.0`; `null` when not available. It is not a guarantee that the sorting rule is correct.
- `sorting_status`: `recyclable`, `general_waste`, `food_waste`, `local_confirmation_required`, or `unknown`.
- `steps`: ordered actions. Each `order` starts at 1 and is unique within that item.
- `cautions`: additional warnings for that item.
- `sources`: supporting rule sources. Each source has a title, URL, applicable region when known, and the date it was checked; `checked_at` may be `null` if unavailable.
- `message`: optional user-facing explanation, especially when clarification is needed or no item can be identified.

`needs_clarification` and `unidentified` responses may have an empty `items` array. The API must not invent a local rule or source when none is available. The Gemini model receives the complete Gwanak-gu JSON with the photo; the server only accepts guidance when the returned rule IDs exist and their sorting status agrees with the selected rules. A `local_confirmation_required` item has no unsupported steps or cautions and clearly states that the final disposal route needs local confirmation.

## Errors

All API errors use this envelope:

```json
{
  "error": {
    "code": "unsupported_image_type",
    "message": "지원하지 않는 이미지 형식입니다.",
    "request_id": "b6ae1511-1bc3-4bc3-ae98-03ac2a0e0320",
    "retryable": false
  }
}
```

| HTTP status | Error code | Retryable | Meaning |
| --- | --- | --- | --- |
| `400` | `invalid_request` | No | Missing or malformed request data. |
| `413` | `image_too_large` | No | Upload exceeds 10 MiB or 24,000,000 decoded pixels. |
| `415` | `unsupported_image_type` | No | File is not a supported, decodable image. |
| `422` | `invalid_analysis_output` | No | Model output cannot be validated against the server schema. |
| `429` | `rate_limited` | Yes | Gemini API rate limit reached; retry after the limit window. No immediate retry is made when Retry-After is absent. |
| `503` | `analysis_unavailable` | Usually yes | Gemini API/model is unavailable or out of service. |
| `500` | `internal_error` | No | Unexpected server error; response must not expose internals. |

FastAPI validation errors must be translated to this envelope rather than returned in the framework's default shape. Error messages are safe for clients and must not include secrets, provider response bodies, prompts, or image data.

## Open questions for implementation

- Upload validation enforces JPEG/PNG/WebP by actual decoding, a 10 MiB byte limit, and a 24,000,000-pixel limit. Valid input is orientation-corrected and normalized to a JPEG with a maximum 4,096-pixel side.
- Region is deliberately a human-readable hint for v1; a canonical region-code system can be added in a later API version.
- Confirm the selected model and image input against the current Gemini API catalog before runtime calls; the implementation defaults to `gemini-3.8-flash`.



