# AI Recycle Guide API

FastAPI backend starter for the AI Recycle Guide service.

## Run locally

From this `server` directory, create and activate a virtual environment, then install dependencies:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
fastapi dev app/main.py
```

Open the interactive API docs at <http://127.0.0.1:8000/docs>.

## Initial endpoint

- `GET /health` — basic liveness check.

The image upload and AI analysis endpoint will be added in a later step.
