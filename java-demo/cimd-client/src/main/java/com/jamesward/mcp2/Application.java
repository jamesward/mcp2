package com.jamesward.mcp2;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2CimdHttpClientTransportCustomizer;
import org.springaicommunity.mcp.security.client.sync.oauth2.metadata.McpMetadataDiscoveryService;
import org.springaicommunity.mcp.security.client.sync.oauth2.registration.InMemoryMcpClientRegistrationRepository;
import org.springaicommunity.mcp.security.client.sync.oauth2.registration.McpClientRegistrationRepository;
import org.springaicommunity.mcp.security.client.sync.oauth2.registration.cimd.DefaultMcpOAuth2CimdClientManager;
import org.springaicommunity.mcp.security.client.sync.oauth2.registration.cimd.McpOAuth2CimdClientManager;
import org.springaicommunity.mcp.security.common.url.DefaultUrlValidator;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springaicommunity.mcp.security.client.sync.config.McpClientOAuth2Configurer.mcpClientOAuth2;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // OAuth2 client support for MCP: adds RFC 8707 resource= to authorize + token requests
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // we host the CIMD document elsewhere (cimd.now), so don't serve one locally
                .with(mcpClientOAuth2(), mcp -> mcp.cimd(false))
                .build();
    }

    @Bean
    McpClientRegistrationRepository mcpClientRegistrationRepository() {
        return new InMemoryMcpClientRegistrationRepository();
    }

    /**
     * On the first 401 from the MCP server:
     * WWW-Authenticate -> Protected Resource Metadata -> Authorization Server metadata
     * -> (AS says client_id_metadata_document_supported) -> client_id = our CIMD URL.
     * No registration call. The AS fetches the document from the client_id URL.
     */
    @Bean
    McpOAuth2CimdClientManager cimdClientManager(McpClientRegistrationRepository repo,
                                                 @Value("${cimd.base-url}") String cimdBaseUrl) {
        var urlValidator = new DefaultUrlValidator(true); // allow http://localhost MCP server for the demo
        var manager = new DefaultMcpOAuth2CimdClientManager(new McpMetadataDiscoveryService(urlValidator), repo, urlValidator);
        // cimd.now serves {client_id: <this url>, redirect_uris: [http://localhost:8085/<path>]}
        manager.setClientRegistrationCustomizer(reg -> ClientRegistration.withClientRegistration(reg)
                .clientId(cimdBaseUrl + "/authorize/oauth2/code/" + reg.getRegistrationId())
                .build());
        return manager;
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository repo,
                                                          OAuth2AuthorizedClientRepository authorizedClients) {
        return new DefaultOAuth2AuthorizedClientManager(repo, authorizedClients);
    }

    // Attach the user's OAuth token to MCP requests; turn a 401 into "go log in"
    @Bean
    OAuth2CimdHttpClientTransportCustomizer transportCustomizer(OAuth2AuthorizedClientManager authorizedClientManager,
                                                                McpClientRegistrationRepository repo,
                                                                McpOAuth2CimdClientManager cimdClientManager) {
        return new OAuth2CimdHttpClientTransportCustomizer(authorizedClientManager, repo, cimdClientManager);
    }

    // Make the current HTTP request + user available to the MCP transport
    @Bean
    McpClientCustomizer<McpClient.SyncSpec> syncClientCustomizer() {
        return (name, spec) -> spec.transportContextProvider(new AuthenticationMcpTransportContextProvider());
    }
}

@RestController
class WhoAmIController {

    private final List<McpSyncClient> clients;

    WhoAmIController(List<McpSyncClient> clients) {
        this.clients = clients;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    String index() {
        var client = clients.getFirst();
        var result = client.callTool(McpSchema.CallToolRequest.builder("whoami").build());
        var text = ((McpSchema.TextContent) result.content().getFirst()).text();
        return """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>MCP CIMD client</title></head>
            <body style="font-family: sans-serif; padding: 2em">
              <h1>Called the secure MCP server</h1>
              <p><code>whoami</code> &rarr; <strong>%s</strong></p>
            </body></html>
            """.formatted(text.replace("<", "&lt;"));
    }
}
