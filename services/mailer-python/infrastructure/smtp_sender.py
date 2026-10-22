import smtplib
import ssl
from dataclasses import dataclass
from email.message import EmailMessage

from domain.messages import Mail


class DeliveryFailed(Exception):
    pass


class NotConfigured(Exception):
    pass


@dataclass(frozen=True)
class SmtpSettings:
    host: str
    port: int
    user: str
    password: str
    sender: str
    security: str  # starttls | ssl | none
    timeout_seconds: float = 10


class SmtpSender:
    def __init__(self, settings: SmtpSettings):
        self._s = settings

    def send(self, mail: Mail) -> None:
        if not self._s.host or not self._s.sender:
            raise NotConfigured("SMTP is not configured. Set SMTP_HOST and SMTP_FROM.")
        message = EmailMessage()
        message["From"], message["To"], message["Subject"] = self._s.sender, mail.to, mail.subject
        message.set_content(mail.body)
        try:
            with self._connect() as server:
                if self._s.user:
                    server.login(self._s.user, self._s.password)
                server.send_message(message)
        except (smtplib.SMTPException, OSError) as error:
            raise DeliveryFailed(str(error)) from error

    def _connect(self) -> smtplib.SMTP:
        context = ssl.create_default_context()
        if self._s.security == "ssl":
            return smtplib.SMTP_SSL(self._s.host, self._s.port, timeout=self._s.timeout_seconds, context=context)
        server = smtplib.SMTP(self._s.host, self._s.port, timeout=self._s.timeout_seconds)
        if self._s.security == "starttls":
            server.starttls(context=context)
        return server
