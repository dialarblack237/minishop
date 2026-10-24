package com.example.minishop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OpenApiContractTest {

    @LocalServerPort
    int port;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void specMatchesSnapshot() throws Exception {
        JsonNode runtime = json.readTree(new URL("http://localhost:" + port + "/v3/api-docs"));
        try (InputStream snapshotStream = getClass().getResourceAsStream("/openapi-snapshot.json")) {
            assertThat(snapshotStream)
                    .as("openapi-snapshot.json must exist in src/test/resources")
                    .isNotNull();
            JsonNode snapshot = json.readTree(snapshotStream);

            // The 'servers' field contains the port the app is running on.
            // That's environment-specific (8080 in dev, 56464 in tests,
            // the Render URL in prod) and not part of the API contract.
            // Strip it from both sides before comparing.
            stripServers(runtime);
            stripServers(snapshot);

            assertThat(runtime)
                    .as("Runtime /v3/api-docs must match the committed snapshot")
                    .isEqualTo(snapshot);
        }
    }

    private void stripServers(JsonNode spec) {
        if (spec instanceof ObjectNode obj) {
            obj.remove("servers");
        }
    }
}