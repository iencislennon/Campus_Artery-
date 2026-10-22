from dataclasses import dataclass

SDU_DOMAIN = "@sdu.edu.kz"
CODE_VALID_MINUTES = 10


class InvalidRecipient(ValueError):
    pass


@dataclass(frozen=True)
class Mail:
    to: str
    subject: str
    body: str


def verification_mail(email: str, code: str) -> Mail:
    recipient = (email or "").strip().lower()
    if not recipient.endswith(SDU_DOMAIN) or recipient.count("@") != 1 or any(c in recipient for c in " \r\n,;<>"):
        raise InvalidRecipient("Only @sdu.edu.kz addresses can receive codes.")
    if not (code or "").isdigit() or len(code) != 6:
        raise InvalidRecipient("The verification code must be 6 digits.")
    body = (f"Your SDU Bağdar verification code is {code}.\n\n"
            f"It is valid for {CODE_VALID_MINUTES} minutes. If you did not try to sign in, ignore this email.\n")
    return Mail(recipient, f"SDU Bağdar verification code: {code}", body)
