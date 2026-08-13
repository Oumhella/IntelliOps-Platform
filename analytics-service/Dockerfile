FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY pyproject.toml ./
COPY app ./app
COPY migrations ./migrations
COPY README.md ./
RUN pip install --no-cache-dir .

RUN useradd --system --uid 10001 analytics
USER analytics

EXPOSE 8091
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091"]
