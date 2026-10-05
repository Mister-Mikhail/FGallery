# Shared development signature

`debug.keystore.b64` is an intentionally public **test-only** Android debug key, alias `androiddebugkey`, store/key password `android`. CI decodes it into the standard Android debug keystore location in both jobs. Starting with 0.2.6, successive CI debug APKs share this certificate and can update one another without clearing app data.

Previous CI APKs had per-run generated certificates. The initial move to this key can require uninstalling the previous APK; Android cannot update an app with an unrelated certificate.

Do not use this public key for production. Production needs a separately managed private signing key and migration plan.

Push a `v<versionName>` tag when deliberately releasing a tested update. The Android workflow builds/tests that exact tag, then publishes arm64-v8a and universal debug APK assets as a GitHub release only if both jobs pass. Branch builds stay Actions artifacts. The app reads the latest GitHub release, offers optional download, validates size/digest/package/version/certificate, and hands installation to Android. Unknown-source installation consent remains a system requirement. Each released update must increase `versionCode` and `versionName`.
