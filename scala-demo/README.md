# scala-demo: zio-http-mcp 0.8.3

A dual-era MCP server: `2025-11-25` (sessions) and `2026-07-28` (stateless) on the same endpoint.

```bash
./sbt "runMain mcp2.Main"     # http://localhost:8081/mcp
```

| Tool | Shows |
|---|---|
| `add` | A plain stateless call |
| `book_flight` | Elicitation: MRTR (`input_required` → retry) for modern clients, SSE for legacy clients |
| `deep_research` | Long-running work: progress notifications, or a Tasks-extension task when asked |

Skills: `src/main/resources/META-INF/skills/mcp2-demo/flight-booking` plus the
`anthropics__skills__brand-guidelines` SkillsJar, served with `McpSkillsJars`.

## The protocol with curl

```bash
./wire.sh            # all steps
STEP=1 ./wire.sh     # pause between steps
```

Needs `curl` and `jq`. Steps: `server/discover`, cacheable `tools/list`, `tools/call` without a handshake,
two MRTR rounds, a task with `tasks/get` polling, `skills/list` + `resources/read`, and the `-32022` version error.

## Modern client

```bash
./sbt "runMain mcp2.ClientMain http://localhost:8081/mcp"   # negotiates 2026-07-28, answers MRTR
./sbt "runMain mcp2.ClientMain http://localhost:8080/mcp"   # Spring server: falls back to 2025-11-25
```
