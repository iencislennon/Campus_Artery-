package campusos.gateway.application;

import java.util.List;
import java.util.Map;

/** Which downstream service owns which first path segment. */
public final class ServiceRoutes {
    public static final String CAMPUS_PLUS = "CampusPlus";
    public static final String FIX_MY_CAMPUS = "FixMyCampus";
    public static final String PROJECT_FORGE = "ProjectForge";

    private static final Map<String, List<String>> OWNED_PREFIXES = Map.of(
            CAMPUS_PLUS, List.of("rooms", "buildings", "teachers", "office-hours"),
            FIX_MY_CAMPUS, List.of("issues", "issue-categories"),
            PROJECT_FORGE, List.of("initiatives", "clubs", "surveys", "recommendations", "profile"));

    private ServiceRoutes() {}

    public static Map<String, List<String>> ownedPrefixes() { return OWNED_PREFIXES; }
}
