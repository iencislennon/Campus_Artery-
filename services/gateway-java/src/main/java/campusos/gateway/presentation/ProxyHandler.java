package campusos.gateway.presentation;

import campusos.common.ApiException;
import campusos.common.Handler;
import campusos.common.Json;
import campusos.common.RawJson;
import campusos.common.Request;
import campusos.common.Response;
import campusos.common.ServiceClient;
import campusos.gateway.application.AccessPolicy;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Forwards a verified request to the owning service, adding nothing the client could forge. */
public final class ProxyHandler implements Handler {
    private static final String BASE = "/api/v1";
    private final ServiceClient target;
    private final AccessPolicy policy;

    public ProxyHandler(ServiceClient target, AccessPolicy policy) {
        this.target = target;
        this.policy = policy;
    }

    @Override
    public Response handle(Request request) {
        policy.check(request.user(), request.method(), request.path());
        String query = request.query().entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String path = request.path().substring(BASE.length()) + (query.isEmpty() ? "" : "?" + query);
        String body = request.body() == null ? null : Json.stringify(request.body());
        HttpResponse<String> answer = target.send(request.method(), path, request.bearer(), body);
        String text = answer.body() == null || answer.body().isBlank() ? "null" : answer.body();
        if (answer.statusCode() >= 500) throw ApiException.unavailable("The service failed to handle this request.");
        return new Response(answer.statusCode(), new RawJson(text));
    }
}
