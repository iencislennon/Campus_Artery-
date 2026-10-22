package campusos.gateway.application;

import java.util.Optional;

public interface UserRepository {
    record StoredUser(String id, String email, String name, String role, String passwordHash) {}
    record LoginCode(long id, String email, String codeHash, long expiresAt, boolean used) {}

    Optional<StoredUser> findByEmail(String email);

    Optional<LoginCode> latestLoginCode(String email);

    void saveLoginCode(String email, String codeHash, long expiresAt);

    void consumeLoginCode(long id);

    boolean isDeviceTrusted(String userId, String deviceId);

    void trustDevice(String userId, String deviceId);

    void insert(StoredUser user);
}
