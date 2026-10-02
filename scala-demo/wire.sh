#!/usr/bin/env bash
# MCP 2026-07-28 on the wire, with nothing but curl.
# Start the server first:  ./sbt "runMain mcp2.Main"
# Usage: ./wire.sh [url]        (STEP=1 ./wire.sh pauses between steps)
set -euo pipefail

URL=${1:-http://localhost:8081/mcp}
V=2026-07-28

# Every request carries its version + capabilities in _meta. No initialize, no session.
META=$(jq -nc --arg v "$V" '{
  "io.modelcontextprotocol/protocolVersion": $v,
  "io.modelcontextprotocol/clientInfo": {name: "curl", version: "1.0"},
  "io.modelcontextprotocol/clientCapabilities": {
    elicitation: {},
    extensions: {"io.modelcontextprotocol/tasks": {}}
  }
}')

id=0
# rpc <method> <params-json> [name]   -> prints the JSON-RPC response
rpc() {
  local method=$1 params=$2 name=${3:-}
  id=$((id + 1))
  local body
  body=$(jq -nc --argjson id "$id" --arg m "$method" --argjson p "$params" --argjson meta "$META" \
    '{jsonrpc: "2.0", id: $id, method: $m, params: ($p + {_meta: ($meta + ($p._meta // {}))})}')
  # SEP-2243: method + name are mirrored into headers so gateways can route without parsing JSON
  curl -s "$URL" \
    -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
    -H "MCP-Protocol-Version: $V" -H "Mcp-Method: $method" ${name:+-H "Mcp-Name: $name"} \
    -d "$body"
}

step() {
  echo
  echo "━━━ $* ━━━"
  if [[ -n "${STEP:-}" ]]; then read -rp "" _; fi
}

step "1. server/discover: versions, capabilities, extensions (replaces initialize)"
rpc server/discover '{}' | jq '.result | {supportedVersions, capabilities, serverInfo, resultType, ttlMs, cacheScope}'

step "2. tools/list: every result has resultType; list results are cacheable (ttlMs, cacheScope)"
rpc tools/list '{}' | jq '.result | {tools: [.tools[].name], resultType, ttlMs, cacheScope}'

step "3. tools/call with no handshake and no Mcp-Session-Id"
rpc tools/call '{"name":"add","arguments":{"a":2,"b":3}}' add | jq '.result | {resultType, structuredContent}'

step "4a. MRTR round 1: the server needs input, so it returns input_required (no server->client request)"
round1=$(rpc tools/call '{"name":"book_flight","arguments":{"destination":"Denver"}}' book_flight)
echo "$round1" | jq '.result'

step "4b. MRTR round 2: retry the same call (new id) with inputResponses keyed by the server's ids"
state=$(echo "$round1" | jq -c '.result.requestState // empty')
retry=$(jq -nc --argjson s "${state:-null}" '{
  name: "book_flight", arguments: {destination: "Denver"},
  inputResponses: {confirm: {action: "accept", content: {confirm: true}}}
} + (if $s then {requestState: $s} else {} end)')
rpc tools/call "$retry" book_flight | jq '.result | {resultType, content}'

step "5a. Tasks extension: ask for a task; get a handle back immediately"
created=$(rpc tools/call '{"name":"deep_research","arguments":{"topic":"Stockholm"},"_meta":{"io.modelcontextprotocol/tasks":{}}}' deep_research)
echo "$created" | jq '.result'
task_id=$(echo "$created" | jq -r '.result.task.taskId')

step "5b. Poll tasks/get until terminal"
while :; do
  got=$(rpc tasks/get "$(jq -nc --arg t "$task_id" '{taskId: $t}')")
  status=$(echo "$got" | jq -r '.result.task.status')
  echo "   status: $status"
  [[ "$status" == completed || "$status" == failed || "$status" == cancelled ]] && break
  sleep 1
done
echo "$got" | jq '.result.result'

step "6a. Skills extension: skills/list (frontmatter + manifest with sha256 digests)"
rpc skills/list '{}' | jq '.result.skills[] | {uri, name: .frontmatter.name, files: [.resources[] | {uri, digest}]}'

step "6b. Skill content is just a resource: resources/read"
rpc resources/read '{"uri":"skill://mcp2-demo/flight-booking/SKILL.md"}' skill://mcp2-demo/flight-booking/SKILL.md \
  | jq -r '.result.contents[0].text'

step "7. Version mismatch -> UnsupportedProtocolVersionError (-32022) with the versions we do support"
id=$((id + 1))
curl -s "$URL" -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2099-01-01' -H 'Mcp-Method: tools/list' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":$id,\"method\":\"tools/list\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2099-01-01\"}}}" \
  | jq '.error'
