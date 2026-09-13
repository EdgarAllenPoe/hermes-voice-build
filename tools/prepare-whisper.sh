#!/usr/bin/env bash
# ONLINE: independent, optional local transcription backend for the Linux host.
# v1.7.6 is a fixed baseline, not a claim to be the newest version.
set -euo pipefail
root="${WHISPER_DIR:-$HOME/.local/share/hermes-voice/whisper.cpp}"
rev="${WHISPER_REV:-v1.7.6}"
[[ ! -e "$root" ]] || { echo 'Target already exists. Preserve it; update deliberately.'; exit 1; }
git clone --branch "$rev" --depth 1 https://github.com/ggml-org/whisper.cpp.git "$root"
cmake -S "$root" -B "$root/build" -DCMAKE_BUILD_TYPE=Release -DGGML_NATIVE=OFF
cmake --build "$root/build" --config Release --parallel 2
bash "$root/models/download-ggml-model.sh" small
git -C "$root" rev-parse HEAD > "$root/SOURCE_COMMIT.txt"
sha256sum "$root/models/ggml-small.bin" > "$root/MODEL_SHA256.txt"
echo "Configure whisper_executable as $root/build/bin/whisper-cli"
echo "Configure whisper_model as $root/models/ggml-small.bin"
