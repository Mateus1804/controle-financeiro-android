#!/usr/bin/env bash
set -euo pipefail

mkdir -p qa
adb shell wm size 720x1600
adb shell wm density 320
adb install -r -t ci-apks/app-debug.apk
adb install -r -t ci-apks/app-debug-androidTest.apk
adb shell am instrument -w -r com.mateuswilliam.financas.test/com.mateuswilliam.financas.IsolatedAndroidTestRunner | tee qa/instrumented.log
adb pull /sdcard/Android/data/com.mateuswilliam.financas/files/screenshots qa/screenshots || true
python3 - <<'PY'
from pathlib import Path
import re

report = Path("qa/instrumented.log").read_text()
match = re.search(r"OK \((\d+) tests?\)", report)
if not match or any(value in report for value in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")):
    raise SystemExit("Os testes instrumentados não concluíram com sucesso. Consulte qa/instrumented.log.")
print(f"Testes instrumentados aprovados: {match.group(1)}.")
PY
