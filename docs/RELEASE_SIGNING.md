# BabyLog Android release signing and updates

Android only allows an in-place update when both APKs use the same signing certificate. Keep this keystore permanently and back it up offline.

## Create the signing key once

On a trusted computer with JDK 17 or newer:

```bash
keytool -genkeypair -v -keystore babylog-release.jks -alias babylog -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 babylog-release.jks
```

On macOS, use `base64 < babylog-release.jks | tr -d '\\n'`. Never commit the keystore or passwords.

## Add GitHub Actions secrets

Repository **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `BABYLOG_KEYSTORE_BASE64` | Entire Base64 output |
| `BABYLOG_KEYSTORE_PASSWORD` | Keystore password |
| `BABYLOG_KEY_ALIAS` | `babylog` |
| `BABYLOG_KEY_PASSWORD` | Key password |

CI debug builds use this certificate once configured. The release workflow refuses to publish without all four secrets.

## Publish a release

Push a tag such as `v0.2.0` to trigger **BabyLog Android Release**. It builds a signed APK and attaches it to a GitHub Release. The app checks for updates every 24 hours and also has a manual check on the home page.

The first installation after switching from the old ephemeral CI key may require uninstalling the old app. Sync or back up local-only records first. Future updates can be installed over the existing app.

The updater checks file size and SHA-256 (when GitHub provides it), then opens Android's package installer. Android may ask to allow BabyLog to install unknown apps. Installation is never silent.
