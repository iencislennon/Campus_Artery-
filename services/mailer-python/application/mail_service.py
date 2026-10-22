import logging
from typing import Protocol

from domain.messages import Mail, verification_mail

log = logging.getLogger("mailer")


class MailSender(Protocol):
    def send(self, mail: Mail) -> None: ...


class MailService:
    def __init__(self, sender: MailSender):
        self._sender = sender

    def send_verification_code(self, email: str, code: str) -> None:
        mail = verification_mail(email, code)
        self._sender.send(mail)
        log.info("verification code sent to %s", mail.to)  # never log the code itself
