#!/usr/bin/env bash
set -euo pipefail
mkdir -p qa
package=com.mateuswilliam.financas
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 35 ]]
adb install -r -t ci-apks/previous-debug.apk
adb shell run-as "$package" id -u | tr -d '\r' > qa/uid-before-update.txt
adb shell am force-stop "$package"
# O conteúdo anterior é intencionalmente incompatível: não deve ser lido nem convertido.
python3 - <<'PY'
import sqlite3
from pathlib import Path
p=Path('qa/old-storage.db')
p.unlink(missing_ok=True)
with sqlite3.connect(p) as db:
    db.execute('CREATE TABLE previous_data (description TEXT)')
    db.execute("INSERT INTO previous_data VALUES ('Dados fictícios autorizados para descarte')")
    db.execute('PRAGMA user_version=10')
PY
adb push qa/old-storage.db /data/local/tmp/old-storage.db
adb shell run-as "$package" mkdir -p databases shared_prefs
adb shell run-as "$package" cp /data/local/tmp/old-storage.db databases/financas.db
cat > qa/reset-preferences.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="appearance">DARK</string><boolean name="hide_amounts" value="true" /><boolean name="intro_seen" value="true" /><boolean name="enabled" value="false" /></map>
XML
adb push qa/reset-preferences.xml /data/local/tmp/reset-preferences.xml
adb shell run-as "$package" cp /data/local/tmp/reset-preferences.xml shared_prefs/capture_settings.xml
# Atualiza a instalação existente, sem desinstalar nem limpar os dados.
adb install -r -t ci-apks/app-debug.apk
adb shell run-as "$package" id -u | tr -d '\r' > qa/uid-after-update.txt
cmp qa/uid-before-update.txt qa/uid-after-update.txt
adb install -r -t ci-apks/app-debug-androidTest.apk
timeout --foreground 8m adb shell am instrument -w -r -e intentionalReset true -e class com.mateuswilliam.financas.data.InstalledResetTest "$package.test/com.mateuswilliam.financas.IsolatedAndroidTestRunner" | tee qa/intentional-reset.log
python3 scripts/verify-instrumentation.py qa/intentional-reset.log
