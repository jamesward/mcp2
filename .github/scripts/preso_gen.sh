#!/bin/sh
# Builds the Marp deck in preso into _site/ (HTML + PDF) for GitHub Pages.
set -eu

site="$(pwd)/_site"
mkdir -p "$site"
cd preso

echo "Building mcp2.md"
npx marp mcp2.md --output "$site/mcp2.html"
npx marp mcp2.md --pdf --allow-local-files --output "$site/mcp2.pdf"

# The root URL opens the deck.
cat > "$site/index.html" <<'HTML'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>MCP 2.0: Stateless, New Auth (CIMD), and Exciting Extensions</title>
  <meta http-equiv="refresh" content="0; url=mcp2.html">
</head>
<body>
  <p><a href="mcp2.html">MCP 2.0: Stateless, New Auth (CIMD), and Exciting Extensions</a> (<a href="mcp2.pdf">PDF</a>)</p>
</body>
</html>
HTML
