"""HTTP layer: POST /api/v1/validate/{kind} and /health. No validation rules here."""
import json
import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from application.validation_service import UnknownKind, ValidationService

log = logging.getLogger("ai-validation")
VALIDATE_PREFIX = "/api/v1/validate/"
MAX_BODY_BYTES = 7_000_000


def make_handler(service: ValidationService):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path in ("/health", "/api/v1/health"):
                self._send(200, {"status": "ok", "service": "ai-validation"})
            else:
                self._send(404, {"message": "No such endpoint."})

        def do_POST(self):
            path = self.path.rstrip("/")
            if not path.startswith(VALIDATE_PREFIX):
                return self._send(404, {"message": "No such endpoint."})
            try:
                length = int(self.headers.get("Content-Length") or 0)
                if length > MAX_BODY_BYTES:
                    raise ValueError("Request body is too large.")
                body = json.loads(self.rfile.read(length) or b"{}")
                if not isinstance(body, dict):
                    raise ValueError("Request body must be a JSON object.")
                self._send(200, service.validate(path.removeprefix(VALIDATE_PREFIX), body))
            except UnknownKind:
                self._send(404, {"message": "No such endpoint."})
            except ValueError as error:  # includes json.JSONDecodeError
                self._send(422, {"valid": False, "reason": str(error)})

        def _send(self, status: int, payload: dict) -> None:
            data = json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, fmt, *args):
            log.info(fmt, *args)

    return Handler


def serve(service: ValidationService, port: int) -> None:
    log.info("listening on http://localhost:%d", port)
    ThreadingHTTPServer(("0.0.0.0", port), make_handler(service)).serve_forever()
