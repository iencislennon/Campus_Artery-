import json
import logging
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

log = logging.getLogger("ai-validation")


class ExternalProvider:
    """Optional real AI backend: POST {base}/validate/{kind}. Returns None on any failure so the caller can fall back."""

    def __init__(self, base_url: str, timeout_seconds: float = 8):
        self._base_url, self._timeout = base_url.rstrip("/"), timeout_seconds

    def validate(self, kind: str, body: dict) -> dict | None:
        request = Request(f"{self._base_url}/validate/{kind}", data=json.dumps(body).encode(),
                          headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urlopen(request, timeout=self._timeout) as response:
                result = json.loads(response.read())
        except (URLError, HTTPError, TimeoutError, ValueError):
            log.warning("external AI provider failed; using local validation")
            return None
        return result if isinstance(result, dict) and "valid" in result else None
