# scala-demo: zio-http-mcp 0.9.0

A dual-era MCP server: `2025-11-25` (sessions) and `2026-07-28` (stateless) on the same endpoint.

```bash
./sbt "runMain mcp2.Main"     # http://localhost:8081/mcp
```

| Tool | Shows |
|---|---|
| `add` | A plain stateless call |
| `book_flight` | Elicitation: MRTR (`input_required` → retry) for modern clients, SSE for legacy clients |
| `deep_research` | Tasks extension: the server returns a task handle, `ctx.progress` becomes the task's `statusMessage` |
| `plan_trip` | A task that needs input midway: `input_required` in `tasks/get`, answered with `tasks/update` |

Extensions are enabled explicitly in `Main`: the Tasks extension with `McpTasks.inMemory` (swap in
`McpTasks(yourStore)` for an `McpTaskStore` of your own), combined with the skills extension via `++`.

Skills: `src/main/resources/META-INF/skills/mcp2-demo/flight-booking` plus the
`anthropics__skills__brand-guidelines` SkillsJar, served with `McpSkillsJars`.

## Client

`McpClientApp` walks through the protocol with the zio-http-mcp client, one step per feature:
`server/discover`, `tools/list`, a stateless `tools/call`, an MRTR round trip, a task polled by
hand with `tasks/get`, a task that asks for input (followed by `callTool` itself), `tasks/cancel`,
and `skills/list` + `resources/read`.

```bash
./sbt "runMain mcp2.McpClientApp"                             # against the server above
STEP=1 ./sbt "runMain mcp2.McpClientApp"                      # pause between steps
./sbt "runMain mcp2.McpClientApp http://localhost:8080/mcp"   # another server; a legacy one falls back to 2025-11-25
```

Run the server and client in separate sbt sessions, or start the server with `bgRunMain mcp2.Main`
in the same one.
