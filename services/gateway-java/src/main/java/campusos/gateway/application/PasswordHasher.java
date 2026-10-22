package campusos.gateway.application;

public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String stored);
}
