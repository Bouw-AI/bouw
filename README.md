# mcp-client

A Spring Boot AI agent that connects to [Model Context Protocol](https://modelcontextprotocol.io) (MCP) servers and lets an OpenAI-schema LLM use their tools to answer prompts. The LLM backend is pluggable — point it at a local [Ollama](https://ollama.com) instance or a hosted provider such as [OpenRouter](https://openrouter.ai) (see [LLM provider](#llm-provider)).

## Prerequisites

- **Java 21** and **Maven**
- An **OpenAI-compatible LLM endpoint** with a tool-calling model. Either:
  - **[Ollama](https://ollama.com)** running locally (no API key):
    ```bash
    ollama pull llama3.2
    ollama serve            # exposes http://localhost:11434
    ```
  - or a hosted provider like **OpenRouter** (set `OPENROUTER_API_KEY`).
- Runtimes for the MCP servers you configure. The default `mcp-servers.json` uses:
  - `npx` (Node.js) for the filesystem server
  - `uvx` ([uv](https://docs.astral.sh/uv/)) for the time server

## Configure MCP servers

Servers are declared in `mcp-servers.json` (repo root) using the standard Claude Desktop format. This file is **gitignored** so your local server list stays private — create it by copying the committed example:

```bash
cp mcp-servers.example.json mcp-servers.json
```

`mcp-servers.example.json`:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
      "env": {}
    },
    "time": {
      "command": "uvx",
      "args": ["mcp-server-time"],
      "env": {}
    }
  }
}
```

Stdio servers use `command` / `args` / `env`; SSE/HTTP servers use `url` / `headers`. Servers can also be managed at runtime through the `/api/servers` REST API.

### Passing secrets and environment variables

The client launches stdio servers as subprocesses and **does not inherit your shell environment** — only a fixed whitelist (`PATH`, `HOME`, etc.) is passed through. To give a server an API key or other config, put it in that server's `env` block:

```json
"ddg-mcp": {
  "command": "node",
  "args": ["/path/to/ddg-mcp/dist/server.js"],
  "env": {
    "OPENROUTER_API_KEY": "sk-or-v1-..."
  }
}
```

Exporting a variable in your shell (or relying on the server's own `.env` file) will **not** work unless the server explicitly loads it — pass values through the `env` block instead. Because `mcp-servers.json` is gitignored, secrets placed here stay local.

## Build

```bash
mvn clean install
```

## Run

```bash
mvn -pl mcp-integration spring-boot:run
```

The app starts on **port 8080**. `mcp-integration` is the only runnable module; `agent-core` and `mcp-client` are libraries.

## Usage

Send a prompt to the agent:

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What time is it in Tokyo?", "model": "llama3.2"}'
```

`model` is optional and falls back to `llm.model` from configuration. Use a model name valid for the active provider (e.g. `llama3.2` for Ollama, `deepseek/deepseek-chat` for OpenRouter).

Manage MCP servers at runtime:

```bash
curl http://localhost:8080/api/servers                 # list servers + connection status
curl http://localhost:8080/api/servers/time/tools      # list a server's tools
curl -X POST http://localhost:8080/api/servers/time/reconnect
```

## LLM provider

The agent talks to any OpenAI-schema `/chat/completions` endpoint. Providers are declared under `llm.providers` in `application.yml`, and `llm.provider` selects the active one:

```yaml
llm:
  provider: ollama            # switch to "openrouter" to use OpenRouter
  model: qwen-coder-3:latest  # default model for the active provider
  providers:
    ollama:
      base-url: http://localhost:11434/v1   # no api-key → no auth header sent
    openrouter:
      base-url: https://openrouter.ai/api/v1
      api-key: ${OPENROUTER_API_KEY:}        # sent as Authorization: Bearer <key>
```

- **Ollama** has no `api-key`, so requests are sent without an `Authorization` header.
- **OpenRouter** authenticates with an `Authorization: Bearer <api-key>` header ([OpenRouter auth docs](https://openrouter.ai/docs/api/reference/authentication)). Provide the key via the `OPENROUTER_API_KEY` environment variable and set `llm.provider: openrouter` (also pick an OpenRouter `model`, e.g. `deepseek/deepseek-chat`).

To add another OpenAI-compatible provider, add an entry under `llm.providers` with its `base-url` and (optionally) `api-key`, then point `llm.provider` at it.

## Configuration

Settings live in `mcp-integration/src/main/resources/application.yml`:

| Key | Description | Default |
| --- | --- | --- |
| `llm.provider` | Active provider; must match a key under `llm.providers` | `ollama` |
| `llm.model` | Default model when a request omits one (must support tool calling) | `qwen-coder-3:latest` |
| `llm.providers.<name>.base-url` | OpenAI-compatible API root (the part before `/chat/completions`) | _(per provider)_ |
| `llm.providers.<name>.api-key` | Optional; when set, sent as `Authorization: Bearer <key>` | _(blank)_ |
| `mcp.config-file` | Path to the MCP servers JSON (supports `~/`) | `./mcp-servers.json` |
| `agent.api-key` | If set, `/api/agent/**` requires the `X-API-Key` header; if blank, those endpoints are open | _(blank)_ |
| `agent.request-timeout` | Per-request wall-clock budget for the agent loop | `5m` |
