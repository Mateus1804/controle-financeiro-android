"""Cria chave descartável só no runner. Não serve para distribuir atualizações definitivas."""
import os
from pathlib import Path
import secrets
import subprocess

runner = Path(os.environ["RUNNER_TEMP"])
keystore = runner / "compatibility-signing.jks"
if keystore.exists():
    raise SystemExit("O keystore temporário já existe; nenhuma chave foi substituída.")
password = secrets.token_hex(32)
values = {"ANDROID_KEYSTORE_PATH": str(keystore), "ANDROID_KEYSTORE_PASSWORD": password, "ANDROID_KEY_ALIAS": "compatibility-ci", "ANDROID_KEY_PASSWORD": password}
print(f"::add-mask::{password}")
environment = dict(os.environ, **values)
subprocess.run(["keytool", "-genkeypair", "-noprompt", "-storetype", "JKS", "-keystore", str(keystore), "-storepass:env", "ANDROID_KEYSTORE_PASSWORD", "-keypass:env", "ANDROID_KEY_PASSWORD", "-alias", values["ANDROID_KEY_ALIAS"], "-keyalg", "RSA", "-keysize", "3072", "-validity", "2", "-dname", "CN=Compatibilidade CI, O=Dados ficticios, C=BR"], env=environment, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
keystore.chmod(0o600)
with open(os.environ["GITHUB_ENV"], "a") as output:
    for key, value in values.items():
        output.write(f"{key}={value}\n")
print("Chave descartável de compatibilidade preparada; a chave não integra os artefatos.")
