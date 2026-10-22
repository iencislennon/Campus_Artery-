package campusos.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HS256-signed bearer tokens: base64url(payload).base64url(hmac). Verified by every service. */
public final class Tokens {
    private final byte[] secret;
    private final long ttlSeconds;

    public Tokens(String secret, long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public static Tokens fromEnv() {
        return new Tokens(Env.require("CAMPUSOS_SECRET"), Long.parseLong(Env.get("CAMPUSOS_TOKEN_TTL_SECONDS", "28800")));
    }

    public String issue(AuthUser user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.id());
        payload.put("name", user.name());
        payload.put("role", user.role());
        payload.put("exp", Instant.now().getEpochSecond() + ttlSeconds);
        String body = encode(Json.stringify(payload).getBytes(StandardCharsets.UTF_8));
        return body + "." + encode(sign(body));
    }

    public AuthUser verify(String token) {
        if (token == null) throw ApiException.unauthorized("Missing bearer token.");
        try {
            return decode(token);
        } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
            throw ApiException.unauthorized("Malformed token.");
        }
    }

    private AuthUser decode(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2) throw ApiException.unauthorized("Malformed token.");
        if (!MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1]))) {
            throw ApiException.unauthorized("Invalid token signature.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) Json.parse(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        if (((Number) payload.get("exp")).longValue() < Instant.now().getEpochSecond()) {
            throw ApiException.unauthorized("Token expired. Sign in again.");
        }
        return new AuthUser((String) payload.get("sub"), (String) payload.get("name"), (String) payload.get("role"));
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
