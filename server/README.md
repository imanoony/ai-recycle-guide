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

- `GET /health` — basic liveness check.
- `POST /api/v1/analyze` — validates the upload and identifies visible items with Gemma 4 31B through OpenRouter. Sorting steps and sources remain `unknown`/empty until the regional rules service is connected.

## OpenRouter configuration

Copy `.env.example` to `.env` and set `OPENROUTER_API_KEY`. The defaults use `google/gemma-4-31b-it:free`, `https://openrouter.ai/api/v1`, a 60-second request timeout, and one bounded retry. The real `.env` file is ignored by Git.

## Dependency management

- Add a dependency: `uv add <package>`
- Add a development dependency: `uv add --dev <package>`
- Refresh the environment from the lock file: `uv sync`