---
name: flight-booking
description: How to book a flight with this server's tools. Use when the user wants to travel somewhere.
license: MIT
---

# Flight booking

1. Call `book_flight` with the destination city.
2. The server will ask the user to confirm (an elicitation). Never confirm on the user's behalf.
3. For trip research, call `deep_research`. It is long-running and may come back as a task.

See [the airport codes](references/airports.md) for the destinations we serve.
