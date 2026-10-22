import base64
import hashlib
import hmac
import json
import os
import time

from domain.errors import unauthorized
from domain.models import AuthUser


def _b64d(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def verify_token(token: str | None) -> AuthUser:
    """Checks the HS256 token issued by the gateway (same format as the Java services)."""
    secret = os.environ.get("CAMPUSOS_SECRET")
    if not secret:
        raise RuntimeError("Missing environment variable CAMPUSOS_SECRET")
    if not token or token.count(".") != 1:
        raise unauthorized("Missing or malformed bearer token.")
    body, signature = token.split(".")
    expected = hmac.new(secret.encode(), body.encode(), hashlib.sha256).digest()
    try:
        signature_ok = hmac.compare_digest(expected, _b64d(signature))
        payload = json.loads(_b64d(body)) if signature_ok else None
        if payload is not None and payload["exp"] < time.time():
            raise unauthorized("Token expired. Sign in again.")
        return AuthUser(payload["sub"], payload["name"], payload["role"]) if payload else _reject()
    except (ValueError, KeyError, TypeError):
        raise unauthorized("Malformed token.") from None


def _reject():
    raise unauthorized("Invalid token signature.")
