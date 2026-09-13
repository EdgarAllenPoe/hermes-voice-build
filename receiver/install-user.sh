#!/usr/bin/env bash
set -euo pipefail
[[ $(id -u) != 0 ]] || { echo 'Run as the regular Hermes user, not root.' >&2; exit 1; }
HERE=$(cd -- "$(dirname -- "$0")" && pwd)
python3 -c 'import sys; assert sys.version_info >= (3,10), "Python 3.10+ required"'
umask 077
mkdir -p "$HOME/.local/share/hermes-voice/receiver" "$HOME/.config/hermes-voice" "$HOME/.config/systemd/user"
cp -R "$HERE/hvbridge" "$HOME/.local/share/hermes-voice/receiver/"
[[ -f "$HOME/.config/hermes-voice/config.json" ]] || cp "$HERE/config.example.json" "$HOME/.config/hermes-voice/config.json"
[[ -f "$HOME/.config/hermes-voice/token" ]] || python3 -c 'import secrets; print(secrets.token_urlsafe(32))' > "$HOME/.config/hermes-voice/token"
chmod 600 "$HOME/.config/hermes-voice/"{config.json,token}
cp "$HERE/systemd/"*.service "$HOME/.config/systemd/user/"
systemctl --user daemon-reload
echo 'Installed files only. Neither service has been started.'
echo 'Edit ~/.config/hermes-voice/config.json; read docs/04-server.md next.'
