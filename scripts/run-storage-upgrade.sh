#!/usr/bin/env bash
set -euo pipefail

mkdir -p qa
package=com.mateuswilliam.financas
test_class="$package.data.InstalledUpgradeTest"
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 35 ]]

run_test() {
  local phase=$1
  local method=$2
  local report="qa/rendly-upgrade-$phase.log"
  timeout --foreground 8m adb shell am instrument -w -r -e installedUpgrade "$phase" \
    -e class "$test_class#$method" "$package.test/$package.IsolatedAndroidTestRunner" | tee "$report"
  python3 scripts/verify-instrumentation.py "$report"
}

adb install -r -t ci-apks/previous-rendly-debug.apk
adb install -r -t ci-apks/app-debug-androidTest.apk
run_test seed seedRendlyV1
adb shell am force-stop "$package"
adb shell run-as "$package" id -u | tr -d '\r' > qa/uid-before-upgrade.txt
adb install -r -t ci-apks/app-debug.apk
adb shell run-as "$package" id -u | tr -d '\r' > qa/uid-after-upgrade.txt
cmp qa/uid-before-upgrade.txt qa/uid-after-upgrade.txt
run_test verify preservesRendlyV1Data
