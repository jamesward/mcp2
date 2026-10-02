#!/usr/bin/env bash
# Headless run of the CIMD browser flow (what you'd click through in a browser).
# Requires: secure-server on :8090 and cimd-client on :8085 running.
# Uses the demo user on login.jamesward.dev (demo / pw).
set -euo pipefail

jar=$(mktemp -d)
trap 'rm -rf "$jar"' EXIT
H='Accept: text/html'

echo "1. GET the client app -> it calls the MCP server -> 401 -> redirect to the AS"
authorize=$(curl -s -i -c "$jar/app" -b "$jar/app" http://localhost:8085/ | grep -i '^location' | cut -d' ' -f2 | tr -d '\r')
echo "   $authorize" | sed 's/&/\n     \&/g'

echo "2. The AS fetches the client_id URL (the CIMD document):"
client_id=$(echo "$authorize" | sed 's/.*client_id=\([^&]*\).*/\1/')
curl -s "$client_id" | sed 's/^/   /'
echo

echo "3. Log in as demo/pw"
curl -s -o /dev/null -H "$H" -c "$jar/as" -b "$jar/as" "$authorize"
csrf=$(curl -s -H "$H" -c "$jar/as" -b "$jar/as" https://login.jamesward.dev/login | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"//')
resume=$(curl -s -i -H "$H" -c "$jar/as" -b "$jar/as" -d "username=demo&password=pw&_csrf=$csrf" https://login.jamesward.dev/login | grep -i '^location' | cut -d' ' -f2 | tr -d '\r')
callback=$(curl -s -i -H "$H" -c "$jar/as" -b "$jar/as" "$resume" | grep -i '^location' | cut -d' ' -f2 | tr -d '\r')
echo "   AS redirects back to: ${callback%%\?*}?code=..."

echo "4. Callback: client exchanges code (+ PKCE verifier + resource) for a token, then retries"
curl -s -o /dev/null -c "$jar/app" -b "$jar/app" "$callback"
curl -s -c "$jar/app" -b "$jar/app" http://localhost:8085/ | grep -o '<strong>.*</strong>' | sed 's/<[^>]*>//g; s/^/   whoami -> /'
