package com.jamesward.mcp2;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@Component
class MathTools {

    @McpTool(description = "add two numbers")
    public int add(int a, int b) {
        return a + b;
    }

    // Stateless tools get the transport context (e.g. HTTP headers), not a session.
    // McpSyncRequestContext (log / progress / elicit / sample) needs a back-channel,
    // which a stateless server does not have.
    @McpTool(description = "multiply two numbers")
    public int multiply(int a, int b, McpTransportContext ctx) {
        return a * b;
    }
}

/**
 * MCP 2026-07-28 removes protocol sessions (SEP-2567).
 * Cross-call state is an explicit, server-minted handle passed as an ordinary tool argument.
 * The model can see it, reason about it, and reuse it. Any replica can serve it if the
 * store is shared (in-memory here; Redis / a database in production).
 */
@Component
class ShoppingListTools {

    record ShoppingList(String listId, List<String> items) { }

    private final Map<String, List<String>> store = new ConcurrentHashMap<>();

    @McpTool(name = "create_shopping_list", description = "Creates a shopping list and returns its listId handle",
            generateOutputSchema = true)
    public ShoppingList createShoppingList() {
        var listId = "list_" + UUID.randomUUID().toString().substring(0, 8);
        store.put(listId, new CopyOnWriteArrayList<>());
        return new ShoppingList(listId, List.of());
    }

    @McpTool(name = "add_item", description = "Adds an item to a shopping list", generateOutputSchema = true)
    public ShoppingList addItem(
            @McpToolParam(description = "listId handle from create_shopping_list") String listId,
            @McpToolParam(description = "the item to add") String item) {
        var items = lookup(listId);
        items.add(item);
        return new ShoppingList(listId, List.copyOf(items));
    }

    @McpTool(name = "get_shopping_list", description = "Gets a shopping list", generateOutputSchema = true)
    public ShoppingList getShoppingList(
            @McpToolParam(description = "listId handle from create_shopping_list") String listId) {
        return new ShoppingList(listId, List.copyOf(lookup(listId)));
    }

    private List<String> lookup(String listId) {
        var items = store.get(listId);
        if (items == null) {
            throw new IllegalArgumentException("Unknown listId: " + listId + ". Call create_shopping_list first.");
        }
        return items;
    }
}
