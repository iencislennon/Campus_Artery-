package campusos.gateway.application;

import campusos.common.ApiException;
import campusos.common.AuthUser;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Role permissions enforced at the edge. Services re-check ownership; this stops obviously forbidden calls early. */
public final class AccessPolicy {
    private record Rule(String method, Pattern path, Set<String> roles) {}

    private final List<Rule> rules = List.of(
            new Rule("POST", Pattern.compile("^/api/v1/office-hours$"), Set.of(AuthUser.TEACHER)),
            new Rule("PUT", Pattern.compile("^/api/v1/teachers/me$"), Set.of(AuthUser.TEACHER)),
            new Rule("POST", Pattern.compile("^/api/v1/issues/[^/]+/status$"), Set.of(AuthUser.TEACHER)));

    public void check(AuthUser user, String method, String path) {
        boolean denied = rules.stream().anyMatch(r -> r.method().equals(method) && r.path().matcher(path).matches() && !r.roles().contains(user.role()));
        if (denied) throw ApiException.forbidden("Your role (" + user.role() + ") cannot do this.");
    }
}
