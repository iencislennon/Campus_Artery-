import unittest

from application.mail_service import MailService
from domain.messages import InvalidRecipient, verification_mail


class RecordingSender:
    def __init__(self):
        self.sent = []

    def send(self, mail):
        self.sent.append(mail)


class MailerTest(unittest.TestCase):
    def test_builds_mail_with_code(self):
        mail = verification_mail("Student@SDU.edu.kz", "123456")
        self.assertEqual(mail.to, "student@sdu.edu.kz")
        self.assertIn("123456", mail.body)

    def test_rejects_non_sdu_and_header_injection(self):
        for bad in ("a@gmail.com", "a@sdu.edu.kz\nBcc: x@y.z", "a@sdu.edu.kz, b@sdu.edu.kz"):
            with self.assertRaises(InvalidRecipient):
                verification_mail(bad, "123456")

    def test_rejects_malformed_code(self):
        with self.assertRaises(InvalidRecipient):
            verification_mail("a@sdu.edu.kz", "12ab56")

    def test_service_hands_mail_to_sender(self):
        sender = RecordingSender()
        MailService(sender).send_verification_code("a@sdu.edu.kz", "654321")
        self.assertEqual(len(sender.sent), 1)


if __name__ == "__main__":
    unittest.main()
