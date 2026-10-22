package campusos.campusplus.presentation;

import campusos.campusplus.application.OfficeHourService;
import campusos.campusplus.application.RoomService;
import campusos.campusplus.application.StrategyRegistry;
import campusos.campusplus.domain.EarliestDeadlineStrategy;
import campusos.campusplus.domain.FirstAvailableStrategy;
import campusos.campusplus.domain.PreferredTimeStrategy;
import campusos.campusplus.infrastructure.CampusSchema;
import campusos.campusplus.infrastructure.SqliteOfficeHourRepository;
import campusos.campusplus.infrastructure.SqliteRoomRepository;
import campusos.campusplus.infrastructure.SqliteTeacherRepository;
import campusos.campusplus.infrastructure.legacy.LegacyTimetableAdapter;
import campusos.campusplus.infrastructure.legacy.LegacyTimetableSystem;
import campusos.common.AuthUser;
import campusos.common.Database;
import campusos.common.Env;
import campusos.common.HttpApp;
import campusos.common.Tokens;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;

/** Composition root: the only place that knows concrete classes. */
public final class CampusPlusMain {
    private static final String API = "/api/v1";

    public static void main(String[] args) throws Exception {
        Database db = new Database(Path.of(Env.get("CAMPUSOS_DATA_DIR", "data"), "campus.db"));
        CampusSchema.init(db);

        Clock clock = Clock.systemDefaultZone();
        SqliteRoomRepository roomRepository = new SqliteRoomRepository(db);
        SqliteTeacherRepository teacherRepository = new SqliteTeacherRepository(db);
        SqliteOfficeHourRepository officeHourRepository = new SqliteOfficeHourRepository(db);

        List<String> roomCodes = roomRepository.search(new campusos.campusplus.application.RoomFilter("", null, "", null, ""))
                .stream().map(room -> room.id()).toList();
        var timetable = new LegacyTimetableAdapter(new LegacyTimetableSystem(roomCodes));

        StrategyRegistry strategies = new StrategyRegistry()
                .register(new FirstAvailableStrategy())
                .register(new EarliestDeadlineStrategy())
                .register(new PreferredTimeStrategy());

        RoomController roomController = new RoomController(new RoomService(roomRepository, timetable), clock);
        OfficeHourController officeHours = new OfficeHourController(
                new OfficeHourService(officeHourRepository, teacherRepository, strategies), teacherRepository, roomRepository, clock);

        new HttpApp("campus-plus", Env.getInt("CAMPUSPLUS_PORT", 8081), Tokens.fromEnv(), Env.get("CAMPUSOS_ALLOWED_ORIGIN", "*"))
                .route("GET", API + "/buildings", roomController::buildings)
                .route("GET", API + "/rooms", roomController::list)
                .route("GET", API + "/rooms/{id}", roomController::get)
                .route("GET", API + "/teachers", officeHours::teachers)
                .route("PUT", API + "/teachers/me", Set.of(AuthUser.TEACHER), officeHours::registerOffice)
                .route("GET", API + "/office-hours", officeHours::list)
                .route("GET", API + "/office-hours/strategies", officeHours::strategies)
                .route("POST", API + "/office-hours", Set.of(AuthUser.TEACHER), officeHours::create)
                .route("POST", API + "/office-hours/book", officeHours::book)
                .start();
    }
}
