#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  suite) bash scripts/run-device-tests.sh ;;
  upgrade) bash scripts/run-upgrade-test.sh ;;
  *) echo "Cenário de compatibilidade inválido." >&2; exit 1 ;;
esac
