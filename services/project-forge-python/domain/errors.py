class ApiError(Exception):
    """An error the caller can act on; mapped to an HTTP status and a JSON {message, code} body."""

    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status, self.code, self.message = status, code, message


def invalid(message: str) -> ApiError:
    return ApiError(422, "VALIDATION_FAILED", message)


def not_found(message: str) -> ApiError:
    return ApiError(404, "NOT_FOUND", message)


def conflict(message: str) -> ApiError:
    return ApiError(409, "CONFLICT", message)


def unauthorized(message: str) -> ApiError:
    return ApiError(401, "UNAUTHORIZED", message)
