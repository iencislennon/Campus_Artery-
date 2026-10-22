"""HTTP layer: POST /api/v1/send-code (internal key required) and /health."""
import hmac
import json
import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from application.mail_service import MailService
from domain.messages import InvalidRecipient
from infrastructure.smtp_sender import DeliveryFailed, NotConfigured

log = logging.getLogger("mailer")
MAX_BODY_BYTES = 4_000


def make_handler(service: MailService, internal_key: str):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path in ("/health", "/api/v1/health"):
                self._send(200, {"status": "ok", "service": "mailer"})
            else:
                self._send(404, {"message": "No such endpoint."})

        def do_POST(self):
            if self.path.rstrip("/") != "/api/v1/send-code":
                return self._send(404, {"message": "No such endpoint."})
            if not internal_key or not hmac.compare_digest(self.headers.get("X-Internal-Key", ""), internal_key):
                return self._send(401, {"message": "Internal key required."})
            try:
                length = int(self.headers.get("Content-Length") or 0)
                if length > MAX_BODY_BYTES:
                    raise ValueError("Request body is too large.")
                body = json.loads(self.rfile.read(length) or b"{}")
                service.send_verification_code(str(body.get("email", "")), str(body.get("code", "")))
                self._send(200, {"sent": True})
            except (ValueError, InvalidRecipient) as error:  # includes JSON errors
                self._send(422, {"message": str(error)})
            except NotConfigured as error:
                self._send(503, {"message": str(error)})
            except DeliveryFailed as error:
                log.error("delivery failed: %s", error)
                self._send(502, {"message": "The mail server rejected or could not send the message."})

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


def serve(service: MailService, internal_key: str, host: str, port: int) -> None:
    log.info("listening on http://%s:%d", host, port)
    ThreadingHTTPServer((host, port), make_handler(service, internal_key)).serve_forever()
