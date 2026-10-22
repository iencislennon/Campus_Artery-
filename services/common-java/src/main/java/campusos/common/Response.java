package campusos.common;

public record Response(int status, Object body) {
    public static Response ok(Object body) { return new Response(200, body); }
    public static Response created(Object body) { return new Response(201, body); }
}
