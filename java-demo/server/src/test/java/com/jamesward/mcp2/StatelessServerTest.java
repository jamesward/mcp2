package com.jamesward.mcp2;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatelessServerTest {

    @LocalServerPort
    int port;

    McpSyncClient newClient() {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build();
        var client = McpClient.sync(transport).build();
        client.initialize();
        return client;
    }

    @Test
    void initializeReturnsNoSessionId() throws Exception {
        var body = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"t","version":"1"}}}""";
        var response = post(body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isEmpty();
    }

    @Test
    void toolCallWorksWithoutInitialize() throws Exception {
        var body = """
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"add","arguments":{"a":2,"b":3}}}""";
        var response = post(body);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"text\":\"5\"");
    }

    @Test
    void getIsNotAllowed() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Accept", "text/event-stream").GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(405);
    }

    @Test
    void handlesWorkAcrossIndependentClients() {
        var clientA = newClient();
        var created = clientA.callTool(call("create_shopping_list", Map.of()));
        var listId = (String) ((Map<?, ?>) created.structuredContent()).get("listId");
        clientA.callTool(call("add_item", Map.of("listId", listId, "item", "coffee")));
        clientA.closeGracefully();

        // a different client (no shared session) continues with the handle
        var clientB = newClient();
        clientB.callTool(call("add_item", Map.of("listId", listId, "item", "milk")));
        var list = clientB.callTool(call("get_shopping_list", Map.of("listId", listId)));
        clientB.closeGracefully();

        assertThat(list.isError()).isFalse();
        assertThat(((Map<?, ?>) list.structuredContent()).get("items")).isEqualTo(java.util.List.of("coffee", "milk"));
    }

    @Test
    void unknownHandleIsAToolError() {
        var client = newClient();
        var result = client.callTool(call("get_shopping_list", Map.of("listId", "nope")));
        client.closeGracefully();

        assertThat(result.isError()).isTrue();
        assertThat(result.content().toString()).contains("Unknown listId");
    }

    private static McpSchema.CallToolRequest call(String name, Map<String, Object> args) {
        return McpSchema.CallToolRequest.builder(name).arguments(args).build();
    }

    private HttpResponse<String> post(String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
