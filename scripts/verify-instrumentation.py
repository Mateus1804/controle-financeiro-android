"""Recusa falhas, interrupções, testes ignorados e execuções vazias no emulador dedicado."""
from pathlib import Path
import re
import sys

report = Path(sys.argv[1]).read_text()
match = re.search(r"OK \((\d+) tests?\)", report)
if not match or int(match.group(1)) == 0 or any(value in report for value in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "INSTRUMENTATION_STATUS_CODE: -3", "INSTRUMENTATION_STATUS_CODE: -4")):
    raise SystemExit("A execução não concluiu todos os testes. Consulte o relatório do emulador.")
print(f"Testes instrumentados aprovados: {match.group(1)}; nenhum teste ignorado.")
