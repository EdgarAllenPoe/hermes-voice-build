# 07 — Preparing a genuinely useful offline kit

## What “offline” means here

All custom project code, configuration templates, build instructions and print files are included and can be read offline immediately. **That is not the same as including every third-party compiler, SDK, dependency, device driver and speech model.** Those large external packages are not bundled, and the target builds were not performed here.

Once a workstation is prepared, it can build from local caches without Internet access. The only trustworthy proof is a successful clean offline rebuild on that actual workstation, followed by installation on the actual devices. A saved webpage alone is not a toolchain.

Runtime capture also has a different meaning: the recorder can capture while the phone or network is absent. Tailscale delivery still requires connectivity between phone and host; local transcription can run offline after the model is downloaded, but the configured Hermes model/provider may require the Internet.

## Online preparation checklist

- [ ] Save this ZIP and keep an untouched copy with its SHA-256 manifest.
- [ ] Install Python 3.10+, JDK 17, GCC/C++ compiler, Git, CMake, Ninja where needed, USB tools and drivers for the chosen OS.
- [ ] Install Android SDK command-line tools, platform 36, build-tools 35.0.0 and platform-tools; accept licenses locally.
- [ ] Download Gradle 8.11.1 and verify the published checksum.
- [ ] Perform a successful Android build so Gradle plugins and build artifacts are cached.
- [ ] Install PlatformIO and all resolved Seeed board/framework/compiler/debug packages; perform a successful firmware build and USB upload.
- [ ] Record the installed platform Git commit; pin it in your local `platformio.ini` rather than relying indefinitely on a moving branch.
- [ ] Build whisper.cpp, download the chosen model, and verify transcription of a real spoken test clip.
- [ ] Confirm the installed Hermes CLI version, model access and authorization; do not store its credentials in the public bundle.
- [ ] Confirm existing Tailscale connection, policy and optional HTTPS Serve while online.
- [ ] Cache the optional Bleak Python package and dependencies only if using the desktop BLE utility.
- [ ] Save a tested build of the APK and firmware, plus associated configuration hashes, to your offline media.
- [ ] Export/print the manufacturer pinout and schematic from the official sources for the exact purchased revision; this bundle provides links and original assembly guidance, not a full mirrored vendor website.

## Files/caches to retain on the build machine

| Environment | Preserve |
|---|---|
| Android | JDK, Android SDK directory, `.tools/gradle-8.11.1`, Gradle caches, project source, debug or release signing key |
| Firmware | Python environment, PlatformIO installation, `~/.platformio` packages/platforms/toolchains, project source and private configuration |
| Receiver | Python runtime, project code/config, actual Hermes installation and its required environment |
| Transcription | whisper.cpp checkout, built binary and runtime libraries, model file and hashes |
| Documents | Markdown, self-contained HTML, PDF, wiring, BOM, private pairing card stored separately |

Copying caches to a different operating system or CPU architecture does not make them compatible. Absolute paths and environment variables may need adjustment after moving to another computer. Android platform licenses and third-party software licenses remain applicable.

## Prove offline operation

After **successful online builds**, record the environment:

```sh
bash tools/freeze-environment.sh
```

Disconnect network access on the build workstation, while keeping whatever local hardware connection is required. Force local outputs to rebuild, without deleting the cached SDKs or dependency packages:

```sh
# After a prior successful build and cached Gradle dependencies:
bash tools/build-android.sh --offline --rerun-tasks
# After a prior successful PlatformIO package installation/build:
pio run -d firmware -t clean
pio run -d firmware
bash tools/run_tests.sh
```

PlatformIO's package manager must find all required packages locally. Its behavior is not made offline-safe by a fictitious command-line flag; test with the network actually disconnected. Restore access only to fetch a specifically missing dependency, then repeat the test.

Also test local Whisper with network disconnected. Test Hermes separately because its provider may be remote. Save the exact outputs and build hashes that worked. A backup of the known-good signed APK and firmware image is often more useful than relying on rebuilding during a field repair.

## Printing without extra software

`print/Hermes-Voice-Button-Manual.pdf` is ready to print. `print/Hermes-Voice-Button-Source.pdf` is a separate printable programming reference. `print/Manual.html` and `print/Source-Listing.html` are self-contained; open them in an ordinary browser with the network disconnected, then print. The source listing is reference material. Always compile the actual source files, not text copied back out of a PDF.

No API token, private BLE passkey, model-provider credential or user recording is included in the printable public manual. Print the locally generated pairing card separately only after provisioning.
