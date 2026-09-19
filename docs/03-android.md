# 03 — Android application: build, setup and locked-phone use

## Build requirements

The project uses JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.10.1, compile/target SDK 36 and minimum SDK 33. These are deliberate baseline choices, not a claim to use the newest available tools. Google's AGP 8.10 documentation supports API 36 and specifies the matching toolchain requirements [S19].

The app has no third-party runtime dependencies. AndroidX is enabled for the separate emulator test dependencies. SDK/build/test dependencies still need to be downloaded. A Gradle wrapper binary is **not** included; use the provided verified-download helper or install the exact Gradle distribution yourself.

Personal builds require the retained signing key before Gradle configuration. Restore it using guide 10; the full Windows workflow is in [the workshop guide](HARDWARE-GUIDE.md). CI builds use a separate disposable package and do not update the personal app.

### Linux/macOS build workstation

Install JDK 17 and Android SDK command-line tools. Set `JAVA_HOME` and `ANDROID_HOME` to the actual installations. Accept SDK licenses interactively and install:

```sh
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
cd /path/to/hermes-voice-build
bash tools/bootstrap-gradle.sh
bash tools/build-android.sh
```

The checksum helper uses `curl`, `sha256sum` and `unzip`; on systems without `sha256sum`, verify the official distribution using the platform's SHA-256 tool and unpack it manually. No credentials or unsigned download mirrors are needed.

After success, the debug APK is:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install with USB debugging already authorized:

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

This command does not bypass Android's on-device authorization. Preserve your signing key when updating; uninstalling or changing keys can require removal of the old app and can destroy its private queued messages. The debug APK is a personal prototype artifact, not a Play Store release.

### Windows build workstation

Install the same JDK and SDK versions. Download the Gradle 8.11.1 binary ZIP and its SHA-256 file from the official distribution source [S20], verify with `Get-FileHash`, and unzip into `.tools` so `.tools\gradle-8.11.1\bin\gradle.bat` exists. Set `ANDROID_HOME` and `JAVA_HOME`, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-android.ps1
```

For a prepared offline build, add `-Offline`. This script changes no system execution policy. PowerShell does not provide the Linux `systemd` server environment; use the actual Hermes host for that part.

## Initial setup

Open **Hermes Voice**. Grant Nearby devices/Bluetooth and notification permissions. Enter the exact receiver URL and locally generated bearer token, then save. Leaving the token field empty on later saves preserves the encrypted stored token.

Preferred URL:

```text
https://YOUR-HOST.YOUR-TAILNET.ts.net/v1/voice
```

Private HTTP fallback:

```text
http://YOUR-100.X.X.X:8765/v1/voice
```

Substitute the real address; placeholders are not valid URLs. The app deliberately rejects public HTTP destinations, arbitrary hostnames, redirects and URL-embedded credentials. HTTPS uses normal system certificate validation, not an “accept any certificate” bypass.

Turn on Bluetooth and, if Android requires it for companion discovery, Location Services during initial pairing. The app does not request location permission. Hold the recorder button 1.5 seconds, release, and tap **Pair voice button**. Approve the Android association chooser and then complete the separate Bluetooth pairing prompt using your printed local passkey. Association alone is not a bond [S10–S12].

Tap **Start relay** after saving settings and completing pairing. A persistent notification confirms the relay service is running. Your default assistant does not change. Keep the existing Tailscale app connected.

## Daily behavior

Double-click the external recorder and speak; no phone interaction is part of the normal workflow. The microphone is on the recorder, not the phone or earbuds. The phone validates incoming data, commits its queue, vibrates once, and acknowledges the recorder. It uploads independently, then vibrates twice only after the server returns a matching acceptance receipt.

Android haptics can be disabled or missed. Neither vibration confirms transcription or completed Hermes actions. Do not speak into a covered phone expecting its microphone to record.

The service attempts immediate upload after receipt. A persisted periodic job retries pending uploads roughly on Android's allowed schedule; its 15-minute interval is a requested minimum cadence, **not an exact delivery-time guarantee under Doze**. The **Retry queued uploads** button is available during troubleshooting. A late upload does not make the recording disappear.

## Locked-phone boundaries

Test the exact phone/Android version. The companion and connected-device APIs are the correct supported mechanisms, but they are not an exemption from every operating-system restriction [S10–S12].

- Unlock once after a reboot so credential-protected app storage and token access are available.
- Do not use Force stop for normal operation. A force-stopped app requires reopening; the recorder queue protects completed captures while it is stopped.
- Keep Bluetooth and Tailscale enabled and paired. Revoked permissions or a removed bond require setup again.
- Review the device's background/battery restrictions for this app and Tailscale during commissioning. Do not disable unrelated system protections blindly.

**Stop relay** is an intentional user stop: it stops transfer and upload without deleting existing messages. New captures remain on the recorder until resumed, subject to its 15-message capacity.

## Storage and privacy

The bearer token is encrypted using an Android Keystore AES-GCM key without per-use biometric authentication so the relay can operate while locked after first unlock. Queued audio remains inside app-private, credential-protected storage; it is not separately content-encrypted by this application. App backups are disabled. Copying the app's data elsewhere or uninstalling the app can lose undelivered recordings.

Phone receipts are retained after server acceptance, while their audio BLOB is cleared. Temporary partial files are removed after recorder ACK completion, or reused on reconnection. Transcript inspection and editing are available through the Linux receiver CLI; the Android app remains a relay.

## Version 0.3 diagnostics and queue recovery

Use Test server connection before pairing to verify the configured server/token. Refresh status shows phone queue usage, connection, transfer progress and the last recorder snapshot. Export diagnostics writes only the diagnostic allowlist; it excludes credentials, endpoint, Bluetooth address and audio. Review held uploads explains permanent per-message rejections and offers explicit retry after the cause is corrected. Pending recordings use bounded backoff; Retry queued uploads makes them eligible immediately. Android scheduling can still delay background work.

Schema 1 databases are upgraded in place to schema 2, retaining existing audio and receipts. Personal v0.3 builds must use the original signing key. The CI app has a separate package and is intended for disposable software testing. See guide 10.

## Version 0.3.1 pairing correction

Android's companion association returns a MacAddress whose string representation uses lowercase letters. BluetoothAdapter.getRemoteDevice requires uppercase letters and rejected that address in version 0.3.0. The app now normalizes and validates the address before saving or using it. Relay connections and companion presence observation also repair saved lowercase addresses during normal use, preserving the association ID and other settings.

Install the personal 0.3.1 APK as an update over 0.3.0; do not uninstall or clear app storage. Hold the board's B button for about two seconds, release, and use Pair voice button in the app again. Complete Android's separate pairing prompt with the existing private passkey if asked. This app fix does not require a firmware flash or bond reset.

The emulator regression uses the actual Android MacAddress and BluetoothAdapter classes to reproduce the failure and verify the corrected handoff. Further cases cover invalid input, already-valid addresses, saved-address repair, and an unpaired installation. Physical phone pairing still requires confirmation on the user's device.

Android API contract: https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getRemoteDevice(java.lang.String)

## Version 0.3.2 recorder queue display

The recorder queue now decreases only after the recorder confirms a transfer acknowledgement. Saving on the phone or receiving a server receipt alone does not change this count. The relay then reads fresh recorder status before requesting the next recording, so recordings added during delivery are included.

The recorder can disconnect immediately after its last acknowledgement. In that case the app retains an estimated remaining count (normally zero) instead of the old pre-transfer count. Recorder status is labelled **last known**, and the last full status read is dated. A lost acknowledgement response keeps the previous count until reconnection; it does not falsely claim deletion. Skipped corrupt recordings still occupy their recorder slots and remain counted.

Tap **Refresh status** to display the latest stored diagnostics. This button does not force a Bluetooth connection. Transfer completion now says **Saved on phone; recorder confirmed receipt** once the acknowledgement succeeds. Check the phone queue and **Last upload** separately for server delivery.

Install the personal 0.3.2 APK over the existing app without uninstalling or clearing storage. The package and retained signing key are unchanged. No firmware update or new pairing is required. An old queue snapshot from an earlier app version is labelled as such until the recorder next connects.

## Version 0.4.0 status and diagnostics screens

The default **Status** tab shows an overall indicator and separate Recorder, Delivery and Server cards. Green means a verified good condition, yellow means waiting or a check is needed, and red identifies an actionable problem. Every indicator includes words and a symbol; meaning never depends on color alone. Recorder standby is expected between recordings and is not reported as a hardware failure. The app cannot confirm that an offline recorder is powered on.

A green server card requires an authenticated health check or matching upload receipt within the previous two minutes. The app checks on foreground entry and approximately once per minute while open. Status refreshes every two seconds while visible. It does not continually poll the server in the background. Changing server settings invalidates older checks, including requests that finish after the change. Server acceptance confirms delivery; processing and Telegram replies remain separate.

**Diagnostics** contains server settings, pairing, permissions, retries, held-recording review, selectable technical details and a privacy-filtered export. The server token remains hidden. Saving an empty token field preserves the existing token. Invalid settings leave the existing configuration intact. Phone queue counts come directly from SQLite; recorder counts remain labelled last known.

The interface follows the phone's light/dark setting and supports enlarged text, screen rotation, system-bar/keyboard insets and scrollable content. Buttons have at least 48 dp touch targets. The two tabs retain selection across activity recreation.

The personal delivery uses a non-debuggable **release** APK, signed locally with the retained installation key. Install it over the existing app without uninstalling or clearing data. The app's package, queue schema, pairing and firmware protocol remain compatible. This is a personal sideloaded release; actual locked-phone Bluetooth behavior and battery hardware still require physical acceptance testing.
