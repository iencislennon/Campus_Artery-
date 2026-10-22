package campusos.gateway.application;

import campusos.common.ApiException;
import campusos.common.AuthUser;
import campusos.gateway.application.UserRepository.StoredUser;
import java.util.Optional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AuthService {
    private static final long CODE_TTL_SECONDS = 600;
    private static final long RESEND_COOLDOWN_SECONDS = 60;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final String teacherInviteCode;
    private final boolean skipVerification;
    private final boolean skipTeacherInvite;
    private final VerificationCodeSender codeSender;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, PasswordHasher hasher, String teacherInviteCode, boolean skipVerification,
                       boolean skipTeacherInvite, VerificationCodeSender codeSender) {
        this.codeSender = codeSender;
        this.users = users;
        this.hasher = hasher;
        this.teacherInviteCode = teacherInviteCode;
        this.skipVerification = skipVerification;
        this.skipTeacherInvite = skipTeacherInvite;
    }

    public AuthUser register(String name, String email, String password, String role, String inviteCode) {
        if (name == null || name.isBlank()) throw ApiException.invalid("Name is required.");
        if (email == null || !EMAIL.matcher(email).matches()) throw ApiException.invalid("Enter a valid email address.");
        if (password == null || password.length() < 8) throw ApiException.invalid("Password needs at least 8 characters.");
        if (!AuthUser.STUDENT.equals(role) && !AuthUser.TEACHER.equals(role)) throw ApiException.invalid("Role must be STUDENT or TEACHER.");
        if (AuthUser.TEACHER.equals(role) && !skipTeacherInvite && !teacherInviteCode.equals(inviteCode)) {
            throw ApiException.forbidden("A valid teacher invite code is required.");
        }
        if (users.findByEmail(email.toLowerCase()).isPresent()) throw ApiException.conflict("An account with this email already exists.");
        StoredUser user = new StoredUser("u-" + UUID.randomUUID().toString().substring(0, 8), email.toLowerCase(), name.trim(), role, hasher.hash(password));
        users.insert(user);
        return new AuthUser(user.id(), user.name(), user.role());
    }

    public boolean requestVerificationCode(String email, String deviceId) {
        String normalizedEmail = normalizeEmail(email);
        requireDevice(deviceId);
        StoredUser user = users.findByEmail(normalizedEmail).orElseGet(() -> createStudent(normalizedEmail));
        if (skipVerification) return true;
        if (users.isDeviceTrusted(user.id(), deviceId)) return true;

        requireResendCooldown(normalizedEmail);
        String code = String.format("%06d", random.nextInt(1_000_000));
        users.saveLoginCode(normalizedEmail, hasher.hash(code), Instant.now().plusSeconds(CODE_TTL_SECONDS).getEpochSecond());
        codeSender.send(normalizedEmail, code);
        return false;
    }

    private void requireResendCooldown(String email) {
        users.latestLoginCode(email).ifPresent(latest -> {
            long sentAt = latest.expiresAt() - CODE_TTL_SECONDS;
            if (Instant.now().getEpochSecond() - sentAt < RESEND_COOLDOWN_SECONDS) {
                throw new ApiException(429, "TOO_MANY_REQUESTS", "A code was just sent. Wait a minute before requesting another.");
            }
        });
    }

    public AuthUser login(String email, String verificationCode, String deviceId) {
        String normalizedEmail = normalizeEmail(email);
        requireDevice(deviceId);
        StoredUser user = users.findByEmail(normalizedEmail)
                .orElseThrow(() -> ApiException.unauthorized("Email or verification code is incorrect."));

        if (skipVerification) {
            users.trustDevice(user.id(), deviceId);
            return new AuthUser(user.id(), user.name(), user.role());
        }

        if (!users.isDeviceTrusted(user.id(), deviceId)) {
            UserRepository.LoginCode latest = users.latestLoginCode(normalizedEmail)
                    .orElseThrow(() -> ApiException.unauthorized("Request a verification code first."));
            if (latest.used() || latest.expiresAt() < Instant.now().getEpochSecond()
                    || verificationCode == null || !hasher.matches(verificationCode, latest.codeHash())) {
                throw ApiException.unauthorized("Email or verification code is incorrect.");
            }
            users.consumeLoginCode(latest.id());
            users.trustDevice(user.id(), deviceId);
        } else {
            users.trustDevice(user.id(), deviceId);
        }
        return new AuthUser(user.id(), user.name(), user.role());
    }

    /** Legacy API compatibility for existing scripts; the frontend uses device verification. */
    public AuthUser loginWithPassword(String email, String password) {
        StoredUser user = email == null ? null : users.findByEmail(email.trim().toLowerCase())
                .filter(u -> password != null && hasher.matches(password, u.passwordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect."));
        return new AuthUser(user.id(), user.name(), user.role());
    }

    private String normalizeEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw ApiException.invalid("Enter a valid SDU email address.");
        }
        String normalized = email.trim().toLowerCase();
        if (!normalized.endsWith("@sdu.edu.kz")) throw ApiException.invalid("Use your @sdu.edu.kz email.");
        return normalized;
    }

    private StoredUser createStudent(String email) {
        String localPart = email.substring(0, email.indexOf('@'));
        StoredUser user = new StoredUser("u-" + UUID.randomUUID().toString().substring(0, 8),
                email, localPart, AuthUser.STUDENT, hasher.hash(UUID.randomUUID().toString()));
        users.insert(user);
        return user;
    }

    private void requireDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > 128) {
            throw ApiException.invalid("This device could not be identified. Refresh and try again.");
        }
    }
}
