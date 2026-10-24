"""Confiro o wrapper distribuído pelo Gradle 8.13 antes da execução em CI."""
import hashlib
from pathlib import Path

wrapper = Path(__file__).resolve().parents[1] / "gradle/wrapper/gradle-wrapper.jar"
expected = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
actual = hashlib.sha256(wrapper.read_bytes()).hexdigest()
if actual != expected:
    raise SystemExit("O Gradle Wrapper não corresponde ao arquivo verificado da versão 8.13.")
print("Gradle Wrapper 8.13: SHA-256 conferido.")
