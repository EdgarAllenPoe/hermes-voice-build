# 06 — Acceptance and failure testing

## Existing automated tests

On a Linux workstation with Python 3.10+, GCC and JDK 17 available:

```sh
bash tools/run_tests.sh
```

The recorded authoring run passed 56 tests. It includes host C/Python codec comparison, Java/Python container agreement, checksum/format rejection, SQLite concurrency/duplicates, private HTTP responses, review-mode behavior, safe subprocess argument passing, timeouts and simulated NOR storage recovery. It does **not** test real BLE, the ADC/charger, the microphone, Android runtime, or compile the target firmware/APK. The test-only `host_shim` headers must never be used for a firmware target build.

The no-default-passkey test checks the tracked example header. Provisioning tests create temporary test repositories and never rotate the real recorder code. The full suite can therefore run before or after local provisioning. GitHub Actions runs it on Linux; the Linux worker and native C harnesses are not a native Windows test suite.

## Required bench sign-off

Use harmless phrases that do not trigger consequential actions. Leave server delivery mode `review` until the entire pipeline is behaving correctly. Record results rather than ticking assumed successes.

| Test | Expected result | Pass / notes |
|---|---|---|
| USB-only firmware build/boot | Correct board/rail labels; usable microphone; no target build error | ______ |
| Charger verification | 100 mA intended profile; correct 4.20 V termination; safe behavior | ______ |
| Idle single click | No stored or uploaded message | ______ |
| Natural double-click and immediate speech | First word and final word preserved | ______ |
| 50–100 repeated captures | Measured startup reliability; no leaks or lost audio buffers | ______ |
| Quiet voice / room noise / outdoors | Threshold chosen from decoded recordings, not LED alone | ______ |
| Normal pauses inside an idea | No unacceptable premature auto-stop | ______ |
| Continuous speech or loud noise | Stops at maximum, flag set; manual click can stop earlier | ______ |
| Five seconds with no speech | No stored noise-only message under tested ambient conditions | ______ |
| Phone absent | Completed messages stay in recorder flash | ______ |
| Queue fills to 15 messages | No overwrite; new capture fails visibly | ______ |
| Phone reconnects | All committed files transfer; free slots return after ACK/erase | ______ |
| BLE interrupted mid-file | Complete original retained; download resumes/restarts safely | ______ |
| Deliberately corrupted transfer fixture | Rejected without recorder deletion | ______ |
| Server accepts, reply lost | Retry produces same receipt, not another queued job | ______ |
| Tailscale off / server off | Phone stores audio and retries later | ______ |
| Wrong API token | Unauthorized; queued file remains | ______ |
| Unauthorized BLE central | Cannot read audio or ACK/delete messages | ______ |
| Phone screen locked, idle 30+ minutes | Associated recorder delivered after actual OS wake behavior | ______ |
| Phone process reclaimed normally | Companion/service recovery works on this phone | ______ |
| User explicitly Force stops app | No bypass; reopening restores relay; recorder retains queue | ______ |
| Phone reboot before first unlock | Delay expected; completed recorder messages preserved | ______ |
| Reboot, first unlock, then relock | Relay resumes without changing assistant | ______ |
| Active phone call / media / earbuds | On-recorder capture still usable; no unintended phone audio use | ______ |
| Linux worker restart during transcription | Safe transcription retry | ______ |
| Worker interruption during Hermes dispatch | `uncertain`, not silent automatic replay | ______ |
| USB charging while capture occurs | No corrupt recordings or unexpected current/heat | ______ |
| Enclosure fitted | Microphone unobstructed, antenna range acceptable, no pouch compression | ______ |
| Battery runtime trial | Current log and observed endurance recorded | ______ |

Do not short the battery, over-discharge it or disconnect live test equipment unsafely to simulate faults. Use software disconnections and a suitable controlled power test arrangement. Real brownout/storage tests require appropriate bench practice.

## Latency record

Measure from the **second click** to the first valid captured samples using a repeatable audible reference and scope/logic trace where available. Record typical and worst-case results, not only the best trial. At least one test should involve speaking the exact first syllable immediately after the second click.

The 100 ms rail-settling delay and 500 ms double-click window are tuning parameters, not measured guarantees. Improving this latency must not introduce buffer overruns, truncate speech or trade away durable storage without an explicit design change.

## Human workflow tests

Use ideas with names, dates, Spanish phrases and long enough pauses to resemble actual use. Compare the retained WAV with the transcript. Confirm where Hermes stores an idea and how you will notice a clarification request, because version 1 does not show its response on the phone.

Only enable `delivery_mode: auto` after choosing an acceptable level of risk for voice-recognition errors and the specific permissions of your Hermes agent. Do not use this prototype for emergency, safety-critical or time-critical commands.
