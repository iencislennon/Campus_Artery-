package campusos.gateway.infrastructure;

import campusos.common.ApiException;
import campusos.common.Json;
import campusos.common.ServiceClient;
import campusos.gateway.application.VerificationCodeSender;
import java.net.http.HttpResponse;
import java.util.Map;

/** Sends codes through the Mailer microservice, which talks SMTP. */
public final class MailerCodeSender implements VerificationCodeSender {
    private final ServiceClient mailer;
    private final String internalKey;

    public MailerCodeSender(ServiceClient mailer, String internalKey) {
        this.mailer = mailer;
        this.internalKey = internalKey;
    }

    @Override
    public void send(String email, String code) {
        HttpResponse<String> response = mailer.send("POST", "/send-code", null, Json.stringify(Map.of("email", email, "code", code)),
                Map.of("X-Internal-Key", internalKey));
        if (response.statusCode() == 200) return;
        if (response.statusCode() == 503) throw ApiException.unavailable("Email delivery is not configured on this server.");
        throw ApiException.unavailable("The verification email could not be sent. Try again shortly.");
    }
}
