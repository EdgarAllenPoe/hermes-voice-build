#!/usr/bin/env bash
# ONLINE preparation. Downloads official Gradle and checks its published SHA-256.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
version=8.11.1
mkdir -p "$root/.tools"; cd "$root/.tools"
base="https://services.gradle.org/distributions/gradle-${version}-bin.zip"
curl --fail --location --proto '=https' --tlsv1.2 "$base" -o "gradle-${version}-bin.zip"
curl --fail --location --proto '=https' --tlsv1.2 "$base.sha256" -o gradle.sha256
expected="$(tr -d '[:space:]' < gradle.sha256)"
[[ "$expected" =~ ^[0-9a-fA-F]{64}$ ]] || { echo 'Invalid published checksum'; exit 1; }
printf '%s  %s\n' "$expected" "gradle-${version}-bin.zip" | sha256sum --check -
unzip -q -o "gradle-${version}-bin.zip"
echo "Run: $root/.tools/gradle-$version/bin/gradle -p $root/android :app:assembleDebug"
echo 'Requires JDK 17 and Android SDK platform 36/build-tools 35.0.0, installed separately.'
