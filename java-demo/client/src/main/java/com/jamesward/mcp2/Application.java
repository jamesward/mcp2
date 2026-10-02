package com.jamesward.mcp2;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.annotation.McpElicitation;
import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    ApplicationRunner run(List<McpSyncClient> clients) {
        return args -> clients.forEach(client -> {
            var server = client.getServerInfo();
            System.out.println("\n=== " + server.name() + " (protocol " + client.getCurrentInitializationResult().protocolVersion() + ")");
            var tools = client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
            System.out.println("tools: " + tools);

            if (tools.contains("create_shopping_list")) {
                // Explicit handles instead of sessions: the model carries listId between calls
                var created = client.callTool(call("create_shopping_list", Map.of()));
                var listId = ((Map<?, ?>) created.structuredContent()).get("listId");
                client.callTool(call("add_item", Map.of("listId", listId, "item", "coffee")));
                var list = client.callTool(call("get_shopping_list", Map.of("listId", listId)));
                System.out.println("shopping list " + listId + " = " + list.structuredContent());
            }

            if (tools.contains("book_flight")) {
                // Same zio tool that uses MRTR for modern clients. For this 2025-11-25 client it
                // becomes a server->client elicitation/create over SSE, handled below.
                var booked = client.callTool(call("book_flight", Map.of("destination", "Amsterdam")));
                System.out.println("book_flight = " + text(booked));
            }

            if (tools.contains("deep_research")) {
                var research = client.callTool(McpSchema.CallToolRequest.builder("deep_research")
                        .arguments(Map.of("topic", "Amsterdam")).progressToken("r1").build());
                System.out.println("deep_research = " + text(research));
            }
        });
    }

    @McpElicitation(clients = "zio")
    public McpSchema.ElicitResult onElicit(McpSchema.ElicitRequest request) {
        System.out.println("  <- server asks: " + request.message() + "  -> accept");
        return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of("confirm", true));
    }

    @McpProgress(clients = "zio")
    public void onProgress(McpSchema.ProgressNotification notification) {
        System.out.println("  progress " + notification.progress() + "/" + notification.total() + " " + notification.message());
    }

    private static McpSchema.CallToolRequest call(String name, Map<String, Object> args) {
        return McpSchema.CallToolRequest.builder(name).arguments(args).build();
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .reduce("", String::concat);
    }
}
