package mcp2

import com.jamesward.ziohttp.mcp.*
import com.jamesward.ziohttp.mcp.client.*
import zio.*
import zio.http.*
import zio.json.ast.Json

/**
 * MCP 2026-07-28 from the client side, one step per feature, using the
 * zio-http-mcp client. Start the server first:  ./sbt "runMain mcp2.Main"
 *
 *   ./sbt "runMain mcp2.McpClientApp"                              # http://localhost:8081/mcp
 *   ./sbt "runMain mcp2.McpClientApp http://localhost:8080/mcp"    # e.g. a legacy server: falls back to 2025-11-25
 *   STEP=1 ./sbt "runMain mcp2.McpClientApp"                       # pause between steps
 */
object McpClientApp extends ZIOAppDefault:

  /** The user, standing in for a UI: accept every elicitation with confirm=true. */
  private val onInputRequest: InputRequest => IO[McpClientError, Json] = request =>
    val message = request.params.get("message").flatMap(_.asString).getOrElse("")
    Console.printLine(s"   <- server needs input [${request.id}] ${request.method}: $message").orDie *>
      Console.printLine("   -> answering: accept, confirm=true").orDie.as:
        Json.Obj(
          "action"  -> Json.Str("accept"),
          "content" -> Json.Obj("confirm" -> Json.Bool(true)),
        )

  private def text(r: CallToolResult): String =
    r.content.collect { case ToolContent.Text(t, _) => t }.mkString

  private def step(title: String): UIO[Unit] =
    val pause = ZIO.when(sys.env.contains("STEP"))(Console.readLine).unit
    (Console.printLine(s"\n━━━ $title ━━━") *> pause).orDie

  private def say(line: String): UIO[Unit] = Console.printLine(s"   $line").orDie

  /**
   * Follow a task by hand — what `callTool` does internally — so each poll is
   * visible: tasks/get at the server's suggested interval, input answered with
   * tasks/update, until a terminal status.
   */
  private def follow(client: McpClient, task: McpTask, answered: Set[String] = Set.empty): IO[McpClientError, CallToolResult] =
    val shown = task.statusMessage.fold("")(m => s" — $m")
    say(s"tasks/get: ${task.status.wire}$shown") *> {
      task.status match
        case TaskStatus.Completed =>
          ZIO.fromOption(task.result).orElseFail(McpClientError.Protocol("completed without a result"))
            .flatMap(r => ZIO.fromEither(Json.Obj(r.fields).as[CallToolResult]).mapError(McpClientError.Decode(_)))
        case TaskStatus.Failed =>
          ZIO.fail(McpClientError.JsonRpc(task.error.fold(-32603)(_.code), task.error.fold("failed")(_.message)))
        case TaskStatus.Cancelled =>
          ZIO.fail(McpClientError.TaskCancelled(task.taskId.value, task.statusMessage))
        case TaskStatus.InputRequired | TaskStatus.Working =>
          // inputRequests repeat on every poll until answered, so answer each key once.
          val fresh = task.inputRequests.filterNot(r => answered.contains(r.id))
          for
            answers <- ZIO.foreach(fresh)(r => onInputRequest(r).map(InputResponse(r.id, _)))
            _       <- ZIO.when(answers.nonEmpty)(
                         client.updateTask(task.taskId, answers) *> say(s"tasks/update: ${answers.map(_.id).mkString(", ")}"))
            _       <- ZIO.sleep(Duration.fromMillis(task.pollIntervalMs.getOrElse(500L)))
            next    <- client.getTask(task.taskId)
            out     <- follow(client, next, answered ++ fresh.map(_.id))
          yield out
    }

  def run =
    for
      args <- getArgs
      url   = args.headOption.getOrElse("http://localhost:8081/mcp")
      _    <- ZIO.scoped:
                for
                  _      <- step("1. Connect: server/discover negotiates the version (no initialize, no session)")
                  // McpClient.connect(config, extensions) gives an extension client, which Skills needs below.
                  client <- McpClient.connect(McpClientConfig(url, onInputRequest = Some(onInputRequest)), McpClientExtensions.empty)
                  _      <- say(s"server ${client.serverInfo.name} ${client.serverInfo.version}, negotiated ${client.protocolVersion}")
                  _      <- say(s"extensions: ${client.serverCapabilities.extensions.fold("none")(_.keys.mkString(", "))}")
                  modern  = client.protocolVersion == ProtocolVersion.V2026_07_28.wire

                  _      <- step("2. tools/list")
                  tools  <- client.listTools
                  _      <- say(tools.map(_.name.value).mkString(", "))
                  names   = tools.map(_.name.value).toSet

                  _      <- ZIO.when(names("add")):
                              step("3. tools/call: a plain stateless call") *>
                                client.callTool("add", Json.Obj("a" -> Json.Num(2), "b" -> Json.Num(3)))
                                  .flatMap(r => say(s"add(2, 3) = ${text(r)}"))

                  _      <- ZIO.when(names("book_flight")):
                              step("4. MRTR: the server answers input_required, the client retries with inputResponses") *>
                                client.callTool("book_flight", Json.Obj("destination" -> Json.Str("Denver")))
                                  .flatMap(r => say(s"= ${text(r)}"))

                  _      <- ZIO.when(modern && names("deep_research")):
                              for
                                _       <- step("5. Tasks: the server decides to run deep_research as a task; we poll it")
                                started <- client.startTool("deep_research", Json.Obj("topic" -> Json.Str("Stockholm")))
                                result  <- started match
                                             case ToolCallOutcome.Completed(r) =>
                                               say("the server answered synchronously").as(r)
                                             case ToolCallOutcome.Started(task) =>
                                               say(s"CreateTaskResult: taskId=${task.taskId.value}, ttlMs=${task.ttlMs.fold("null")(_.toString)}, pollIntervalMs=${task.pollIntervalMs.getOrElse(0L)}") *>
                                                 follow(client, task)
                                _       <- say(s"= ${text(result)}")
                              yield ()

                  _      <- ZIO.when(modern && names("plan_trip")):
                              step("6. Tasks with input: plan_trip goes input_required mid-task; callTool polls and answers via tasks/update") *>
                                client.callTool("plan_trip", Json.Obj("destination" -> Json.Str("Lisbon")))
                                  .flatMap(r => say(s"= ${text(r)}"))

                  _      <- ZIO.when(modern && names("deep_research")):
                              for
                                _       <- step("7. tasks/cancel: stop a running task")
                                started <- client.startTool("deep_research", Json.Obj("topic" -> Json.Str("Oslo")))
                                _       <- started match
                                             case ToolCallOutcome.Completed(_) => say("the server answered synchronously")
                                             case ToolCallOutcome.Started(task) =>
                                               client.cancelTask(task.taskId) *>
                                                 client.getTask(task.taskId).flatMap: t =>
                                                   say(s"tasks/get after cancel: ${t.status.wire}${t.statusMessage.fold("")(m => s" — $m")}")
                              yield ()

                  _      <- McpSkillsClient.from(client).toOption.fold(ZIO.unit): skills =>
                              for
                                _    <- step("8. Skills extension: skills/list, then the skill's content via resources/read")
                                page <- skills.list()
                                _    <- ZIO.foreachDiscard(page.skills): s =>
                                          val files = s.resources match
                                            case McpSkillResources.Static(values) => s"${values.size} files"
                                            case McpSkillResources.Dynamic        => "dynamic"
                                          say(s"${s.uri.value} ($files)")
                                body <- ZIO.foreach(page.skills.find(_.uri.value.contains("flight-booking")))(s => skills.readSkill(s.uri))
                                _    <- ZIO.foreachDiscard(body.toList.flatten.flatMap(_.text).headOption)(t =>
                                          say(t.linesIterator.take(6).mkString("\n   ")))
                              yield ()
                yield ()
    yield ()
  .provideSome[ZIOAppArgs](Client.default)
