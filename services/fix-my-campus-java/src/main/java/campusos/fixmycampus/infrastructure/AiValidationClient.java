package campusos.fixmycampus.infrastructure;

import campusos.common.ApiException;
import campusos.common.Json;
import campusos.common.ServiceClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Calls the shared AI validation service before a report enters FixMyCampus. */
public final class AiValidationClient {
    private final ServiceClient client;

    public AiValidationClient(String baseUrl) {
        this.client = new ServiceClient("AI validation", baseUrl, Duration.ofSeconds(8));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> validateIssue(String roomId, String category, String description,
                                             Map<String, Object> attachment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", roomId);
        body.put("category", category);
        body.put("description", description);
        if (attachment != null && !attachment.isEmpty()) body.put("attachment", attachment);
        var response = client.send("POST", "/api/v1/validate/fixmycampus", null, Json.stringify(body));
        if (response.statusCode() >= 500) throw ApiException.unavailable("AI validation is temporarily unavailable.");
        Object parsed = Json.parse(response.body());
        if (!(parsed instanceof Map<?, ?> raw)) throw ApiException.unavailable("AI validation returned an invalid response.");
        Map<String, Object> result = (Map<String, Object>) raw;
        if (!Boolean.TRUE.equals(result.get("valid"))) {
            throw ApiException.invalid("AI rejected this report: " + result.getOrDefault("reason", "The report is not valid."));
        }
        return result;
    }
}
