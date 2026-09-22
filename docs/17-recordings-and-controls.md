# Recordings and recorder controls

Applies to Android **0.5.0** and XIAO nRF54LM20A Sense firmware **0.4.0**. Install the personal APK over the existing app; keep its data and Bluetooth bond. Older firmware can still transfer recordings, but the new recorder controls require firmware 0.4.0.

## Everyday use

1. Keep Bluetooth and Tailscale on, with the Hermes Voice relay running.
2. **Press and release the recorder button once.** Speak when the RGB light turns green.
3. Pause for the configured silence interval to save, or press once again to save immediately. The default is **two seconds**. Automatic mode cancels a recording if no speech is detected in the first five seconds. Every recording has a 60-second limit.
4. The green save pattern means the recording is stored on the recorder. The blue transfer pattern means the phone has safely received it.
5. Open **Recordings** to distinguish receipt by the server from completed Hermes processing.

The Status and Diagnostics tabs remain. Both provide access to **Recordings** and **Recorder controls**. The button on the board and the external button use the same controls. Hold for about two seconds, then release, only when you need pairing. A ten-second idle hold forgets the phone bond; it is not needed for normal reconnection.

## Recording history

The latest 200 entries show duration, a short recording ID, and a stage:

| Stage | What it confirms |
|---|---|
| On recorder (last seen) | The recorder reported a completed recording in its latest inventory. |
| Saved on phone / waiting to send | The phone has a durable copy; server acceptance is pending. |
| Saved on phone / held for review | The phone has retained the audio after an upload problem requiring attention. |
| Received by server | The server returned a matching ID and audio hash. Processing is separate. |
| Transcribing / awaiting review / Hermes processing | The server reports its current processing stage. |
| Processed by Hermes | The server reports a completed worker handoff. Check the actual result for any requested external action. |
| Server processing needs attention | The server reports failure or an uncertain handoff. Inspect the receiver before retrying an action. |

**Refresh delivery status** requests authenticated status from the server. Each receipt is checked at most once a minute, in batches of 20; several refresh cycles may be needed for a long history. A missing or unreachable status endpoint leaves the previous receipt intact. It does not resend delivered messages. These checks run on foreground refresh and upload work, not as a continuous background status monitor.

Times are **received on phone** or **first seen**, not a claimed recording wall-clock time. The recorder has no synchronized clock. Older receipts may have no duration or playable audio. A recorder entry can be brief because the phone immediately transfers available audio.

## Settings and microphone test

Open **Recorder controls** while connected.

- Choose **2, 4, or 6 seconds** under Silence before automatic save, then tap **Save settings to recorder**. Four seconds gives more room for thinking between phrases.
- **Save only when I press again** selects manual mode. Press again to save; the 60-second cap still applies. Manual mode also disables the five-second no-speech cancellation.
- Speech threshold defaults to **40**. Lower values detect quieter sounds; higher values reject more background noise. The accepted range is 10–1000. This is an audio-level threshold, not speech recognition.
- Saved settings survive a recorder restart. Changes apply to the next recording; the recorder rejects changes while capture is active.

For a microphone check, tap **Start microphone test**, stay quiet briefly, then speak at the usual distance. Compare the room-noise level and normal speech with the threshold. The cyan light indicates this test. **No test audio is stored or uploaded.** Stop it with **Stop microphone test**, leave this screen, press the recorder button, or disconnect. It also stops after 60 seconds. The first button press during a test stops the test; press again to make a recording.

## Queue tools and deletion

- **Recordings → Delete unsent phone copy** removes that pending or held copy after confirmation. A receipt/tombstone prevents an already-seen copy from reappearing on re-transfer. An upload already in progress may finish first; deletion cannot recall a server copy.
- **Recorder controls → Review recorder queue** lets you select a completed or damaged recorder slot for deletion. The confirmation lists slot, duration when known, and short ID.
- **Clear recorder queue** deletes only the specific slot/ID pairs listed in its confirmation. A new recording is not swept into an old confirmation. If the queue changes while the dialog is open, refresh and try again. Active recordings cannot be deleted.
- **Clear unfinished phone transfers** removes partial or rejected downloads. It leaves complete phone recordings and recorder originals intact, then reconnects so needed transfers can start again.

Phone and recorder deletion are separate. Neither deletes server audio, transcripts, Telegram messages, or completed Hermes actions. Free slots may take a moment to appear while flash erasure finishes. The recorder has 15 slots regardless of recording length.

## Playback and retention

Unsent phone recordings can be played from **Recordings**. Playback uses the phone's media volume and stops when leaving the screen.

**Keep delivered audio for 24 hours** is off by default. Enable it before sending a recording if you want to play its phone copy afterward. The retention period starts when the server accepts the audio. Turning the option off removes retained delivered audio while keeping receipts. It cannot restore previously removed audio.

Playback and normal queue operations enforce expiry. An hourly Android job also requests cleanup without needing the relay or network; Android can defer that job. Thus expired audio is not playable through the app, but physical database cleanup is not guaranteed at the exact deadline. This is ordinary deletion, not a forensic secure-erase promise. Unsent audio is retained until delivered or explicitly deleted. All phone audio shares a 100 MiB quota; if full, further recordings remain on the recorder.

## Connection and battery

**Reconnect recorder** closes a stuck connection and starts another attempt. With the relay running, automatic retry uses a bounded backoff. Firmware 0.4.0 remains available for reconnection when its queue is empty. Status timestamps distinguish fresh information from an offline snapshot. Android restrictions, distance, and radio conditions can still interrupt a connection.

The battery card reports measured voltage and charging state. **USB powered · not charging** does not by itself prove a full battery: a disconnected cell or a charging pause can also stop current. No percentage or remaining runtime is invented. Low battery is currently a diagnostic threshold of 3.50 V or less while on battery; it produces a brief red pulse and a phone notification at most once per hour. Runtime and the threshold still need battery-only testing.

## Light reference

| RGB light | Meaning |
|---|---|
| Steady green | Recording. |
| Two green flashes in a group | Saved on recorder. |
| Three blue flashes in a group | Safely received and acknowledged by the phone; not a server receipt. |
| Three red flashes in a group | No free recording slot or a storage/commit error. Check Diagnostics. |
| Steady red | Some microphone startup/capture errors. Check Diagnostics. |
| Brief red flash about every ten seconds | Low battery when other light indications are idle. |
| Cyan | Live microphone test; no recording saved. |
| Blue while holding the button | Pairing hold recognized. |
| Magenta after a long idle hold | Phone bond reset. |

Save/transfer/error groups repeat over about 2.4 seconds. A newer event can replace an earlier one, and an active recording/test takes priority. The **separate red charging LED** is independent of these RGB patterns; its existing current-based behavior is unchanged. An idle recorder may have no RGB light lit.

## Verification and remaining checks

See [release verification](../FEATURES_0.5.0_VERIFIED.md). Device checks cover connected status, settings round trips, live microphone frames without saved audio, and reconnect. Actual speech delivery, LED visibility, long-duration screen-lock/range testing, low-battery behavior, and charging-cycle acceptance are separate physical checks.
