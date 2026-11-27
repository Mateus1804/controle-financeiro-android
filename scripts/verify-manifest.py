"""Verificação do manifesto mesclado para detectar permissões introduzidas por dependências."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = root / "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
tree = ET.parse(manifest).getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {node.get(android + "name") for node in tree.findall("uses-permission")}
allowed = {
    "android.permission.USE_BIOMETRIC",
    "com.mateuswilliam.financas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
if permissions - allowed:
    raise SystemExit("Permissões fora do escopo: " + ", ".join(sorted(permissions - allowed)))
application = tree.find("application")
if application.get(android + "allowBackup") != "false":
    raise SystemExit("O backup automático do Android deve permanecer desativado nesta versão.")
removed_services = ("BankNotificationListener", "RecurringReminderService")
if any(node.get(android + "name", "").endswith(removed_services) for node in application.findall("service")):
    raise SystemExit("O manifesto voltou a incluir serviços de captura ou lembretes.")
print("Manifesto conferido. Permissões limitadas e backup automático do Android desativado.")
