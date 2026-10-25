"""Gera base de teste fictícia a partir do esquema 3 congelado; nunca abre a base do usuário."""
import argparse
import json
from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("destination", type=Path)
args = parser.parse_args()
if args.destination.exists():
    raise SystemExit("O destino já existe. A base anterior não foi substituída.")
schema = json.loads((root / "app/schemas/com.mateuswilliam.financas.data.FinanceDatabase/3.json").read_text())["database"]
snapshot = json.loads((root / "app/src/androidTest/assets/compatibility/snapshot-v1.json").read_text())
args.destination.parent.mkdir(parents=True, exist_ok=True)
# A criação exclusiva impede substituição acidental de um arquivo existente.
with args.destination.open("xb"):
    pass
try:
    with sqlite3.connect(args.destination) as connection:
        for entity in schema["entities"]:
            table = entity["tableName"]
            connection.execute(entity["createSql"].replace("${TABLE_NAME}", table))
            for index in entity.get("indices", []):
                connection.execute(index["createSql"].replace("${TABLE_NAME}", table))
            columns = [field["columnName"] for field in entity["fields"]]
            placeholders = ",".join("?" for _ in columns)
            for row in snapshot[table]:
                connection.execute(f"INSERT INTO {table} ({','.join(columns)}) VALUES ({placeholders})", [row[key] for key in columns])
        for query in schema["setupQueries"]:
            connection.execute(query)
        connection.execute("PRAGMA user_version = 3")
        assert connection.execute("PRAGMA integrity_check").fetchone() == ("ok",)
except Exception:
    args.destination.unlink(missing_ok=True)
    raise
print("Base fictícia do esquema 3 preparada; nenhum dado real foi utilizado.")
