package campusos.gateway.infrastructure;

import campusos.common.Database;
import campusos.gateway.application.UserRepository;
import java.util.Optional;

public final class SqliteUserRepository implements UserRepository {
    private final Database db;

    public SqliteUserRepository(Database db) {
        this.db = db;
        db.executeScript("""
                CREATE TABLE IF NOT EXISTS users (
                  id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
                  role TEXT NOT NULL CHECK (role IN ('STUDENT','TEACHER')),
                  password_hash TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now')))
                ;
                CREATE TABLE IF NOT EXISTS login_codes (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL,
                  code_hash TEXT NOT NULL, expires_at INTEGER NOT NULL,
                  used INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT (datetime('now')))
                ;
                CREATE TABLE IF NOT EXISTS trusted_devices (
                  user_id TEXT NOT NULL, device_id TEXT NOT NULL,
                  registered_at TEXT NOT NULL DEFAULT (datetime('now')),
                  last_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
                  PRIMARY KEY (user_id, device_id))
                """);
    }

    @Override
    public Optional<StoredUser> findByEmail(String email) {
        return db.query("SELECT id, email, name, role, password_hash FROM users WHERE email = ?",
                r -> new StoredUser(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)), email).stream().findFirst();
    }

    @Override
    public Optional<LoginCode> latestLoginCode(String email) {
        return db.query("SELECT id, email, code_hash, expires_at, used FROM login_codes WHERE email = ? ORDER BY id DESC LIMIT 1",
                r -> new LoginCode(r.getLong(1), r.getString(2), r.getString(3), r.getLong(4), r.getInt(5) != 0), email)
                .stream().findFirst();
    }

    @Override
    public void saveLoginCode(String email, String codeHash, long expiresAt) {
        db.update("INSERT INTO login_codes (email, code_hash, expires_at) VALUES (?,?,?)", email, codeHash, expiresAt);
    }

    @Override
    public void consumeLoginCode(long id) {
        db.update("UPDATE login_codes SET used = 1 WHERE id = ?", id);
    }

    @Override
    public boolean isDeviceTrusted(String userId, String deviceId) {
        return !db.query("SELECT 1 FROM trusted_devices WHERE user_id = ? AND device_id = ? LIMIT 1",
                r -> r.getInt(1), userId, deviceId).isEmpty();
    }

    @Override
    public void trustDevice(String userId, String deviceId) {
        db.update("INSERT INTO trusted_devices (user_id, device_id) VALUES (?,?) ON CONFLICT(user_id, device_id) DO UPDATE SET last_seen_at = datetime('now')",
                userId, deviceId);
    }

    @Override
    public void insert(StoredUser u) {
        db.update("INSERT INTO users (id, email, name, role, password_hash) VALUES (?,?,?,?,?)", u.id(), u.email(), u.name(), u.role(), u.passwordHash());
    }
}
