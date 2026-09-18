# 08 — Troubleshooting and recovery

## Start at the earliest failed handoff

Do not change firmware, Android, Tailscale and Hermes simultaneously. Establish where the last durable copy is, then diagnose the next stage. Preserve original audio until recovery is confirmed.

| Symptom | Check first | Do not do |
|---|---|---|
| Board build reports unknown label/Kconfig symbol | Correct Sense board, Seeed BSP version, generated DTS and manufacturer examples | Guess GPIOs or remove charger checks |
| Board shows red after startup/click | Mic rail, flash initialization, queue capacity, debug evidence | Assume it is merely low battery |
| Double-click loses first word | Actual mic-settling delay, debounce/window, first-click timing and audio waveform | Wait for a phone vibration and call it instant capture |
| Auto-stop never happens | Background noise, energy threshold, 60 s cap/manual stop | Assume the energy gate recognizes human speech |
| Auto-stop too early | Natural pauses versus 1.2 s timeout | Increase sensitivity blindly without noise tests |
| Recorder not in pairing chooser | Physical pairing window, antenna, Bluetooth/Location Services, wrong device/bond | Share the passkey publicly |
| Pairing reports an invalid Bluetooth address | Update the personal app to 0.3.1 or later, then retry Pair voice button; 0.3.0 passed lowercase addresses to an uppercase-only Android API | Uninstall the app, erase its data, or reflash the recorder for this formatting error |\n| Android association exists but transfer fails | Separate Bluetooth bond/passkey, permissions, authenticated characteristic access | Treat association as equivalent to pairing |
| Locked phone does not respond | First unlock, Force stop status, relay enabled, OS battery restrictions | Promise Android restrictions can be bypassed |
| Phone saved file but no second vibration | Tailscale status, endpoint/token, server logs, phone pending queue | Delete app data to “start fresh” |
| HTTP 401 | Token mismatch; save the correct local token | Put a token in the URL or paste it in chat |
| HTTP 409 | UUID collision with different bytes | Overwrite the server's existing receipt |
| HTTP 507 | Queue quota; receiver/worker failure or retention backlog | Delete unprocessed originals indiscriminately |
| No Hermes action | Review mode, queue state, actual CLI capability and environment | Assume 202 means processing completed |
| `uncertain` job | Hermes log/history and whether action already happened | Blindly retry a potentially repeated action |
| Unacceptable battery runtime | Measured System-ON/advertising current and pending queue duration | Apply published System OFF numbers to this build |

## Local commands

```sh
systemctl --user status hermes-voice-receiver.service hermes-voice-worker.service
journalctl --user -u hermes-voice-receiver.service -n 50 --no-pager
journalctl --user -u hermes-voice-worker.service -n 50 --no-pager
tailscale status
tailscale serve status
cd ~/.local/share/hermes-voice/receiver
python3 -m hvbridge --config ~/.config/hermes-voice/config.json status
```

Application logs should not include bearer tokens or HTTP audio bodies. Worker files intentionally contain transcripts, prompts and Hermes output; redact them before sharing. A stack trace in the **automated tests** for the deliberately broken fake agent or missing fake model is expected; the test passes only when that failure is correctly contained.

## Capture inspection

Export a retained server recording with the CLI `export` command. For a recorder download or fixture already on disk:

```sh
python3 tools/audio_tool.py inspect recording.hvb
python3 tools/audio_tool.py decode recording.hvb recording.wav
```

Play the WAV locally. A correct checksum does not guarantee that the microphone captured intelligible speech; it only confirms byte integrity.

The optional Linux desktop BLE utility can isolate radio/protocol problems before testing the phone. Stop the phone relay so it does not take the board's single connection. Pair the recorder in a physical pairing window using the host's normal Bluetooth interface, then:

```sh
python3 tools/desktop_ble_receiver.py AA:BB:CC:DD:EE:FF --out received
```

The utility requires the separately prepared Bleak package and normal OS Bluetooth permissions. Its durable local-file ACK means the recorder may reclaim the message; those bench files must then be uploaded or otherwise retained deliberately.

## Re-pairing without losing captures

Do not reinstall the phone app while it contains pending recordings. Stop relay, resolve/retain its pending data, remove the old Android association/bond through settings as appropriate, hold the recorder button 10 seconds to clear its bond, and pair again using the current local code. That firmware gesture does not erase committed messages.

Repeated checksum failure of the oldest recorder message can block progress. This prototype intentionally has no casual “delete corrupt recorder message” action. Preserve the flash for developer inspection; deleting a message just to clear an error may destroy the only copy.
