"""Regressões de procedência; os textos usados aqui também são sintéticos."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("verify-bank-fixtures.py")

class ProvenanceTest(unittest.TestCase):
    def run_case(self, metadata=None, origin="REAL_ANONYMIZED", body="Pix recebido de R$ 10,00."):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "e.json").write_text(json.dumps({"formatVersion": 1, "evidence": metadata if metadata is not None else [{"id": "evidence-001", "bank": "Inter", "packageName": "fixture.local", "appVersion": "0.0-ficticia", "androidVersion": "35", "deviceModel": "Emulador fictício", "collectedOn": "2026-10-25", "redactionReviewed": True, "listenerConnected": True, "notificationDelivered": False, "reviewResult": "PENDENTE"}]}))
            (root / "f.tsv").write_text("\t".join([origin, "Inter", "case-001", "Exemplo fictício", body, "1000", "INCOME", "PIX", "OTHER", "false", "evidence-001"]) + "\n")
            return subprocess.run([sys.executable, str(SCRIPT), "--require-real", "--evidence", str(root / "e.json"), "--fixtures", str(root / "f.tsv")], text=True, capture_output=True)
    def test_complete_review_metadata_is_accepted_without_claiming_delivery(self):
        self.assertEqual(0, self.run_case().returncode)
    def test_synthetic_input_cannot_be_presented_as_real(self):
        self.assertNotEqual(0, self.run_case(origin="SYNTHETIC").returncode)
    def test_missing_evidence_is_rejected(self):
        self.assertNotEqual(0, self.run_case(metadata=[]).returncode)
    def test_possible_personal_data_is_rejected_without_echoing_it(self):
        result = self.run_case(body="email-ficticio@example.com")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("email-ficticio@example.com", result.stderr + result.stdout)
    def test_repeated_evidence_identifiers_are_rejected(self):
        self.assertNotEqual(0, self.run_case(metadata=[{"id": "same-id"}, {"id": "same-id"}]).returncode)

if __name__ == "__main__":
    unittest.main()
