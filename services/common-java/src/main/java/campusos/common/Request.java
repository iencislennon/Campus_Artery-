package campusos.common;

import java.util.Collections;
import java.util.Map;

public record Request(String method, String path, Map<String, String> query, Map<String, String> pathParams,
                      Object body, String bearer, AuthUser user) {

    @SuppressWarnings("unchecked")
    public Map<String, Object> bodyMap() {
        if (body == null) return Collections.emptyMap();
        if (!(body instanceof Map)) throw ApiException.badRequest("Request body must be a JSON object.");
        return (Map<String, Object>) body;
    }

    public String bodyString(String key) {
        Object value = bodyMap().get(key);
        return value == null ? null : value.toString().trim();
    }

    public String requiredBody(String key) {
        String value = bodyString(key);
        if (value == null || value.isEmpty()) throw ApiException.invalid("Field '" + key + "' is required.");
        return value;
    }

    public String param(String key) { return pathParams.get(key); }
    public String queryParam(String key) { return query.getOrDefault(key, ""); }
}
