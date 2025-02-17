package com.keycloak.events;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;



public class RestClient {
    private static final Logger log = Logger.getLogger(RestClient.class);
    private static final String BACKEND_BASE_URL = "BACKEND_BASE_URL";
    private static final String SYNC_PATH = "/api/v1/kc/sync";

    public static void sendRequest(String data) throws IOException {
        try {
            String apiUrl = System.getenv(BACKEND_BASE_URL);
            log.infof("BACKEND_BASE_URL: %s", apiUrl);

            if (apiUrl == null || apiUrl.isEmpty()) {
                throw new IllegalArgumentException("Environment variable BACKEND_BASE_URL is not set or is empty.");
            }

            apiUrl = apiUrl + SYNC_PATH;

            URI uri = URI.create(apiUrl);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new RuntimeException("Failed : HTTP error code : " + response.statusCode());
            }

            log.debugf("Output from Server: %s", response.body());
        } catch (IOException | InterruptedException e) {
            throw new IOException("Failed to post service: " + e.getMessage(), e);
        }
    }
}