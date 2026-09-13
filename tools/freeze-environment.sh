#!/usr/bin/env bash
# Record the installed versions AFTER both target builds succeed.
# This script does not copy caches or claim offline completeness.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"; out="$root/config/build-environment.txt"
{
 date -u '+Recorded UTC %Y-%m-%dT%H:%M:%SZ'; uname -a
 python3 --version; java -version 2>&1
 command -v pio >/dev/null && pio --version || true
 command -v pio >/dev/null && pio pkg list -d "$root/firmware" || true
 python3 -m pip freeze 2>/dev/null || true
 for d in "$HOME"/.platformio/platforms/*; do
  [[ -d "$d/.git" ]] && { printf 'Platform %s: ' "$d"; git -C "$d" rev-parse HEAD; } || true
 done
 [[ -x "$root/.tools/gradle-8.11.1/bin/gradle" ]] && "$root/.tools/gradle-8.11.1/bin/gradle" --version || true
} > "$out"
echo "Wrote $out. Follow docs/07-offline.md to archive caches and prove an offline rebuild."
