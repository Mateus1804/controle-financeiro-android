#!/usr/bin/env bash
set -euo pipefail

mkdir -p qa
adb shell wm size 720x1600
adb shell wm density 320
adb install -r -t ci-apks/app-debug.apk
adb install -r -t ci-apks/app-debug-androidTest.apk
adb shell am instrument -w -r com.mateuswilliam.financas.test/com.mateuswilliam.financas.IsolatedAndroidTestRunner | tee qa/instrumented.log
adb pull /sdcard/Android/data/com.mateuswilliam.financas/files/screenshots qa/screenshots || true
python3 scripts/verify-instrumentation.py qa/instrumented.log
