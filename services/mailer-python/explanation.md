# Mailer (Python) :8085

**Owns:** nothing persistent (no database, so no UML). Sends the one-time login codes to students' `@sdu.edu.kz` mailboxes over SMTP.
Standard library only (`smtplib`). Listens on `127.0.0.1`, so it is reachable only from this machine.

## Flow
1. The student asks for a code: `POST /api/v1/auth/request-code` on the gateway.
2. The gateway generates a 6-digit code, stores only its PBKDF2 hash (valid 10 minutes, one request per minute per email) and calls `VerificationCodeSender`.
3. `MailerCodeSender` calls `POST /api/v1/send-code {email, code}` here with the `X-Internal-Key` header (= `CAMPUSOS_SECRET`).
4. The mailer validates the recipient, builds the message and sends it by SMTP. The code is never written to the logs.

Errors the gateway sees: 503 SMTP not configured, 502 mail server refused or unreachable, 422 bad recipient, 401 wrong internal key.

## Layers
```
presentation/server.py             HTTP and internal-key check
application/mail_service.py        MailSender port, builds then sends
domain/messages.py                 Mail, recipient rules (only @sdu.edu.kz, no header injection)
infrastructure/smtp_sender.py      smtplib (starttls | ssl | none)
```

## Configuration (.env)
| Variable | Meaning |
|---|---|
| `SMTP_HOST`, `SMTP_PORT` | mail server; port defaults to 587 (starttls), 465 (ssl), 25 (none) |
| `SMTP_SECURITY` | `starttls` (default), `ssl` or `none` |
| `SMTP_USER`, `SMTP_PASSWORD` | login; use an app password |
| `SMTP_FROM` | sender shown to the student, e.g. `SDU Bağdar <no-reply@sdu.edu.kz>` |

Gateway side: `SDU_BAGDAR_SKIP_VERIFICATION=false` turns codes on; `MAIL_DELIVERY=console` prints codes in `build/logs/gateway.log` instead of emailing (local development).

Run tests: `python3 -m unittest discover tests`.
