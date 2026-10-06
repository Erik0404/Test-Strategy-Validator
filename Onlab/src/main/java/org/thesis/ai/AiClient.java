package org.thesis.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

public class AiClient {
    private static final String TS_SERVER_URL = "http://localhost:3000/generate-validated-scenario";
    private final HttpClient client;
    private final ObjectMapper mapper;

    public AiClient() {
        this.client = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public String getTestStrategy(String requirement, String metaModel, String lastError) throws Exception {
        Map<String, String> payload = new HashMap<>();
        payload.put("requirement", requirement);
        payload.put("metaModel", metaModel);
        if (lastError != null && !lastError.isEmpty()) {
            payload.put("lastError", lastError);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TS_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}