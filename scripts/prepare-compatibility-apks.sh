#!/usr/bin/env bash
set -euo pipefail

signer="$ANDROID_HOME/build-tools/36.0.0/apksigner"
mkdir -p ci-apks
sign_apk() {
  "$signer" sign --ks "$ANDROID_KEYSTORE_PATH" --ks-key-alias "$ANDROID_KEY_ALIAS" \
    --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEY_PASSWORD --out "$2" "$1"
  "$signer" verify --verbose "$2"
}
sign_apk ci-legacy/app/build/outputs/apk/debug/app-debug.apk ci-apks/legacy-debug.apk
sign_apk app/build/outputs/apk/debug/app-debug.apk ci-apks/app-debug.apk
sign_apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk ci-apks/app-debug-androidTest.apk
# A versão release usa o mesmo caminho Gradle que será usado com a chave definitiva.
"$signer" verify --verbose app/build/outputs/apk/release/app-release.apk
python3 - <<'PYTHON'
import hashlib
import json
from pathlib import Path
paths = sorted(Path("ci-apks").glob("*.apk"))
Path("ci-apks/checksums.json").write_text(json.dumps({p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}, indent=2) + "\n")
PYTHON
