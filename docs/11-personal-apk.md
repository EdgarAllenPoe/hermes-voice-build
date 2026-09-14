# Personal APK builds with local signing

The "Personal APK for local signing" GitHub workflow compiles the ordinary package (`org.tomstout.hermesvoice`) and runs Android lint without receiving a signing key. Its artifact is deliberately unsigned and cannot be installed until local signing succeeds. The separate emulator CI package remains `org.tomstout.hermesvoice.ci`.

Use the retained personal keystore and the original personal APK when signing. `tools/SignPersonalApk.java` verifies that the key matches the previous APK, refuses to overwrite an output file, signs the new APK, and checks the resulting signature. It never generates or rotates a key. The helper uses Google's Apache-licensed apksig library 8.10.1, retrieved from the official Google Maven repository. Its verified SHA-256 is `c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89`.

After downloading the successful workflow artifact, verify its SHA256SUMS.txt and build-result.json before signing. Confirm the source commit, personal package, version, and minimum/target SDK in the included apk-badging.txt. Do not substitute an unsigned CI-package build. Keep temporary artifacts and the library under ignored `.tools/` or `dist/` directories.

With JDK 17 available and apksig-8.10.1.jar in `.tools/apksig/`, PowerShell commands from the source checkout are:

```powershell
javac -cp .tools/apksig/apksig-8.10.1.jar -d .tools/apksig tools/SignPersonalApk.java
java -cp '.tools/apksig;.tools/apksig/apksig-8.10.1.jar' SignPersonalApk `
  'FULL-PATH-TO-UNSIGNED.apk' 'NEW-OUTPUT-PATH.apk' `
  'config/private/android-debug.keystore' '../android/Hermes-Voice-0.2.0-test.apk'
```

Replace the two uppercase path placeholders with actual files. On Linux the Java classpath separator is `:` instead of `;`. The restored personal key uses the original Android debug keystore password and alias; this helper is specifically for that preserved prototype identity.

Before delivery, independently inspect the signed APK's binary manifest and DEX checksums, verify its signer matches the prior installation, record its SHA-256, and retain the source/run provenance. A successful build and signature check do not replace installation or hardware testing. Updating with the same package/key and a higher version code preserves Android's normal update path; do not uninstall an app containing pending recordings.

Normal local builds still use `tools/build_binaries.py --target android` and require the retained signing key. The unsigned mode is explicit through `HVB_UNSIGNED_PERSONAL_BUILD=1` and cannot be combined with `HVB_CI_BUILD=1`.
