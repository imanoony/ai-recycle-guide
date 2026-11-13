# AI Recycle Guide API

FastAPI backend for identifying waste in uploaded photos and explaining how to sort it.

## Prerequisites

- Python 3.12 or later
- [uv](https://docs.astral.sh/uv/)

## Run locally

From this `server` directory:

```powershell
uv sync
uv run fastapi dev app/main.py
```

Open the interactive API docs at <http://127.0.0.1:8000/docs>.

## Current endpoints

- GET /health - basic liveness check.
- POST /api/v1/analyze - validates an uploaded image, sends it with the Gwanak-gu rules JSON to Gemini, and returns identification and disposal guidance in the existing API response format.

## Gemini API configuration

Copy .env.example to .env and set GEMINI_API_KEY from Google AI Studio. The default model is gemini-3.8-flash; base URL, timeout, and bounded retries can also be configured. Restart the server after changing .env. Keep the real key only in the ignored .env file.

Google states that content submitted on the free tier may be used to improve its products. Avoid sending private photos through the free tier.

## Dependency management

- Add a dependency: `uv add <package>`
- Add a development dependency: `uv add --dev <package>`
- Refresh the environment from the lock file: `uv sync`