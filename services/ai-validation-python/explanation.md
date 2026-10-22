# AI Validation (Python) :8084

**Owns:** nothing persistent. It is a stateless service with no database, so there is no UML diagram to draw.
**Role:** placeholder "AI" that other services call before accepting user input. Standard library only.

## Who calls it
| Caller | Endpoint | Purpose |
|---|---|---|
| ProjectForge (`ClubService.create` via `AiValidationClient`) | `POST /api/v1/validate/club-document` | club registration document |
| FixMyCampus (`AiValidationClient`) | `POST /api/v1/validate/fixmycampus` | issue description and optional evidence file |

Request: the same JSON the user submitted. Response: `{valid, confidence, reason, checks?, engine}`. `GET /health` for liveness.
Callers treat a timeout or an unreachable service as 503, never as "valid".

## Layers
```
presentation/server.py                 HTTP only
application/validation_service.py      picks provider or local validator
domain/validators.py + verdict.py      the placeholder rules (Validator port)
infrastructure/external_provider.py    optional real AI over HTTP
main.py                                composition root
```

## Placeholder logic (what the "AI" really checks today)
- **Document:** present, type in PDF/PNG/JPG/WEBP/TXT, valid base64, non-empty and at most 5 MB, correct PDF or image signature.
- **Issue report:** 10 to 1000 characters, mentions an issue keyword or has at least 5 words, evidence file passes the document check.

## Replacing it with a real model
1. Set `AI_PROVIDER_URL`: the service forwards to `{url}/validate/{kind}` and reports `engine: external-provider`. On failure it falls back to the local rules.
2. Or add a new class implementing `Validator` and register it in `main.py`. Nothing outside this folder changes.

## Run and test
`python3 main.py` (port from `AI_VALIDATION_PORT`) and `python3 -m unittest discover tests`.
