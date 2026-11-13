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

## Initial endpoint

- `GET /health` — basic liveness check.

The image upload and AI analysis endpoint will be added in a later step.

## Dependency management

- Add a dependency: `uv add <package>`
- Add a development dependency: `uv add --dev <package>`
- Refresh the environment from the lock file: `uv sync`
