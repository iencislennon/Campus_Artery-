package campusos.gateway.infrastructure;

import campusos.gateway.application.VerificationCodeSender;

/** Development only: prints the code to the gateway log instead of emailing it. */
public final class ConsoleCodeSender implements VerificationCodeSender {
    @Override
    public void send(String email, String code) {
        System.out.printf("[auth] verification code for %s: %s%n", email, code);
    }
}
