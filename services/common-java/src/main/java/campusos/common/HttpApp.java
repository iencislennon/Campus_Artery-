package campusos.common;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/** Tiny routing layer over the JDK HttpServer: path params, bearer auth, role checks, JSON errors, CORS. */
public final class HttpApp {
    private record Route(String method, String[] segments, boolean open, Set<String> roles, Handler handler) {}

    private final String name;
    private final int port;
    private final Tokens tokens;
    private final String allowedOrigin;
    private final List<Route> routes = new ArrayList<>();

    public HttpApp(String name, int port, Tokens tokens, String allowedOrigin) {
        this.name = name;
        this.port = port;
        this.tokens = tokens;
        this.allowedOrigin = allowedOrigin;
    }

    /** No authentication required. */
    public HttpApp open(String method, String pattern, Handler handler) {
        routes.add(new Route(method, split(pattern), true, Set.of(), handler));
        return this;
    }

    /** Any authenticated user. */
    public HttpApp route(String method, String pattern, Handler handler) {
        return route(method, pattern, Set.of(), handler);
    }

    /** Authenticated user holding one of {@code roles} (empty = any role). Method "*" matches every method. */
    public HttpApp route(String method, String pattern, Set<String> roles, Handler handler) {
        routes.add(new Route(method, split(pattern), false, roles, handler));
        return this;
    }

    public void start() throws IOException {
        Handler health = request -> Response.ok(Map.of("status", "ok", "service", name));
        open("GET", "/health", health).open("GET", "/api/v1/health", health);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::dispatch);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("[%s] listening on http://localhost:%d%n", name, port);
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        long started = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        int status;
        try {
            if ("OPTIONS".equals(method)) {
                status = 204;
                send(exchange, status, null);
            } else {
                Response response = handle(exchange, method, path);
                status = response.status();
                send(exchange, status, response.body());
            }
        } catch (ApiException e) {
            status = e.status();
            send(exchange, status, Map.of("message", e.getMessage(), "code", e.code()));
        } catch (Exception e) {
            status = 500;
            e.printStackTrace();
            send(exchange, status, Map.of("message", "Internal error. Try again later.", "code", "INTERNAL"));
        }
        System.out.printf("[%s] %s %s -> %d (%d ms)%n", name, method, path, status, (System.nanoTime() - started) / 1_000_000);
    }

    private Response handle(HttpExchange exchange, String method, String path) throws Exception {
        String[] segments = split(path);
        boolean pathKnown = false;
        for (Route route : routes) {
            Map<String, String> params = match(route.segments(), segments);
            if (params == null) continue;
            pathKnown = true;
            if (!route.method().equals("*") && !route.method().equals(method)) continue;
            String bearer = bearerOf(exchange);
            AuthUser user = route.open() ? null : tokens.verify(bearer);
            if (user != null && !route.roles().isEmpty() && !route.roles().contains(user.role())) {
                throw ApiException.forbidden("Your role (" + user.role() + ") cannot do this.");
            }
            Request request = new Request(method, path, parseQuery(exchange.getRequestURI().getRawQuery()), params,
                    readBody(exchange), bearer, user);
            return route.handler().handle(request);
        }
        throw pathKnown ? new ApiException(405, "METHOD_NOT_ALLOWED", "Method not allowed.") : ApiException.notFound("No such endpoint: " + path);
    }

    private static Map<String, String> match(String[] pattern, String[] actual) {
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i].equals("*")) return params;
            if (i >= actual.length) return null;
            if (pattern[i].startsWith("{")) params.put(pattern[i].substring(1, pattern[i].length() - 1), decode(actual[i]));
            else if (!pattern[i].equals(actual[i])) return null;
        }
        return pattern.length == actual.length ? params : null;
    }

    private static String[] split(String path) {
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    private static String bearerOf(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }

    private static Object readBody(HttpExchange exchange) throws IOException {
        String text = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Json.parse(text);
    }

    public static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return query;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            query.put(key, eq < 0 ? "" : decode(pair.substring(eq + 1)));
        }
        return query;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = body == null ? new byte[0]
                : (body instanceof RawJson raw ? raw.text() : Json.stringify(body)).getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", allowedOrigin);
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        headers.set("Vary", "Origin");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        exchange.close();
    }
}
