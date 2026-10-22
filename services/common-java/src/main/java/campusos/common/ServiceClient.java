package campusos.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** HTTP client for service-to-service calls with a hard timeout and safe failure mapping. */
public final class ServiceClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String serviceName;
    private final String baseUrl;
    private final Duration timeout;

    public ServiceClient(String serviceName, String baseUrl, Duration timeout) {
        this.serviceName = serviceName;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeout = timeout;
    }

    public HttpResponse<String> send(String method, String pathAndQuery, String bearer, String jsonBody) {
        return send(method, pathAndQuery, bearer, jsonBody, Map.of());
    }

    public HttpResponse<String> send(String method, String pathAndQuery, String bearer, String jsonBody, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery)).timeout(timeout)
                .header("Content-Type", "application/json")
                .method(method, jsonBody == null || jsonBody.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        headers.forEach(builder::header);
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw ApiException.unavailable(serviceName + " is unavailable. Try again shortly.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.unavailable(serviceName + " did not answer in time.");
        }
    }
}
