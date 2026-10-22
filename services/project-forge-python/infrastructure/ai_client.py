import json
import os
from urllib.error import URLError, HTTPError
from urllib.request import Request, urlopen

from domain.errors import ApiError


class AiValidationClient:
    def __init__(self, base_url: str):
        self._base_url = base_url.rstrip("/")

    def validate_club_document(self, document: dict) -> dict:
        return self._post("/api/v1/validate/club-document", document)

    def _post(self, path: str, body: dict) -> dict:
        request = Request(self._base_url + path, data=json.dumps(body).encode(),
                          headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=float(os.environ.get("AI_VALIDATION_TIMEOUT_SECONDS", "8"))) as response:
                result = json.loads(response.read())
        except (URLError, HTTPError, TimeoutError) as error:
            raise ApiError(503, "AI_SERVICE_UNAVAILABLE", "AI document verification is temporarily unavailable.") from error
        if not isinstance(result, dict):
            raise ApiError(503, "AI_SERVICE_INVALID", "AI verification returned an invalid response.")
        return result
