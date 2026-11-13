# API Contract: Waste Photo Analysis

Version: `v1`  
Status: Initial contract for Android/server implementation

## Endpoint

`POST /api/v1/analyze`

Accepts one photo and returns identified waste items with step-by-step sorting guidance. The backend will analyze the image through OpenRouter and Gemma 4 31B, then attach sorting guidance and sources when supported by the configured regional rules.

## Request

Content type: `multipart/form-data`

| Field | Type | Required | Meaning |
| --- | --- | --- | --- |
| `image` | file | Yes | One waste photo. JPEG, PNG, and WebP are accepted based on decoded image contents (not the MIME header). Maximum file size is 10 MiB and maximum image size is 24,000,000 pixels. Valid images are orientation-corrected, resized to a maximum side of 4,096 pixels, and normalized to JPEG in memory. |
| `region` | string | No | Human-readable region hint, such as `Seoul, Mapo-gu`. If absent or insufficient to select a local rule, the result must say that local confirmation is needed. |

The server does not retain the original upload or normalized image after processing. The multipart parser may spool uploads to a temporary file; the upload handle is closed after validation, and the normalized image is held only for the request.

## Successful response (`200`)

```json
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
        { "order": 1, "title": "내용물 비우기", "instruction": "병 안의 내용물을 비워 주세요." },
        { "order": 2, "title": "라벨 분리", "instruction": "비닐 라벨을 떼어 주세요." },
        { "order": 3, "title": "배출하기", "instruction": "투명 페트병 전용 수거함에 배출하세요." }
      ],
      "cautions": [],
      "sources": [
        {
          "title": "분리배출 안내",
          "url": "https://example.org/recycling",
          "region": "Seoul, Mapo-gu",
          "checked_at": "2026-11-14"
        }
      ]
    }
  ],
  "message": null
}
```

The sample source URL uses the reserved `example.org` domain and is illustrative only; it is not a real recycling-rule source.

## Response fields

- `request_id`: UUID for support and log correlation; it must not contain user or image data.
- `status`: `identified`, `needs_clarification`, or `unidentified`.
- `region`: normalized or echoed region hint when known, otherwise `null`.
- `items`: zero or more detected items. Multiple objects in one photo can be represented independently.
- `name`: user-facing item name.
- `material`: recognized material when known; otherwise `null`.
- `confidence`: model confidence from `0.0` to `1.0`; `null` when not available. It is not a guarantee that the sorting rule is correct.
- `sorting_status`: `recyclable`, `general_waste`, `local_confirmation_required`, or `unknown`.
- `steps`: ordered actions. Each `order` starts at 1 and is unique within that item.
- `cautions`: additional warnings for that item.
- `sources`: supporting rule sources. Each source has a title, URL, applicable region when known, and the date it was checked; `checked_at` may be `null` if unavailable.
- `message`: optional user-facing explanation, especially when clarification is needed or no item can be identified.

`needs_clarification` and `unidentified` responses may have an empty `items` array. The API must not invent a local rule or source when none is available. A `local_confirmation_required` item may include general preparation steps, but must clearly state that the final disposal route needs local confirmation.

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
| `503` | `analysis_unavailable` | Usually yes | OpenRouter/model is unavailable, rate limited, or out of service. |
| `500` | `internal_error` | No | Unexpected server error; response must not expose internals. |

FastAPI validation errors must be translated to this envelope rather than returned in the framework's default shape. Error messages are safe for clients and must not include secrets, provider response bodies, prompts, or image data.

## Open questions for implementation

- Upload validation enforces JPEG/PNG/WebP by actual decoding, a 10 MiB byte limit, and a 24,000,000-pixel limit. Valid input is orientation-corrected and normalized to a JPEG with a maximum 4,096-pixel side.
- Region is deliberately a human-readable hint for v1; a canonical region-code system can be added in a later API version.
- Exact OpenRouter model ID and image input capability must be checked before the provider adapter is implemented.



