# ERP Operations MCP Server

This service is the controlled operational gateway for the ERP's future AI agent and BI conversation layer. It provides useful, current workflow capabilities now without granting an LLM direct write access.

## Current value

- Read product catalog and inventory before deciding to replenish, reserve, or investigate a discrepancy.
- Read a lead and an agent's lead workload before creating an order.
- Prepare either a stock adjustment or a lead-to-order conversion with an exact impact summary and calculated total.

## Mandatory two-step mutation flow

1. Call `preparerAjustementStock` or `preparerConversionLeadEnCommande`. It returns an approval token and **does not change data**.
2. Show the summary to a human. Only after their explicit approval, call the matching `confirmer...` tool with that token and `confirmation = CONFIRM`.

Tokens expire after five minutes by default, are one-time, and are bound to their operation. The future agent API deliberately advertises only read-only capabilities; it is not wired to automatically invoke write confirmations.

## Gateway and OpenAPI boundary

The MCP server is internal-only. MCP clients connect through the gateway's
authenticated SSE endpoints: `GET /sse` and `POST /mcp/message/**`. Every
connection and message must carry the caller's JWT. Tool calls are then sent
back through the gateway, which injects the validated user and enterprise
headers for the downstream service.

The gateway's Swagger UI remains the source of endpoint documentation. Do not
turn every OpenAPI operation directly into an MCP tool: generated write tools
would bypass the mandatory preview/confirmation flow. Add a new operation only
after it has been classified as read-only or wrapped in a dedicated preview and
confirmation tool. The former `McpOpenApiConfig` used a legacy function-callback
API and was not registered with the MCP server, so it intentionally has been
removed rather than giving a false impression that Swagger endpoints were
available as MCP tools.

The generic OpenAPI MCP tools cover the documented gateway operations without
requiring a Java method per controller:

1. `listerOperationsOpenApi` returns the current `service`, `operationId`,
   HTTP method, path, and read-only classification from each Swagger document.
2. `executerLectureOpenApi` executes any listed `GET` operation.
3. `preparerMutationOpenApi` previews every listed `POST`, `PUT`, `PATCH`, or
   `DELETE` operation; `confirmerMutationOpenApi` sends that exact request only
   after a separate `CONFIRM` call.

Authentication/bootstrap operations (`login`, `register`, and `setup-admin`)
are deliberately excluded. All requests remain subject to gateway JWT
validation and downstream role/enterprise authorization.

## NVIDIA NIM readiness

NVIDIA's NIM API is OpenAI-compatible. Supply secrets through environment variables (or Vault), never a properties file:

```text
NVIDIA_API_KEY=...
NVIDIA_MODEL=meta/llama-3.1-70b-instruct
AGENT_LLM_PROVIDER=openai
# Optional only if you use a proxy; do not include /v1 here.
NVIDIA_BASE_URL=https://integrate.api.nvidia.com
```

`GET /api/v1/agent/status` confirms whether the key is present without returning it. `GET /actuator/health` is available for orchestration.

## Read-only chat API

With `AGENT_LLM_PROVIDER=openai` and `NVIDIA_API_KEY` configured, call `POST /api/v1/agent/chat` through the gateway with a JWT:

```json
{"message":"Show inventory for product 12 in store 3"}
```

The endpoint exposes only the four read-only inventory and lead tools to the model. It cannot prepare, confirm, or execute a mutation.
