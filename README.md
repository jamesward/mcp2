# MCP 2.0: Stateless, New Auth (CIMD), and Exciting Extensions

Slides and demos for the MCP `2026-07-28` spec.

- `preso/` has the MARP slides (`mcp2.md`)
- `java-demo/` has the Spring AI `2.1.0-M1` demos (stateless server, CIMD auth, interop client)
- `scala-demo/` has the `zio-http-mcp` `0.8.3` demos (`2026-07-28` on the wire: discover, MRTR, Tasks, Skills)

## Ports

| App | Port |
|---|---|
| `java-demo/server` (stateless Spring AI) | 8080 |
| `scala-demo` (dual-era zio-http-mcp) | 8081 |
| `java-demo/cimd-client` (fixed: cimd.now encodes it) | 8085 |
| `java-demo/secure-server` (OAuth resource server) | 8090 |
| slides (`npm run serve`) | 8070 |

## Slides

Published on every push to `main` that touches `preso/`: https://jamesward.github.io/mcp2/ ([PDF](https://jamesward.github.io/mcp2/mcp2.pdf))

```bash
cd preso
npm install
npm run serve      # live preview at http://localhost:8070/mcp2.md
npm run pdf        # dist/mcp2.pdf
```

## Demo order

1. Stateless Spring AI server: `java-demo/README.md`
2. Raw `2026-07-28` with curl: `scala-demo/wire.sh`
3. Interop: Spring AI client and zio client against both servers
4. CIMD: `java-demo` secure-server + cimd-client
5. MCP Apps: live in claude.ai (not in this repo)
