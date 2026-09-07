# syntax=docker/dockerfile:1
# =============================================================================
#  imagebind-service — P0 PLACEHOLDER (GET /health only).
#  Replaced in P6-T01 by the real in-repo Meta ImageBind wrapper (text/image/
#  audio/video -> float[1024]). CPU-friendly base for the M4 lab.
# =============================================================================
FROM python:3.12-slim
WORKDIR /app
COPY health_server.py .
EXPOSE 8081
CMD ["python", "health_server.py"]
