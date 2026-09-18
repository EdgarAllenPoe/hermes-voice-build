# Receiver commissioning on September 18, 2026

The receiver and local transcription worker are installed as user services on the existing Hermes host. The phone user confirmed successful Bluetooth pairing with personal APK 0.3.1. A physical recording uploaded by the phone has not yet been confirmed.

## Installed configuration

- Receiver endpoint: http://100.99.200.55:8765/v1/voice
- Receiver and transcription worker: enabled and active as user services.
- Source installed from commit f86c9931b731b987c3bfb5a474e9c4da5eb9f183.
- Delivery mode: review. Transcribed messages wait for approval; they do not automatically invoke Hermes.
- Token: generated privately at ~/.config/hermes-voice/token with mode 0600. It is not included in Git or this document.
- Configuration: ~/.config/hermes-voice/config.json.
- Installed receiver: ~/.local/share/hermes-voice/receiver.
- Durable queue and transcripts: ~/.local/state/hermes-voice.
- Local transcription: whisper.cpp v1.7.6, multilingual small model.
- Existing Hermes CLI supports chat --query-file.
- User lingering was already enabled.

An existing unrelated service listens on 127.0.0.1:8765. The new receiver binds only to 100.99.200.55:8765, allowing both services to coexist. No existing service, public route, or tailnet policy was changed.

## Verified behavior

A request from the Windows project computer across Tailscale returned HTTP 200 with the correct token and HTTP 401 without a token or with a wrong token. The synthetic HVB1 upload returned a matching acceptance receipt; a repeat returned the same message ID and marked it duplicate.

The bundled public JFK speech sample was encoded as HVB1 and uploaded through the receiver. The running worker decoded and transcribed it into a nonempty transcript containing the expected word, country, and held it in review. Neither test invoked Hermes. Both test records are retained as rejected fixtures, outside the processing queue.

Whisper source commit: a8d002cfd879315632a579e73f0148d06959de36
Model SHA-256: 1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b

## Complete the phone setup

A private copy of the token is in the delivery kit at private/Hermes-Voice-server-token.txt. Transfer it privately to the phone, copy its single line into the app's Server token field, and keep the existing endpoint above. Tap Save server settings, then Test server connection. Expect Server reachable and token accepted. Then tap Start relay.

Keep Tailscale and Bluetooth connected. Double-click the board's B button, speak a short harmless phrase, and click B once to stop. Check the app's status and then the server queue. One vibration indicates receipt by the phone; two indicate acceptance by the server. Neither confirms transcription or agent execution.

Pairing is user-confirmed; microphone quality, real phone delivery, and reviewed Hermes handoff remain to be tested. Keep delivery mode review during those tests. The private commissioning JSON in the delivery kit contains the synthetic receipts and speech-test record without the server token.
