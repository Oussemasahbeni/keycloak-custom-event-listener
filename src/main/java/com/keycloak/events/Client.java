package com.keycloak.events;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;



public class Client {
    private static final Logger log = Logger.getLogger(Client.class);
    private static final String WEBHOOK_URL = "WEBHOOK_URL";

    public static void postService(String data) throws IOException, InterruptedException {
        try {
            final String urlString = System.getenv(WEBHOOK_URL);
            log.debugf("WEBHOOK_URL: %s", urlString);

            if (urlString == null || urlString.isEmpty()) {
                throw new IllegalArgumentException("Environment variable WEBHOOK_URL is not set or is empty.");
            }

            URI uri = URI.create(urlString);
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