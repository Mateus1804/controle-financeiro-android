#!/usr/bin/env bash
set -euo pipefail
mkdir -p qa
package=com.mateuswilliam.financas
python3 scripts/create-legacy-fixture.py qa/upgrade-schema3.db
adb install -r -t ci-apks/legacy-debug.apk
adb shell am force-stop "$package"
adb push qa/upgrade-schema3.db /data/local/tmp/upgrade-schema3.db
adb shell run-as "$package" mkdir -p databases shared_prefs
adb shell run-as "$package" cp /data/local/tmp/upgrade-schema3.db databases/financas.db
cat > qa/upgrade-preferences.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map><string name="appearance">DARK</string><boolean name="hide_amounts" value="true" /><boolean name="intro_seen" value="true" /><boolean name="enabled" value="false" /></map>
XML
adb push qa/upgrade-preferences.xml /data/local/tmp/upgrade-preferences.xml
adb shell run-as "$package" cp /data/local/tmp/upgrade-preferences.xml shared_prefs/capture_settings.xml
# Não há desinstalação nem limpeza: o Package Manager confere a assinatura da atualização.
adb install -r -t ci-apks/app-debug.apk
adb install -r -t ci-apks/app-debug-androidTest.apk
adb shell am instrument -w -r -e installedUpgrade true -e class com.mateuswilliam.financas.data.InstalledUpdateTest "$package.test/com.mateuswilliam.financas.IsolatedAndroidTestRunner" | tee qa/installed-upgrade.log
python3 scripts/verify-instrumentation.py qa/installed-upgrade.log
adb shell run-as "$package" ls databases/financas.db > qa/preserved-database.txt
