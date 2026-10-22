"""HTTP layer: routing, bearer auth, JSON parsing and error mapping. No SQL and no business rules here."""
import json
import logging
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from application.survey_service import SurveyService
from application.services import ClubService, InitiativeService, ProfileService
from domain.errors import ApiError, invalid, not_found
from infrastructure.tokens import verify_token

log = logging.getLogger("project-forge")
API = "/api/v1"
MAX_BODY_BYTES = 8_000_000


class Routes:
    """Maps (method, path) to a callable(user, query, body) -> (status, payload)."""

    def __init__(self, initiatives: InitiativeService, clubs: ClubService, profiles: ProfileService, surveys: SurveyService):
        self._table = {
            ("GET", "/initiatives"): lambda u, q, b: (200, [i.to_json() for i in initiatives.search(u, q("q"), q("category"))]),
            ("POST", "/initiatives"): lambda u, q, b: (201, initiatives.create(u, b.get("title"), b.get("category"), b.get("summary"), b.get("contact")).to_json()),
            ("POST", "/initiatives/follow"): lambda u, q, b: (200, initiatives.toggle_follow(u, b.get("id")).to_json()),
            ("GET", "/recommendations"): lambda u, q, b: (200, [i.to_json() for i in initiatives.recommend(u, q("strategy"))]),
            ("GET", "/clubs"): lambda u, q, b: (200, [c.to_json() for c in clubs.list(u)]),
            ("POST", "/clubs"): lambda u, q, b: (201, clubs.create(u, b.get("name"), b.get("deadlineTitle"), b.get("deadlineDate"), b.get("document"))),
            ("POST", "/clubs/toggle"): lambda u, q, b: (200, clubs.toggle(u, b.get("id")).to_json()),
            ("GET", "/clubs/manage"): lambda u, q, b: (200, surveys.club_page(u, q("id"))),
            ("PUT", "/clubs/manage"): lambda u, q, b: (200, surveys.update_event(u, b.get("id"), b)),
            ("GET", "/surveys"): lambda u, q, b: (200, surveys.get(u, q("id"))),
            ("POST", "/surveys"): lambda u, q, b: (201, surveys.create(u, b)),
            ("PUT", "/surveys"): lambda u, q, b: (200, surveys.update(u, b)),
            ("POST", "/surveys/delete"): lambda u, q, b: (200, surveys.delete(u, b.get("id"))),
            ("POST", "/surveys/respond"): lambda u, q, b: (200, surveys.respond(u, b.get("id"), b.get("answers"))),
            ("GET", "/surveys/results"): lambda u, q, b: (200, surveys.results(u, q("id"))),
            ("GET", "/surveys/file"): lambda u, q, b: (200, surveys.answer_file(u, q("id"), q("question"), int(q("n") or -1))),
            ("GET", "/profile"): lambda u, q, b: (200, profiles.overview(u)),
            ("PUT", "/profile"): lambda u, q, b: (200, profiles.update(u, b)),
        }

    def find(self, method: str, path: str):
        route = self._table.get((method, path))
        if route is None:
            known_path = any(p == path for _, p in self._table)
            raise ApiError(405, "METHOD_NOT_ALLOWED", "Method not allowed.") if known_path else not_found(f"No such endpoint: {path}")
        return route


def make_handler(routes: Routes, allowed_origin: str):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self): self._dispatch("GET")
        def do_POST(self): self._dispatch("POST")
        def do_PUT(self): self._dispatch("PUT")

        def do_OPTIONS(self):
            self._send(204, None)

        def _dispatch(self, method: str) -> None:
            started = time.perf_counter()
            url = urlparse(self.path)
            try:
                status, payload = self._handle(method, url)
            except ApiError as error:
                status, payload = error.status, {"message": error.message, "code": error.code}
            except Exception:
                log.exception("unhandled error")
                status, payload = 500, {"message": "Internal error. Try again later.", "code": "INTERNAL"}
            self._send(status, payload)
            log.info("%s %s -> %d (%d ms)", method, url.path, status, (time.perf_counter() - started) * 1000)

        def _handle(self, method: str, url):
            path = url.path
            if path in ("/health", f"{API}/health"):
                return 200, {"status": "ok", "service": "project-forge"}
            if not path.startswith(API + "/"):
                raise not_found(f"No such endpoint: {path}")
            route = routes.find(method, path.removeprefix(API))
            header = self.headers.get("Authorization", "")
            user = verify_token(header.removeprefix("Bearer ").strip() if header.startswith("Bearer ") else None)
            query = parse_qs(url.query)
            return route(user, lambda key: query.get(key, [""])[0], self._read_body())

        def _read_body(self) -> dict:
            length = int(self.headers.get("Content-Length") or 0)
            if length > MAX_BODY_BYTES:
                raise ApiError(413, "PAYLOAD_TOO_LARGE", "Request body is too large.")
            if length == 0:
                return {}
            try:
                body = json.loads(self.rfile.read(length))
            except json.JSONDecodeError:
                raise invalid("Request body must be valid JSON.") from None
            if not isinstance(body, dict):
                raise invalid("Request body must be a JSON object.")
            return body

        def _send(self, status: int, payload) -> None:
            data = b"" if payload is None else json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", allowed_origin)
            self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, *args):  # request logging is done in _dispatch
            pass

    return Handler


def serve(routes: Routes, port: int, allowed_origin: str) -> None:
    server = ThreadingHTTPServer(("0.0.0.0", port), make_handler(routes, allowed_origin))
    log.info("listening on http://localhost:%d", port)
    server.serve_forever()
