package com.jamesward.mcp2;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecureServerTest {

    static final String ISSUER = "https://login.jamesward.dev";
    static final ObjectMapper json = new ObjectMapper();
    static final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    String mcpUrl() {
        return "http://localhost:" + port + "/mcp";
    }

    @Test
    void unauthenticatedGetsChallengeWithResourceMetadata() throws Exception {
        var response = postMcp(null, """
            {"jsonrpc":"2.0","id":1,"method":"tools/list"}""");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse(""))
                .contains("resource_metadata=");
    }

    @Test
    void protectedResourceMetadataPointsAtAuthServer() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/.well-known/oauth-protected-resource/mcp")).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        var prm = json.readTree(response.body());
        assertThat(prm.get("resource").asString()).isEqualTo(mcpUrl());
        assertThat(prm.get("authorization_servers").get(0).asString()).isEqualTo(ISSUER);
    }

    /** Live: machine-to-machine token from login.jamesward.dev, audience-bound to this server. */
    @Test
    void validAudienceBoundTokenCanCallTool() throws Exception {
        var token = clientCredentialsToken(mcpUrl());

        var response = postMcp(token, """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"whoami","arguments":{}}}""");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("user=");
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        var token = clientCredentialsToken("https://some-other-server.example.com/mcp");

        var response = postMcp(token, """
            {"jsonrpc":"2.0","id":3,"method":"tools/list"}""");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> postMcp(String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(mcpUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String clientCredentialsToken(String resource) throws Exception {
        // Dynamic Client Registration (deprecated in 2026-07-28, fine for a test m2m client)
        var reg = http.send(HttpRequest.newBuilder(URI.create(ISSUER + "/oauth2/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                    {"client_name":"mcp2-secure-server-test","grant_types":["client_credentials"],
                     "token_endpoint_auth_method":"client_secret_basic"}"""))
                .build(), HttpResponse.BodyHandlers.ofString());
        JsonNode creds = json.readTree(reg.body());
        var basic = Base64.getEncoder().encodeToString(
                (creds.get("client_id").asString() + ":" + creds.get("client_secret").asString()).getBytes(StandardCharsets.UTF_8));

        var form = "grant_type=client_credentials&resource=" + URLEncoder.encode(resource, StandardCharsets.UTF_8);
        var tok = http.send(HttpRequest.newBuilder(URI.create(ISSUER + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + basic)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(tok.statusCode()).as(tok.body()).isEqualTo(200);
        return json.readTree(tok.body()).get("access_token").asString();
    }
}
