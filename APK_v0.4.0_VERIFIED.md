# Verified personal release APK 0.4.0

Built and locally signed September 19, 2026. The app now has two tabs: **Status** for everyday health and **Diagnostics** for configuration, queue recovery and technical detail.

## What changed

- An overall status and separate Recorder, Delivery and Server cards use green, yellow and red with readable labels and distinct symbols.
- Normal recorder standby is explained. An offline recorder is not claimed to be connected.
- A healthy server indicator requires an authenticated check or verified upload receipt within two minutes. Server checks refresh approximately once a minute while the app is visible; the screen updates every two seconds.
- Permission, pairing, relay-running, pending, held, storage and stale-status conditions have separate handling. Invalid server edits preserve existing settings; old checks cannot validate newly edited settings.
- The interface follows system light/dark mode, respects system-bar and keyboard insets, supports scrolling and enlarged text, and preserves tab selection on recreation.
- The personal APK is now a non-debuggable release build. Its package, signing identity, queue schema and recorder protocol remain compatible with the installed app.

## Verified build

| Item | Verified value |
|---|---|
| Delivery file | android/Hermes-Voice-0.4.0.apk |
| Package | org.tomstout.hermesvoice |
| Version / code | 0.4.0 / 6 |
| Build type | Release; debuggable false |
| Minimum / target SDK | 33 / 36 |
| Source commit | 50e830648a1232dab2c9758adcbb7b8d987cc632 |
| APK SHA-256 | 090af733807909bb112cb70dfae6dea3c3e115f8b943c2248c069be9c5d70347 |
| Signing certificate SHA-256 | c9000cf91505d6a88f2a1afc2dc4cd00a507780434040e77fbb1c4a03a8d19d2 |
| Personal release build | [35454318474](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35454318474) |
| Android tests | [35454318006](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35454318006) |
| Host tests | [35454318014](https://github.com/EdgarAllenPoe/hermes-voice-build/actions/runs/35454318014) |

Release compilation and lint passed. The downloaded artifact and its members passed checksum checks. Local signing matched the previous personal APK. Independent checks verified the release manifest, signature, content digest, DEX integrity, unchanged compiled entries and rejection of tampered/truncated APKs.

All **21 Android tests passed on each of API 33, 35 and 36** (63 executions), including persisted queue status, database migration, pairing-address handling, atomic settings updates, health invalidation, tab navigation, dark theme, 200% text scaling and credential exclusion. The host suite passed, including 27 dashboard health scenarios and 19 recorder transfer scenarios.

Actual Android 16 screenshots were visually reviewed in light/dark modes and with enlarged text; the Android 13 standard layout was also inspected. Android 15 app assertions passed, but one dark-mode screenshot was obscured by an unrelated Pixel Launcher ANR dialog. This is retained in the private evidence rather than counted as a clean visual capture. All 12 measured app text/color pairs exceed 4.5:1 contrast (minimum 5.72:1).

Screenshots under android/preview-v0.4.0 in the delivery kit show an isolated, unconfigured CI installation. They do not show the user's settings or the status of the real recorder.

## Install and use

Install **Hermes-Voice-0.4.0.apk** over the existing personal app. Do not uninstall or clear its storage. Pairing, the server token, queued audio and receipts stay with the existing installation. No firmware flash is needed.

Open the app. **Status** is the default tab. Tap **Start relay** if the relay is paused, then **Check connection**. A recorder between recordings normally shows yellow **Standing by**. Green server verification confirms the receiver and token; the phone does not independently verify Hermes processing or Telegram delivery.

Use **Diagnostics** for server settings, pairing, Android permissions, retries, held-recording review, live technical readings and the privacy-filtered export. A blank token field preserves the saved token.

## Remaining physical acceptance

This release is compiled, signed, automatically tested and visually reviewed. It has not yet been installed on the user's phone. Confirm update-in-place behavior, one complete recorder-to-server transfer, reconnect after Bluetooth loss, and delivery with the phone locked on the actual phone/OS. Battery-only operation and charging remain a separate unresolved hardware issue.

The repository includes a release build for personal sideloading; it is not a claim of Play Store certification or completed end-to-end hardware acceptance.
