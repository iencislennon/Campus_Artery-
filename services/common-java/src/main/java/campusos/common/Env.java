package campusos.common;

/** Configuration is read only from environment variables (loaded from .env by the start scripts). */
public final class Env {
    private Env() {}

    public static String get(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment variable " + name);
        return value;
    }

    public static int getInt(String name, int fallback) {
        return Integer.parseInt(get(name, String.valueOf(fallback)));
    }

    public static boolean getBoolean(String name, boolean fallback) {
        return Boolean.parseBoolean(get(name, String.valueOf(fallback)));
    }
}
