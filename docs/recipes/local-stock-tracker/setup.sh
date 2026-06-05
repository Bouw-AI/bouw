#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="${ROOT}/.venv"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ ! -d "${VENV}" ]]; then
  "${PYTHON_BIN}" -m venv "${VENV}"
fi

source "${VENV}/bin/activate"
python -m pip install --upgrade pip >/dev/null
python -m pip install -r "${ROOT}/requirements.txt"
echo "Python environment ready in ${VENV}"
