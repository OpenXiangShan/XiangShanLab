#!/usr/bin/env bash
# One-click replay check for OpenXiangShan/XiangShan#6265.
#
# Builds a minimal bare-metal program containing only
#     ld x1, 1(x0)      (effective address 0x1, an 8-byte load in the MMIO PMA region)
# and runs it through the already-built XiangShan DUT + bundled NEMU reference to
# confirm the DUT/REF exception-classification divergence:
#     XiangShan -> load access fault,      mcause = 5
#     NEMU      -> load address misaligned mcause = 4
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
WORK_ROOT="${WORK_ROOT:-$SCRIPT_DIR}"
REPLAY="$SCRIPT_DIR/../replay-work"
EMU="${EMU:-$REPLAY/xs-env/XiangShan/build/emu}"
REF="${REF:-$REPLAY/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so}"

CROSS="${CROSS:-riscv64-unknown-elf}"
SRC="$WORK_ROOT/test.S"
LD="$WORK_ROOT/link.ld"
ELF="$WORK_ROOT/test.elf"
DUMP="$WORK_ROOT/test.dump"
LOG="$WORK_ROOT/run.log"
MAX_CYCLES="${MAX_CYCLES:-200000}"

run() { printf '\n==> %s\n' "$*"; "$@"; }

for tool in "$CROSS"-gcc "$CROSS"-objdump "$CROSS"-ld; do
    command -v "$tool" >/dev/null 2>&1 || {
        printf 'error: tool not found: %s\n' "$tool" >&2
        exit 1
    }
done
[[ -x "$EMU" ]] || { printf 'error: DUT emu not found at %s\n' "$EMU" >&2; exit 1; }
[[ -f "$REF" ]] || { printf 'error: REF not found at %s\n' "$REF" >&2; exit 1; }

set -e
run "$CROSS"-gcc -nostdlib -nostartfiles -march=rv64im_zicsr -mabi=lp64 \
    -T "$LD" -o "$ELF" "$SRC"
run "$CROSS"-objdump -d "$ELF" > "$DUMP"

echo
echo '===== disassembly ====='
"$CROSS"-objdump -d "$ELF"
echo '======================='

printf 'running DUT(%s) vs REF ...\n' "$EMU"
set +e
timeout "${TIMEOUT:-120}" "$EMU" -C "$MAX_CYCLES" \
    -i "$ELF" --diff "$REF" > "$LOG" 2>&1
rc=$?
set -e

echo
echo '===== run.log (tail) ====='
tail -n 30 "$LOG"
echo '=========================='
echo "emu exit code: $rc"

if grep -q "mcause different" "$LOG"; then
    echo
    echo "REPRODUCED: Difftest reports a mcause mismatch."
    grep -m1 "mcause different" "$LOG" || true
    exit 0
else
    echo
    echo "No 'mcause different' found in run.log."
    exit 1
fi
