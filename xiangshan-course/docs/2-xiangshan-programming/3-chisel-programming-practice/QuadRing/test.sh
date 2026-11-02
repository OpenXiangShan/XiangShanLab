#!/usr/bin/env bash
# Compile, test, generate, and lint RTL from the repository root.
set -euo pipefail

if (( $# > 1 )) || [[ "${1:-}" != "" && "${1:-}" != "--long" ]]; then
  echo "Usage: $0 [--long]" >&2
  exit 2
fi
if [[ "${1:-}" == "--long" ]]; then
  export QUADRING_LONG_TESTS=1
fi

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

lint_rtl() (
  echo "Linting $1 ($2)"
  cd -- "$1"
  verilator --lint-only --top-module "$2" -f filelist.f
)

# Close build handles after each phase, including on NFS workspaces.
./mill --no-daemon QuadRing.compile
./mill --no-daemon QuadRing.test
./mill --no-daemon QuadRing.runMain noc.RingMain --target-dir generated/ring
./mill --no-daemon QuadRing.runMain chi.ChiNoCMain --target-dir generated/chi
./mill --no-daemon QuadRing.runMain chi.ChiSystemMain --target-dir generated/system
./mill --no-daemon QuadRing.runMain chi.ChiSystemMain --coherent --target-dir generated/system-coherent

lint_rtl generated/ring Ring
lint_rtl generated/chi ChiNoCTop
lint_rtl generated/system ChiSystem
lint_rtl generated/system-coherent ChiSystem
