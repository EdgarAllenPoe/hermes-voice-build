#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
export PYTHONPATH="$root/receiver${PYTHONPATH:+:$PYTHONPATH}"
export PYTHONDONTWRITEBYTECODE=1
cd "$root"
python3 -m unittest discover -s tests -p 'test_*.py' -v
