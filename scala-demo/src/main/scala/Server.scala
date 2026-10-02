package mcp2

import com.jamesward.ziohttp.mcp.*
import zio.*
import zio.http.*
import zio.json.ast.Json
import zio.schema.*

case class AddInput(a: Int, b: Int) derives Schema
case class FlightInput(destination: String) derives Schema
case class ResearchInput(topic: String) derives Schema

object Tools:

  val add = McpTool("add")
    .description("Adds two numbers")
    .handle: (in: AddInput) =>
      ZIO.succeed(in.a + in.b)

  private val confirmSchema = Json.Obj(
    "type"       -> Json.Str("object"),
    "properties" -> Json.Obj("confirm" -> Json.Obj("type" -> Json.Str("boolean"))),
    "required"   -> Json.Arr(Json.Str("confirm")),
  )

  /**
   * Elicitation, the 2026-07-28 way: MRTR (Multi Round-Trip Request).
   * The handler just asks. On a modern request the server answers
   *   resultType: "input_required" + inputRequests{ "confirm": elicitation/create }
   * and the client retries tools/call with inputResponses. No session, no SSE back-channel.
   * On a legacy (2025-11-25) request the same code sends elicitation/create over SSE.
   */
  val bookFlight = McpTool("book_flight")
    .description("Books a flight to a destination after the user confirms")
    .handleWithContext[Any, ToolError, FlightInput, String]: (in, ctx) =>
      ctx.elicit("confirm", s"Book a flight to ${in.destination} for $$420?", confirmSchema).map: answer =>
        val confirmed = answer.action == "accept" &&
          answer.content.flatMap(_.get("confirm")).contains(Json.Bool(true))
        if confirmed then s"Booked: flight to ${in.destination}, confirmation MCP-2026"
        else s"Not booked (${answer.action})"

  /**
   * Long-running work. Called normally it just takes a while.
   * Called with the Tasks extension marker in _meta, the server returns a task handle
   * (resultType: "task") right away and the client polls tasks/get.
   */
  val deepResearch = McpTool("deep_research")
    .description("Researches a travel topic. Slow: prefer calling this as a task.")
    .handleWithContext[Any, ToolError, ResearchInput, String]: (in, ctx) =>
      ZIO.foreachDiscard(1 to 5): step =>
        ctx.progress(step, 5, Some(s"researching ${in.topic} ($step/5)")) *> ZIO.sleep(1.second)
      .as(s"Research on ${in.topic}: go in spring, book 6 weeks out, the train beats a taxi.")

object Main extends ZIOAppDefault:

  def run =
    val port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8081)
    for
      skills <- McpSkillsJars.load // META-INF/skills/** from our resources + SkillsJars deps
      _      <- ZIO.foreachDiscard(skills.entries)(e => Console.printLine(s"skill: ${e.uri.value}"))
      server  = McpServer("mcp2-zio-server", "1.0.0")
                  .instructions("Travel helper. Read the flight-booking skill before booking.")
                  .tool(Tools.add)
                  .tool(Tools.bookFlight)
                  .tool(Tools.deepResearch)
                  .withExtensions(skills.extensions) // io.modelcontextprotocol/skills
                  .resourceSource(skills.resources)
      _      <- Console.printLine(s"MCP server (2025-11-25 + 2026-07-28) on http://localhost:$port/mcp")
      // server.routes is dual-era: initialize -> legacy session, _meta.protocolVersion -> stateless
      _      <- Server.serve(server.routes).provide(
                  Server.defaultWith(_.binding("0.0.0.0", port)),
                  McpServer.State.default,
                )
    yield ()
