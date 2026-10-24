"""Verificação do manifesto mesclado para detectar permissões introduzidas por dependências."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = root / "app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
tree = ET.parse(manifest).getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {node.get(android + "name") for node in tree.findall("uses-permission")}
allowed = {
    "android.permission.POST_NOTIFICATIONS",
    "com.mateuswilliam.financas.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
if permissions - allowed:
    raise SystemExit("Permissões fora do escopo: " + ", ".join(sorted(permissions - allowed)))
application = tree.find("application")
if application.get(android + "allowBackup") != "false":
    raise SystemExit("O backup automático do Android deve permanecer desativado nesta versão.")
listener = next(node for node in application.findall("service") if node.get(android + "name", "").endswith("BankNotificationListener"))
if listener.get(android + "exported") != "false" or listener.get(android + "permission") != "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE":
    raise SystemExit("O serviço de captura precisa ser privado e protegido pela permissão do sistema.")
print("Manifesto conferido: permissões limitadas, serviço protegido e backup automático desativado.")
