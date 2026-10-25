"""Confere identidade, versão, assinatura e checksum sem publicar o aplicativo."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
apk = root / 'app/build/outputs/apk/release/app-release.apk'
aab = root / 'app/build/outputs/bundle/release/app-release.aab'
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
expected_certificate = os.environ.get('ANDROID_SIGNING_CERT_SHA256', '').replace(':', '').lower()
if not re.fullmatch('[a-f0-9]{64}', expected_certificate):
    raise SystemExit('Defina ANDROID_SIGNING_CERT_SHA256 com a impressão digital pública esperada.')
certificate = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)', certificate)
if [value.lower() for value in digests] != [expected_certificate]:
    raise SystemExit('A assinatura não corresponde ao certificado definitivo esperado.')
badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
if not package or package.groups() != ('com.mateuswilliam.financas', os.environ['RELEASE_VERSION_CODE'], os.environ['RELEASE_VERSION_NAME']):
    raise SystemExit('Identidade ou versão divergente da preparação solicitada.')
if 'application-debuggable' in badging:
    raise SystemExit('A versão release não pode ser depurável.')
bundle_verification = subprocess.run(['jarsigner', '-verify', str(aab)], text=True, capture_output=True, check=True)
if 'jar verified.' not in bundle_verification.stdout:
    raise SystemExit('A assinatura do AAB não foi confirmada.')
bundle_certificate = subprocess.check_output(['keytool', '-printcert', '-jarfile', str(aab)], text=True)
bundle_digest = re.search(r'SHA256: ([a-fA-F0-9:]+)', bundle_certificate)
if not bundle_digest or bundle_digest[1].replace(':', '').lower() != expected_certificate:
    raise SystemExit('O certificado do AAB difere da assinatura esperada.')
metadata = {'applicationId': package[1], 'versionCode': int(package[2]), 'versionName': package[3], 'certificateSha256': expected_certificate,
            'sha256': {file.name: hashlib.sha256(file.read_bytes()).hexdigest() for file in (apk, aab)}}
(root / 'qa').mkdir(exist_ok=True)
(root / 'qa/release-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print('APK release, identidade, versão e certificado conferidos. Os artefatos não foram publicados.')
