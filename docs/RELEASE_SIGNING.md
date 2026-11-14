# BabyLog Android release signing and updates

Android only allows an in-place update when both APKs use the same signing certificate. Keep the private key and exported keystore permanently and back them up offline.

## Create the signing key once (Windows, without installing JDK)

Install Git for Windows and open Git Bash. The following commands use OpenSSL bundled with Git for Windows:

```bash
mkdir -p ~/babylog-signing
cd ~/babylog-signing

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out babylog-private.pem
MSYS_NO_PATHCONV=1 openssl req -new -x509 -key babylog-private.pem -out babylog-certificate.pem -days 36500 -subj "/CN=BabyLog/"
openssl pkcs12 -export -out babylog-release.p12 -inkey babylog-private.pem -in babylog-certificate.pem -name babylog
```

Choose and remember the export password. The GitHub secrets `BABYLOG_KEYSTORE_PASSWORD` and `BABYLOG_KEY_PASSWORD` both use this same password. The alias is `babylog`.

Copy the PKCS#12 file as Base64 for GitHub Actions:

```bash
base64 -w 0 babylog-release.p12 | clip.exe
```

This copies the Base64 text to the Windows clipboard. Never commit or share the private key, keystore, Base64 value, or passwords.

## Add GitHub Actions secrets

In the repository, open **Settings → Secrets and variables → Actions → Repository secrets → New repository secret**:

| Secret | Value |
|---|---|
| `BABYLOG_KEYSTORE_BASE64` | Entire Base64 text copied above |
| `BABYLOG_KEYSTORE_PASSWORD` | PKCS#12 export password |
| `BABYLOG_KEY_ALIAS` | `babylog` |
| `BABYLOG_KEY_PASSWORD` | Same PKCS#12 export password |

CI debug builds use this certificate once configured. The release workflow refuses to publish without all four secrets.

## Publish a release

Push a tag such as `v0.2.1` to trigger **BabyLog Android Release**. It builds a signed APK and attaches it to a GitHub Release. The app checks for updates every 24 hours and also has a manual check on the home page. Use a version higher than the app's installed version.

The first installation after switching from the old ephemeral CI key may require uninstalling the old app. Sync or back up local-only records first. Future updates can be installed over the existing app.

The updater checks file size and SHA-256 (when GitHub provides it), then opens Android's package installer. Android may ask to allow BabyLog to install unknown apps. Installation is never silent.
