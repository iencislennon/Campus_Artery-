package campusos.common;

/** Already-serialised JSON, passed through the gateway without re-encoding. */
public record RawJson(String text) {}
