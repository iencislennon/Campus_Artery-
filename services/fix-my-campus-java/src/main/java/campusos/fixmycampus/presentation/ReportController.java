package campusos.fixmycampus.presentation;

import campusos.common.ApiException;
import campusos.common.Request;
import campusos.common.Response;
import campusos.fixmycampus.application.ReportService;
import campusos.fixmycampus.domain.Report;
import campusos.fixmycampus.domain.ReportStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) { this.service = service; }

    public Response categories(Request request) { return Response.ok(service.categories()); }

    public Response list(Request request) {
        return Response.ok(service.list(request.user(), "all".equals(request.queryParam("scope"))).stream().map(ReportController::view).toList());
    }

    public Response notifications(Request request) { return Response.ok(service.notifications(request.user())); }

    public Response create(Request request) {
        Report saved = service.submit(request.user(), request.bearer(), request.bodyString("roomId"),
                request.bodyString("category"), request.bodyString("description"), attachment(request));
        Map<String, Object> body = view(saved);
        if (saved.duplicateOf() != null) body.put("warning", "A similar report (" + saved.duplicateOf() + ") is already open for " + saved.roomId() + ".");
        return Response.created(body);
    }

    private static Map<String, Object> attachment(campusos.common.Request request) {
        Object value = request.bodyMap().get("attachment");
        return value instanceof Map<?, ?> map ? castMap(map) : null;
    }

    private static Map<String, Object> castMap(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    public Response setStatus(Request request) {
        ReportStatus status;
        try {
            status = ReportStatus.valueOf(request.requiredBody("status"));
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("Status must be OPEN, IN_PROGRESS or RESOLVED.");
        }
        return Response.ok(view(service.updateStatus(request.user(), request.param("id"), status)));
    }

    private static Map<String, Object> view(Report r) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", r.id());
        view.put("roomId", r.roomId());
        view.put("category", r.category());
        view.put("description", r.description());
        view.put("status", r.status().name());
        view.put("createdAt", r.createdAt());
        view.put("duplicateOf", r.duplicateOf());
        view.put("assigneeId", r.assigneeId());
        return view;
    }
}
