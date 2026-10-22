package campusos.fixmycampus.presentation;

import campusos.common.AuthUser;
import campusos.common.Database;
import campusos.common.Env;
import campusos.common.HttpApp;
import campusos.common.ServiceClient;
import campusos.common.Tokens;
import campusos.fixmycampus.application.ReportService;
import campusos.fixmycampus.application.processing.ReportPipelineFactory;
import campusos.fixmycampus.infrastructure.CampusPlusRoomDirectory;
import campusos.fixmycampus.infrastructure.AiValidationClient;
import campusos.fixmycampus.infrastructure.FixSchema;
import campusos.fixmycampus.infrastructure.SqliteReportRepository;
import campusos.fixmycampus.infrastructure.SqliteSupportRepositories;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;

/** Composition root. The decorator chain is chosen by FIXMYCAMPUS_PIPELINE. */
public final class FixMyCampusMain {
    private static final String API = "/api/v1";

    public static void main(String[] args) throws Exception {
        Database db = new Database(Path.of(Env.get("CAMPUSOS_DATA_DIR", "data"), "issues.db"));
        FixSchema.init(db);

        var reports = new SqliteReportRepository(db);
        var support = new SqliteSupportRepositories(db);
        var pipeline = new ReportPipelineFactory(reports, support, support, support)
                .build(Arrays.asList(Env.get("FIXMYCAMPUS_PIPELINE", "validation,duplicate,notification").split(",")));
        var rooms = new CampusPlusRoomDirectory(new ServiceClient("CampusPlus", Env.get("CAMPUSPLUS_URL", "http://localhost:8081/api/v1"), Duration.ofSeconds(3)));
        ReportController controller = new ReportController(new ReportService(pipeline, reports, support, rooms, support, support,
                new AiValidationClient(Env.get("AI_VALIDATION_URL", "http://localhost:8084"))));

        new HttpApp("fix-my-campus", Env.getInt("FIXMYCAMPUS_PORT", 8082), Tokens.fromEnv(), Env.get("CAMPUSOS_ALLOWED_ORIGIN", "*"))
                .route("GET", API + "/issue-categories", controller::categories)
                .route("GET", API + "/issues", controller::list)
                .route("GET", API + "/issues/notifications", controller::notifications)
                .route("POST", API + "/issues", controller::create)
                .route("POST", API + "/issues/{id}/status", Set.of(AuthUser.TEACHER), controller::setStatus)
                .start();
    }
}
