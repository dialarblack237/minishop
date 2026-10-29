package com.example.minishop.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class OpenApiContractTest {

    @LocalServerPort
    int port;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("runtime spec matches the committed snapshot")
    void specMatchesSnapshot() throws Exception {
        JsonNode runtime = json.readTree(new URL("http://localhost:" + port + "/v3/api-docs"));
        try (InputStream snapshotStream = getClass().getResourceAsStream("/openapi-snapshot.json")) {
            assertThat(snapshotStream)
                    .as("openapi-snapshot.json must exist in src/test/resources")
                    .isNotNull();
            JsonNode snapshot = json.readTree(snapshotStream);
            stripServers(runtime);
            stripServers(snapshot);
            assertThat(runtime)
                    .as("Runtime /v3/api-docs must match the committed snapshot")
                    .isEqualTo(snapshot);
        }
    }

    @Test
    @DisplayName("runtime spec matches the hand-authored openapi.yaml (path + method + status)")
    void runtimeMatchesContract() throws Exception {
        JsonNode runtime = json.readTree(new URL("http://localhost:" + port + "/v3/api-docs"));
        JsonNode contract = yamlToJson(Path.of("src/main/resources/openapi.yaml"));

        List<String> problems = new ArrayList<>();
        JsonNode contractPaths = contract.path("paths");
        contractPaths.fieldNames().forEachRemaining(path -> {
            JsonNode contractPathItem = contractPaths.path(path);
            JsonNode runtimePathItem = runtime.path("paths").path(path);
            if (runtimePathItem.isMissingNode()) {
                problems.add("path missing from runtime: " + path);
                return;
            }
            contractPathItem.fieldNames().forEachRemaining(method -> {
                JsonNode contractOperation = contractPathItem.path(method);
                JsonNode runtimeOperation = runtimePathItem.path(method);
                if (runtimeOperation.isMissingNode()) {
                    problems.add("operation missing from runtime: " + method.toUpperCase() + " " + path);
                    return;
                }
                JsonNode contractResponses = contractOperation.path("responses");
                JsonNode runtimeResponses = runtimeOperation.path("responses");
                contractResponses.fieldNames().forEachRemaining(code -> {
                    JsonNode runtimeResponse = runtimeResponses.path(code);
                    if (runtimeResponse.isMissingNode()) {
                        problems.add("response " + code + " missing from runtime: "
                                + method.toUpperCase() + " " + path);
                        return;
                    }
                    // Rule: if the runtime describes no content for this response,
                    // it is weaker than the promise but not a contradiction.
                    // SpringDoc emits error responses with description-only content by
                    // default; the promise may name content, the runtime may not.
                    // Schema-level comparison is a follow-up PR (DTO @Schema annotations),
                    // not this test. Named in the PR description.
                });
            });
        });

        assertThat(problems)
                .as("promise (openapi.yaml) vs reality (/v3/api-docs) — path + method + status")
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private JsonNode yamlToJson(Path path) throws Exception {
        Map<String, Object> yamlMap = new Yaml().load(Files.newInputStream(path));
        return json.valueToTree(yamlMap);
    }

    private void stripServers(JsonNode spec) {
        if (spec instanceof ObjectNode obj) {
            obj.remove("servers");
        }
    }
}