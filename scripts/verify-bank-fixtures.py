"""Conferência de procedência e metadados dos casos bancários; não homologa formatos reais."""
from pathlib import Path
import argparse
import json
import re

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--fixtures", type=Path, default=root / "core/src/test/resources/bank-notifications.tsv")
parser.add_argument("--evidence", type=Path, default=root / "docs/evidencias-bancarias.json")
parser.add_argument("--require-real", action="store_true", help="Recusa casos sintéticos na avaliação privada.")
args = parser.parse_args()
metadata = json.loads(args.evidence.read_text())
if metadata.get("formatVersion") != 1:
    raise SystemExit("Versão de metadados não suportada.")
if not isinstance(metadata.get("evidence"), list):
    raise SystemExit("A lista de evidências está ausente.")
evidence = {}
for item in metadata["evidence"]:
    identifier = item.get("id")
    if not isinstance(identifier, str) or not re.fullmatch(r"[a-z0-9-]{3,80}", identifier) or identifier in evidence:
        raise SystemExit("Identificador de evidência inválido ou repetido.")
    evidence[identifier] = item
seen = set()
counts = {"SYNTHETIC": 0, "REAL_ANONYMIZED": 0}
for number, line in enumerate(args.fixtures.read_text().splitlines(), 1):
    if not line.strip() or line.startswith("#"):
        continue
    fields = line.split("\t")
    if len(fields) != 11:
        raise SystemExit(f"Linha {number}: são esperadas 11 colunas separadas por tabulação.")
    origin, bank, case, title, body, cents, movement, method, category, sensitive, reference = fields
    if origin not in counts or bank not in {"Inter", "Nubank", "PicPay", "Mercado Pago", "PagBank"} or case in seen:
        raise SystemExit(f"Linha {number}: origem, banco ou identificador inválido/repetido.")
    if not re.fullmatch(r"[a-z0-9-]{3,80}", case) or sensitive not in {"true", "false"}:
        raise SystemExit(f"Linha {number}: identificador ou indicação de conteúdo sensível inválido.")
    if cents != "null" and (not cents.isascii() or not cents.isdigit() or not 0 <= int(cents) <= 1_000_000_000_000):
        raise SystemExit(f"Linha {number}: valor esperado inválido.")
    if movement not in {"EXPENSE", "INCOME", "TRANSFER", "REFUND", "UNKNOWN"} or method not in {"PIX", "CARD", "BOLETO", "BANK_TRANSFER", "CASH", "UNKNOWN"} or category not in {"FOOD", "HOME", "TRANSPORT", "HEALTH", "EDUCATION", "LEISURE", "OTHER"}:
        raise SystemExit(f"Linha {number}: expectativa de interpretação inválida.")
    if args.require_real and origin != "REAL_ANONYMIZED":
        raise SystemExit(f"Linha {number}: a avaliação privada requer notificação real anonimizada.")
    if origin == "SYNTHETIC":
        if reference != "synthetic" or "fictício" not in title.lower():
            raise SystemExit(f"Linha {number}: exemplo sintético precisa de identificação explícita.")
    else:
        item = evidence.get(reference, {})
        required = ("packageName", "appVersion", "androidVersion", "deviceModel", "collectedOn")
        if item.get("bank") != bank or item.get("redactionReviewed") is not True or not all(item.get(k) for k in required) or any("PREENCHER" in str(item.get(k, "")) for k in required):
            raise SystemExit(f"Linha {number}: faltam evidências do aparelho ou revisão da anonimização.")
        if args.require_real and (not isinstance(item.get("listenerConnected"), bool) or not isinstance(item.get("notificationDelivered"), bool) or item.get("reviewResult") not in {"RECONHECIDO", "AMBIGUO", "DESCARTADO", "PENDENTE"}):
            raise SystemExit(f"Linha {number}: registre separadamente entrega, conexão e resultado da revisão.")
        import datetime
        try:
            datetime.date.fromisoformat(item["collectedOn"])
        except ValueError:
            raise SystemExit(f"Linha {number}: data de coleta inválida.")
        # Este filtro é complementar; nomes e outros dados livres exigem revisão humana.
        if re.search(r"[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}|\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b|\b\d{8,}\b", title + " " + body):
            raise SystemExit(f"Linha {number}: possível dado pessoal. Revise a anonimização antes de publicar.")
    seen.add(case)
    counts[origin] += 1
if not seen:
    raise SystemExit("Nenhum caso reproduzível foi encontrado.")
print(f"Casos conferidos: {counts['SYNTHETIC']} sintéticos; {counts['REAL_ANONYMIZED']} reais anonimizados. A entrega pelo Android e pelos bancos exige teste no aparelho.")
