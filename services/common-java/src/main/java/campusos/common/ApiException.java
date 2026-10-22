package campusos.common;

/** An error the caller can act on; mapped 1:1 to an HTTP status and a JSON {message, code} body. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static ApiException badRequest(String message) { return new ApiException(400, "BAD_REQUEST", message); }
    public static ApiException unauthorized(String message) { return new ApiException(401, "UNAUTHORIZED", message); }
    public static ApiException forbidden(String message) { return new ApiException(403, "FORBIDDEN", message); }
    public static ApiException notFound(String message) { return new ApiException(404, "NOT_FOUND", message); }
    public static ApiException conflict(String message) { return new ApiException(409, "CONFLICT", message); }
    public static ApiException invalid(String message) { return new ApiException(422, "VALIDATION_FAILED", message); }
    public static ApiException unavailable(String message) { return new ApiException(503, "SERVICE_UNAVAILABLE", message); }
}
