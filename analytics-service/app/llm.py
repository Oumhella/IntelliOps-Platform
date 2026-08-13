import json

from openai import AsyncOpenAI

from app.config import Settings
from app.semantic import CATALOG, QueryPlan

SCHEMA = {
    "name": "analytics_query_plan",
    "strict": True,
    "schema": {
        "type": "object",
        "properties": {
            "metric": {"type": "string"},
            "sql": {"type": "string"},
            "visualization": {
                "type": "string",
                "enum": ["none", "single_value", "table", "bar", "line"],
            },
            "assumptions": {"type": "array", "items": {"type": "string"}},
        },
        "required": ["metric", "sql", "visualization", "assumptions"],
        "additionalProperties": False,
    },
}


async def generate_plan(question: str, settings: Settings) -> QueryPlan:
    if not settings.llm_api_key:
        raise ValueError("This question is outside the currently supported analytics catalogue")
    client = AsyncOpenAI(api_key=settings.llm_api_key, base_url=settings.llm_base_url)
    completion = await client.chat.completions.create(
        model=settings.llm_model,
        temperature=0,
        messages=[
            {
                "role": "system",
                "content": "Generate one bounded read-only PostgreSQL query. " + CATALOG,
            },
            {"role": "user", "content": question},
        ],
        response_format={"type": "json_schema", "json_schema": SCHEMA},
    )
    content = completion.choices[0].message.content
    if not content:
        raise ValueError("The model did not produce a query plan")
    data = json.loads(content)
    return QueryPlan(data["metric"], data["sql"], {}, data["visualization"], data["assumptions"])
