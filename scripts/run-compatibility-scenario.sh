#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  suite) bash scripts/run-device-tests.sh ;;
  reset) bash scripts/run-storage-reset.sh ;;
  *) echo "Cenário de compatibilidade inválido." >&2; exit 1 ;;
esac
