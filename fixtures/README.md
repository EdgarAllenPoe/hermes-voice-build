# Synthetic test recording

`synthetic-tone.hvb` and its decoded WAV contain a computer-generated one-second 440 Hz tone, not anyone's voice. The fixed test UUID is `aa72fd6b-2400-4c9b-87ca-81d303e780f1`.

Use it for codec, checksum, queue, and HTTP tests. It is **not** a meaningful speech-recognition test. Speech models may hallucinate words from tones; keep the server in review mode and do not approve the resulting transcript.
