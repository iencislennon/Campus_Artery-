package campusos.gateway.presentation;

import campusos.common.ApiException;
import campusos.common.AuthUser;
import campusos.common.Database;
import campusos.common.Env;
import campusos.common.HttpApp;
import campusos.common.Response;
import campusos.common.ServiceClient;
import campusos.common.Tokens;
import campusos.gateway.application.AccessPolicy;
import campusos.gateway.application.AuthService;
import campusos.gateway.application.ServiceRoutes;
import campusos.gateway.application.VerificationCodeSender;
import campusos.gateway.infrastructure.ConsoleCodeSender;
import campusos.gateway.infrastructure.MailerCodeSender;
import campusos.gateway.application.UserRepository;
import campusos.gateway.application.UserRepository.StoredUser;
import campusos.gateway.infrastructure.Pbkdf2PasswordHasher;
import campusos.gateway.infrastructure.SqliteUserRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Java HTTP entry point: login, permissions, API routing. Composition root. */
public final class GatewayMain {
    private static final String API = "/api/v1";

    public static void main(String[] args) throws Exception {
        Tokens tokens = Tokens.fromEnv();
        var hasher = new Pbkdf2PasswordHasher();
        UserRepository users = new SqliteUserRepository(new Database(Path.of(Env.get("CAMPUSOS_DATA_DIR", "data"), "auth.db")));
        seedDemoUsers(users, hasher);
        AuthService auth = new AuthService(users, hasher, Env.get("TEACHER_INVITE_CODE", "sdu-teacher"),
                Env.getBoolean("SDU_BAGDAR_SKIP_VERIFICATION", false),
                Env.getBoolean("SDU_BAGDAR_SKIP_TEACHER_INVITE", false), codeSender());
        AccessPolicy policy = new AccessPolicy();

        Map<String, ServiceClient> clients = Map.of(
                ServiceRoutes.CAMPUS_PLUS, new ServiceClient("CampusPlus", Env.get("CAMPUSPLUS_URL", "http://localhost:8081/api/v1"), Duration.ofSeconds(6)),
                ServiceRoutes.FIX_MY_CAMPUS, new ServiceClient("FixMyCampus", Env.get("FIXMYCAMPUS_URL", "http://localhost:8082/api/v1"), Duration.ofSeconds(6)),
                ServiceRoutes.PROJECT_FORGE, new ServiceClient("ProjectForge", Env.get("PROJECTFORGE_URL", "http://localhost:8083/api/v1"), Duration.ofSeconds(6)));

        HttpApp app = new HttpApp("gateway", Env.getInt("GATEWAY_PORT", 8080), tokens, Env.get("CAMPUSOS_ALLOWED_ORIGIN", "*"));
        app.open("POST", API + "/auth/request-code", r -> Response.ok(Map.of(
                "trusted", auth.requestVerificationCode(r.bodyString("email"), r.bodyString("deviceId")))));
        app.open("POST", API + "/auth/login", r -> Response.ok(session(tokens,
                r.bodyString("password") != null
                        ? auth.loginWithPassword(r.bodyString("email"), r.bodyString("password"))
                        : auth.login(r.bodyString("email"), r.bodyString("verificationCode"), r.bodyString("deviceId")))));
        app.open("POST", API + "/auth/register", r -> Response.created(session(tokens, auth.register(
                r.bodyString("name"), r.bodyString("email"), r.bodyString("password"), r.bodyString("role"), r.bodyString("inviteCode")))));
        app.route("GET", API + "/auth/me", r -> Response.ok(userView(r.user())));
        app.open("GET", API + "/health", r -> Response.ok(health(clients)));

        ServiceRoutes.ownedPrefixes().forEach((service, prefixes) -> {
            var proxy = new ProxyHandler(clients.get(service), policy);
            prefixes.forEach(prefix -> app.route("*", API + "/" + prefix, proxy).route("*", API + "/" + prefix + "/*", proxy));
        });
        app.start();
    }

    /** MAIL_DELIVERY=mailer (default) emails codes through the Mailer service; "console" prints them for local development. */
    private static VerificationCodeSender codeSender() {
        if ("console".equalsIgnoreCase(Env.get("MAIL_DELIVERY", "mailer"))) return new ConsoleCodeSender();
        var mailer = new ServiceClient("Mailer", Env.get("MAILER_URL", "http://localhost:8085/api/v1"), Duration.ofSeconds(15));
        return new MailerCodeSender(mailer, Env.require("CAMPUSOS_SECRET"));
    }

    private static Map<String, Object> session(Tokens tokens, AuthUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", userView(user));
        body.put("token", tokens.issue(user));
        return body;
    }

    private static Map<String, Object> userView(AuthUser user) {
        return Map.of("id", user.id(), "name", user.name(), "role", user.role());
    }

    private static Map<String, Object> health(Map<String, ServiceClient> clients) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("gateway", "ok");
        clients.forEach((name, client) -> {
            try {
                report.put(name, client.send("GET", "/health", null, null).statusCode() == 200 ? "ok" : "degraded");
            } catch (ApiException e) {
                report.put(name, "down");
            }
        });
        return report;
    }

    private static void seedDemoUsers(UserRepository users, Pbkdf2PasswordHasher hasher) {
        if (!"true".equals(Env.get("CAMPUSOS_SEED_DEMO", "false"))) return;
        seed(users, hasher, new String[]{"u-student", "student@sdu.edu.kz", "Ansar L.", AuthUser.STUDENT, Env.get("DEMO_STUDENT_PASSWORD", "")});
        seed(users, hasher, new String[]{"u-teacher", "teacher@sdu.edu.kz", "Dr. Aigerim Nurlanova", AuthUser.TEACHER, Env.get("DEMO_TEACHER_PASSWORD", "")});
    }

    private static void seed(UserRepository users, Pbkdf2PasswordHasher hasher, String[] u) {
        if (u[4].length() < 8 || users.findByEmail(u[1]).isPresent()) return;
        users.insert(new StoredUser(u[0], u[1], u[2], u[3], hasher.hash(u[4])));
    }
}
