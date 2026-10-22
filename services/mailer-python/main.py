"""Composition root for the mailer service: real SMTP delivery of verification codes."""
import logging
import os

from application.mail_service import MailService
from infrastructure.smtp_sender import SmtpSender, SmtpSettings
from presentation.server import serve


def build_service() -> MailService:
    env = os.environ.get
    security = env("SMTP_SECURITY", "starttls").lower()
    default_port = {"ssl": 465, "starttls": 587, "none": 25}.get(security, 587)
    return MailService(SmtpSender(SmtpSettings(
        host=env("SMTP_HOST", "").strip(), port=int(env("SMTP_PORT") or default_port),
        user=env("SMTP_USER", "").strip(), password=env("SMTP_PASSWORD", ""),
        sender=env("SMTP_FROM", "").strip(), security=security)))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="[mailer] %(message)s")
    serve(build_service(), os.environ.get("CAMPUSOS_SECRET", ""), "127.0.0.1", int(os.environ.get("MAILER_PORT", "8085")))
