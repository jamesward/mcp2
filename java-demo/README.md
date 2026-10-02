# java-demo: Spring AI 2.1.0-M1

Spring Boot `4.2.0-M2`, Spring AI `2.1.0-M1` (MCP Java SDK `2.0.0`), Java 25.

The Java SDK `2.0.0` implements protocol `2025-11-25`. The `2026-07-28` work (discover, MRTR, `_meta`) is on the
SDK `2.2` milestone. These demos use the parts of the new model that work today: stateless transport, explicit
handles, and CIMD auth.

| Module | Port | What it shows |
|---|---|---|
| `server` | 8080 | `protocol=stateless`, explicit handles (`create_shopping_list` → `listId`) |
| `secure-server` | 8090 | OAuth 2.1 resource server (`mcp-server-security`), audience validation |
| `cimd-client` | 8085 | Web app MCP client that authenticates with a Client ID Metadata Document |
| `client` | - | Spring AI MCP client calling the Spring server and the zio dual-era server |

## Stateless server

```bash
./gradlew :server:bootRun
./gradlew :server:test     # no Mcp-Session-Id, tools/call without initialize, GET 405, handles across clients
npx @modelcontextprotocol/inspector   # Streamable HTTP, http://localhost:8080/mcp
```

## CIMD

The client's `client_id` is `https://www.cimd.now/8085/authorize/oauth2/code/secure`. [cimd.now](https://www.cimd.now)
serves a metadata document at that URL with `redirect_uris: [http://localhost:8085/authorize/oauth2/code/secure]`.
The authorization server, [login.jamesward.dev](https://login.jamesward.dev), advertises
`client_id_metadata_document_supported: true` and fetches the document instead of requiring registration.

```bash
./gradlew :secure-server:bootRun
./gradlew :cimd-client:bootRun
open http://localhost:8085      # log in as demo / pw
./cimd-flow.sh                  # the same flow with curl, printing each step
```

Expected: `whoami → user=demo aud=[http://localhost:8090/mcp]`

`./gradlew :secure-server:test` checks the 401 challenge, the PRM document, a live audience-bound token, and
the rejection of a token minted for a different audience. These tests need network access to `login.jamesward.dev`.

Note: `McpServerOAuth2Configurer.validateAudienceClaim` defaults to `false`. The demo turns it on.

## Interop client

Start `:server` and the zio server (`cd ../scala-demo && ./sbt "runMain mcp2.Main"`), then:

```bash
./gradlew :client:bootRun
```

The client negotiates `2025-11-25` with both servers. On the zio server, `book_flight` elicits with
`elicitation/create` over SSE, the same handler that uses MRTR for `2026-07-28` clients.
