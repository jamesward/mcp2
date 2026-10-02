package com.jamesward.mcp2;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

import static org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer.mcpServerOAuth2;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * The MCP server is an OAuth 2.1 Resource Server. It:
     *  - serves RFC 9728 Protected Resource Metadata at /.well-known/oauth-protected-resource
     *  - answers 401 with WWW-Authenticate: Bearer resource_metadata="..."
     *  - validates JWTs from the AS, including the RFC 8707 audience (aud == this server)
     * Client registration (CIMD / DCR) is the Authorization Server's concern, not ours.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${mcp.auth.issuer-uri}") String issuerUri) {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .with(mcpServerOAuth2(), mcp -> mcp
                        .authorizationServer(issuerUri)
                        // RFC 8707: reject tokens minted for some other server (off by default!)
                        .validateAudienceClaim(true))
                .build();
    }
}

@Component
class SecureTools {

    @McpTool(description = "Returns the authenticated user and the audience their token was minted for")
    public String whoami() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return "user=" + jwt.getSubject() + " aud=" + jwt.getAudience();
        }
        return "anonymous";
    }
}
