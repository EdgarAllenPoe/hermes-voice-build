# 04 — Hermes host installation and Tailscale setup

## Assumptions to confirm

These scripts target a **Linux host with Python 3.10+ and user systemd**, using your ordinary Hermes login account. Arch, Debian, Ubuntu and other distributions have different package installation commands; the host OS has not been supplied. Do not paste guessed distro package commands into a different system.

Install the OS packages providing Python 3, Git, C/C++ build tools and CMake while online. Tailscale and Hermes are assumed to be your existing authorized installations. The receiver itself uses Python's standard library; no pip runtime dependencies are required.

Confirm:

```sh
python3 --version
command -v hermes
hermes chat --help
tailscale ip -4
tailscale status
```

The adapter specifically requires `hermes chat --query-file`. If your “Hermes” is another product or CLI, the bridge's capture/storage components still apply, but the dispatch adapter must be changed and tested. Do not automate keystrokes into a terminal as a substitute [S16].

## Install without starting execution

From the extracted package, as the same user that runs Hermes:

```sh
bash receiver/install-user.sh
```

The script refuses root, creates a private random token if missing, copies the receiver into `~/.local/share/hermes-voice/receiver`, places config/token under `~/.config/hermes-voice`, and installs two **user** service files. It does not start them or enable automatic agent execution.

Edit:

```text
~/.config/hermes-voice/config.json
```

Use absolute executable/model paths. Keep `delivery_mode` as `review` during commissioning. Use `bind: "127.0.0.1"` for HTTPS Serve. Set the correct Hermes working directory; normal interactive shell startup files are not automatically loaded by systemd. If Hermes needs other executables on PATH, configure a deliberate user-service environment rather than running as root.

## Prepare local speech-to-text

The optional online preparation script clones whisper.cpp v1.7.6, builds `whisper-cli`, and downloads the multilingual `small` model. A successful build requires a compatible C/C++ compiler and enough memory/disk. It deliberately limits parallel jobs. Review the script before running; it does not change your existing Hermes installation [S15].

```sh
bash tools/prepare-whisper.sh
```

Copy the paths printed by the script into `whisper_executable` and `whisper_model`. A different tested local model/executable may be substituted in configuration. Archive the completed source, executable, runtime libraries and model before offline use.

Check configuration without sending a voice command:

```sh
cd ~/.local/share/hermes-voice/receiver
python3 -m hvbridge --config ~/.config/hermes-voice/config.json check
```

## Start the private receiver

```sh
systemctl --user enable --now hermes-voice-receiver.service
systemctl --user status hermes-voice-receiver.service
```

For foreground troubleshooting instead, stop the service and run:

```sh
python3 -m hvbridge --config ~/.config/hermes-voice/config.json serve
```

Do not run both on the same port. The receiver accepts only a literal loopback or Tailscale IPv4 bind, not `0.0.0.0`. This implementation's intended tested socket family is IPv4; use `127.0.0.1`, not `::1`.

## Choose one network mode

### Preferred: HTTPS Serve

First inspect existing configuration:

```sh
tailscale serve status
```

**Do not overwrite an existing service at HTTPS 443/root.** Resolve any existing route conflict before proceeding. For a host without a conflicting Serve route, the intended setup is:

```sh
tailscale serve --bg --https=443 http://127.0.0.1:8765
```

Follow the local Tailscale permissions/HTTPS prompts. Use the full HTTPS URL Tailscale reports, adding `/v1/voice` in the Android app. No Funnel/public exposure is needed. Serve terminates TLS and forwards locally; the application bearer token is still required [S13].

### Alternative: private Tailscale HTTP

Set `bind` to the host's actual `100.x.x.x` Tailscale address, keep port 8765, and restart the receiver. Use `http://100.x.x.x:8765/v1/voice` in Android. The Tailscale tunnel encrypts transport, but this fallback lacks the additional HTTPS hostname/certificate check. Android performs a conservative local Tailscale-interface check; that check alone is not cryptographic proof of VPN identity. Prefer HTTPS.

Never expose port 8765 by router forwarding, public reverse proxy or Funnel. If the Tailscale interface is not ready at service startup, restart-on-failure allows a later bind retry.

### Access policy

`config/tailscale-grant.example.hujson` shows a phone-IP-to-server-IP grant for TCP 443, or TCP 8765 for direct HTTP. Merge the relevant entry into the **existing** tailnet policy, preserve administrative access, and test allowed/denied connections. Existing broad allow rules still permit their original access; restrictive intent cannot be achieved merely by adding another grant [S14].

## Prove durable receipt before invoking Hermes

Run the synthetic upload from the package root on the server. Leave the worker stopped initially:

```sh
python3 tools/send_recording.py fixtures/synthetic-tone.hvb \
  --url http://127.0.0.1:8765/v1/voice \
  --token-file ~/.config/hermes-voice/token
```

Use the host's Tailscale address instead of loopback if direct HTTP is your chosen bind. Repeat the upload: the second receipt should mark a duplicate with the same ID/hash. This fixed fixture is only a transfer test, not real speech. Never approve a Whisper transcript inferred from its tone.

Inspect queue status:

```sh
cd ~/.local/share/hermes-voice/receiver
python3 -m hvbridge --config ~/.config/hermes-voice/config.json status
```

Copy the token into the Android setup screen by a private local method; do not post it in chat or include it in screenshots. The default token file has restrictive permissions. Do not place the token in a URL or shell command-line argument.

## Start the worker in review mode

```sh
systemctl --user enable --now hermes-voice-worker.service
journalctl --user -u hermes-voice-worker.service -n 50 --no-pager
```

Send a harmless real voice test, then inspect the transcript under:

```text
~/.local/state/hermes-voice/work/MESSAGE_UUID/transcript.txt
```

A successful transcription becomes `review`, not an automatically executed command. To approve that specific, checked message:

```sh
python3 -m hvbridge --config ~/.config/hermes-voice/config.json approve MESSAGE_UUID
```

Confirm the intended Hermes outcome and inspect its log. The worker starts a standalone Hermes CLI invocation; it is not the same session as an already-open terminal chat. For your “capture an idea” workflow, configure Hermes's desired destination separately before trusting automatic notes placement.

After repeated harmless tests, change `delivery_mode` to `auto` and restart the worker. This affects newly transcribed items. Existing `review` items remain held until approved, avoiding an unexpected bulk release.

## Failure and retention operations

`failed` generally means transcription or preparation failed. `uncertain` means agent dispatch may already have caused side effects. Inspect the message and Hermes logs before deciding to retry.

```sh
python3 -m hvbridge --config ~/.config/hermes-voice/config.json retry MESSAGE_UUID
# Only after reviewing whether Hermes already acted:
python3 -m hvbridge --config ~/.config/hermes-voice/config.json retry MESSAGE_UUID --allow-uncertain
python3 -m hvbridge --config ~/.config/hermes-voice/config.json export MESSAGE_UUID recovered.wav
```

`purge-audio --older-than-days 7` clears only completed-message **database audio BLOBs**, preserving receipt IDs. It does not delete worker WAVs, prompts, transcripts, logs, SQLite journal remnants or Hermes's own history. Do not treat it as secure erasure. Stop services and follow guide 09 before deliberate filesystem cleanup or moving data to a new host.

User services may stop after logout unless the account is configured to remain active. On a dedicated host, review whether `loginctl enable-linger YOUR_USER` is appropriate under your administration policy. It changes login-service behavior; do not run it blindly on a shared machine.
