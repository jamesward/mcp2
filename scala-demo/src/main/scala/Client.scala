package mcp2

import com.jamesward.ziohttp.mcp.*
import com.jamesward.ziohttp.mcp.client.*
import zio.*
import zio.http.*
import zio.json.ast.Json

/**
 * A 2026-07-28 client. Run against the zio server (modern) or the Spring server (legacy):
 *   ./sbt "runMain mcp2.ClientMain http://localhost:8081/mcp"
 *   ./sbt "runMain mcp2.ClientMain http://localhost:8080/mcp"
 */
object ClientMain extends ZIOAppDefault:

  /** MRTR: the server says input_required, we answer each inputRequest by its key. */
  private val onInputRequest: InputRequest => IO[McpClientError, Json] = request =>
    Console.printLine(s"  <- server needs input [${request.id}] ${request.method}: ${request.params.get("message").flatMap(_.asString).getOrElse("")}").orDie *>
      Console.printLine("  -> answering: accept, confirm=true").orDie.as:
        Json.Obj(
          "action"  -> Json.Str("accept"),
          "content" -> Json.Obj("confirm" -> Json.Bool(true)),
        )

  private def text(r: CallToolResult): String =
    r.content.collect { case ToolContent.Text(t, _) => t }.mkString

  def run =
    for
      args   <- getArgs
      url     = args.headOption.getOrElse("http://localhost:8081/mcp")
      _      <- ZIO.scoped:
                  for
                    // probes server/discover; falls back to initialize for a legacy server
                    client <- McpClient.connect(McpClientConfig(url, onInputRequest = Some(onInputRequest)))
                    _      <- Console.printLine(s"connected to ${client.serverInfo.name}, negotiated ${client.protocolVersion}")
                    tools  <- client.listTools
                    _      <- Console.printLine(s"tools: ${tools.map(_.name.value).mkString(", ")}")
                    names   = tools.map(_.name.value).toSet
                    _      <- ZIO.when(names("add"))(
                                client.callTool("add", Json.Obj("a" -> Json.Num(2), "b" -> Json.Num(3)))
                                  .flatMap(r => Console.printLine(s"add(2,3) = ${text(r)}")))
                    _      <- ZIO.when(names("book_flight"))(
                                Console.printLine("book_flight(Denver):") *>
                                client.callTool("book_flight", Json.Obj("destination" -> Json.Str("Denver")))
                                  .flatMap(r => Console.printLine(s"  = ${text(r)}")))
                  yield ()
    yield ()
  .provideSome[ZIOAppArgs](Client.default)
