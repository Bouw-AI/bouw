#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="${ROOT}/.venv"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ ! -d "${VENV}" ]]; then
  "${ROOT}/setup.sh"
fi

source "${VENV}/bin/activate"

exec python "${ROOT}/stock_tracker.py" --config "${1:-${ROOT}/config.json}"
