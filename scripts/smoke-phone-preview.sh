#!/usr/bin/env bash
set -euo pipefail

original=com.mateuswilliam.financas
preview=com.mateuswilliam.financas.teste
hierarchy=/sdcard/finance-preview-smoke.xml
mkdir -p qa

cleanup() {
  adb shell am force-stop "$preview" >/dev/null 2>&1 || true
  adb shell rm -f "$hierarchy" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Sem -t: o APK precisa aceitar a instalação comum. O pacote original já está no emulador.
adb install ci-apks/controle-financeiro-teste.apk
adb shell pm path "$original" | grep -F 'package:'
adb shell pm path "$preview" | grep -F 'package:'
adb shell am start -W -n "$preview/$original.MainActivity" | tr -d '\r' | tee qa/phone-preview-launch.log
grep -Fx 'Status: ok' qa/phone-preview-launch.log

for attempt in 1 2 3; do
  if timeout 12s adb shell uiautomator dump "$hierarchy" >/dev/null &&
     adb exec-out cat "$hierarchy" > qa/phone-preview-hierarchy.xml &&
     grep -F 'package="com.mateuswilliam.financas.teste"' qa/phone-preview-hierarchy.xml >/dev/null &&
     grep -F 'text="Rendly"' qa/phone-preview-hierarchy.xml >/dev/null &&
     grep -F 'text="Resultado do mês"' qa/phone-preview-hierarchy.xml >/dev/null; then
    echo 'APK de teste instalado ao lado do original; planejamento carregado na primeira abertura.' | tee -a qa/phone-preview-launch.log
    exit 0
  fi
done
echo 'O planejamento do APK de teste não foi carregado.' >&2
exit 1
