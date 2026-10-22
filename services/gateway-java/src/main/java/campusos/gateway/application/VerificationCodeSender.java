package campusos.gateway.application;

/** Delivers a one-time login code to a student's mailbox. Throws ApiException when it cannot be delivered. */
public interface VerificationCodeSender {
    void send(String email, String code);
}
