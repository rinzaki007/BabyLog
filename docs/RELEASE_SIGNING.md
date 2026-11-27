# BabyLog Android release signing and updates

Android only allows an in-place update when both APKs use the same signing certificate. Keep the private key and exported PKCS#12 keystore permanently and back them up offline. Losing the signing key may prevent users from installing future APKs over the existing app.

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

This copies the Base64 text to the Windows clipboard. Never commit or share the private key, keystore, Base64 value, or passwords. Keep at least one encrypted offline backup of the original `.p12` file and its recovery information.

## Add GitHub Actions secrets

In the repository, open **Settings → Secrets and variables → Actions → Repository secrets → New repository secret**:

| Secret | Value |
|---|---|
| `BABYLOG_KEYSTORE_BASE64` | Entire Base64 text copied above |
| `BABYLOG_KEYSTORE_PASSWORD` | PKCS#12 export password |
| `BABYLOG_KEY_ALIAS` | `babylog` |
| `BABYLOG_KEY_PASSWORD` | Same PKCS#12 export password |

CI debug builds use this certificate once configured. The formal release workflow refuses to publish without all four secrets.

## Publish a release

The workflow is **BabyLog Android Release** at `.github/workflows/release.yml`. It is separate from the ordinary PR / main CI.

Recommended manual process:

1. Merge the intended release changes into `main` and confirm all three CI checks pass.
2. Open **GitHub → Actions → BabyLog Android Release → Run workflow**.
3. Select the `main` branch and enter the version without the `v` prefix, for example `2.0.0`.
4. Start the workflow and wait for the signed APK build, launcher-icon validation, GitHub Release publication, and update-manifest publication to finish.
5. Verify the GitHub Release attachment and the `updates` branch's `latest.json`; the tag, version, download URL, file size, and SHA-256 must match the actual APK.

A pushed tag matching `v*` (for example `v2.0.0`) also triggers the workflow. Only create such a tag after confirming it points to a commit already merged into `main` and validated by CI. Manual dispatch creates the corresponding `v<version>` tag and GitHub Release.

The workflow generates `versionCode` from the GitHub Actions run number and publishes it in `updates/latest.json`. The version name comes from the manual input or the pushed tag. The app compares `versionCode` whenever the update manifest provides it; version names are only a compatibility fallback. Do not reuse a version tag or assume that rerunning a partially successful release is harmless.

## Verify the published update feed

The release workflow generates `latest.json` on the `updates` branch. It contains the release tag, version, `versionCode`, APK download URL, file size, and SHA-256 digest. The app's update checker uses this public manifest to identify the current release. Historical installs with the mistakenly formatted `3.03` version name are interpreted as `3.0.3` when an older NAS manifest does not provide a `versionCode`.

A successful GitHub Release does not by itself prove that the in-app update feed was published. Confirm that the **Publish public update manifest** step also succeeded and compare the manifest values with the actual release APK. If that step fails after the Release was created, inspect the existing Release and manifest before retrying; repair the feed from the already-published APK where possible rather than rebuilding and republishing the same tag blindly.

For the complete failure-recovery checklist and NAS update decision rules, see [DEVELOPMENT.md](DEVELOPMENT.md#android-正式发布流程).

## First installation and upgrades

The first installation after switching from an old ephemeral CI signing key may require uninstalling the old app. Sync or back up local-only records first. Once the persistent release certificate is installed, future releases signed with the same key can be installed over the existing app.

The updater checks file size and SHA-256 when supplied by the manifest, then opens Android's package installer. Android may ask to allow BabyLog to install unknown apps. Installation is never silent.
