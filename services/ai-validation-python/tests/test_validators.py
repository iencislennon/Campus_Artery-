import base64
import unittest

from application.validation_service import UnknownKind, ValidationService
from domain.validators import DocumentValidator, IssueReportValidator


def document(content: bytes, mime="text/plain", name="doc.txt") -> dict:
    return {"filename": name, "mimeType": mime, "contentBase64": base64.b64encode(content).decode()}


class DocumentValidatorTest(unittest.TestCase):
    def setUp(self):
        self.validator = DocumentValidator()

    def test_accepts_plain_text(self):
        self.assertTrue(self.validator.validate(document(b"charter")).valid)

    def test_rejects_fake_pdf(self):
        self.assertFalse(self.validator.validate(document(b"not a pdf", "application/pdf", "a.pdf")).valid)

    def test_rejects_unsupported_type(self):
        self.assertFalse(self.validator.validate(document(b"x", "application/zip", "a.zip")).valid)

    def test_rejects_corrupt_base64(self):
        self.assertFalse(self.validator.validate({"filename": "a.txt", "mimeType": "text/plain", "contentBase64": "%%%"}).valid)


class IssueReportValidatorTest(unittest.TestCase):
    def setUp(self):
        self.validator = IssueReportValidator(DocumentValidator())

    def test_rejects_short_description(self):
        self.assertFalse(self.validator.validate({"description": "broken"}).valid)

    def test_accepts_plausible_issue(self):
        self.assertTrue(self.validator.validate({"description": "The projector in G205 is not working at all"}).valid)

    def test_rejects_bad_evidence(self):
        body = {"description": "The projector in G205 is not working at all", "attachment": document(b"x", "image/png", "a.png")}
        self.assertFalse(self.validator.validate(body).valid)


class ValidationServiceTest(unittest.TestCase):
    def test_marks_local_engine_and_rejects_unknown_kind(self):
        service = ValidationService({"club-document": DocumentValidator()})
        self.assertEqual(service.validate("club-document", document(b"ok"))["engine"], "local-ai-validation")
        with self.assertRaises(UnknownKind):
            service.validate("nope", {})


if __name__ == "__main__":
    unittest.main()
