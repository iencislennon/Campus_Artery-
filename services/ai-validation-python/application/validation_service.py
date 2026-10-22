from domain.validators import Validator
from infrastructure.external_provider import ExternalProvider

LOCAL_ENGINE, PROVIDER_ENGINE = "local-ai-validation", "external-provider"


class UnknownKind(Exception):
    pass


class ValidationService:
    """Routes a validation kind to the external provider when configured, otherwise to the local placeholder validator."""

    def __init__(self, validators: dict[str, Validator], provider: ExternalProvider | None = None):
        self._validators, self._provider = validators, provider

    def validate(self, kind: str, body: dict) -> dict:
        if kind not in self._validators:
            raise UnknownKind(kind)
        remote = self._provider.validate(kind, body) if self._provider else None
        if remote is not None:
            return {**remote, "engine": PROVIDER_ENGINE}
        return {**self._validators[kind].validate(body).to_json(), "engine": LOCAL_ENGINE}
