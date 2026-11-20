#!/usr/bin/env bash
# Run the OpenXiangShan/XiangShan#6668 cases against one emu build.
#
# usage: run_cases.sh <emu> <out-dir> [emu-args...]
#
# For every case the script runs three configurations:
#   nodiff : --no-diff            (DUT only, assertions still enabled)
#   nemu   : --diff <NEMU so>     (Difftest against the pinned NEMU)
#   spike  : --diff <spike so>    (Difftest against the pinned Spike)
# Results:
#   ABORT          the RTL assertion / XSError fired
#   DIFF-MISMATCH  Difftest reported a mismatch
#   COMPLETED      ran to the instruction limit with no abort and no mismatch
#   TIMEOUT        wall-clock timeout
set -uo pipefail

EMU="${1:?usage: run_cases.sh <emu> <out-dir> [emu-args...]}"
OUT="${2:?usage: run_cases.sh <emu> <out-dir> [emu-args...]}"
shift 2 || true
EXTRA=("$@")

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
NEMU="${NEMU:-${HERE}/../XiangShan/ready-to-run/riscv64-nemu-interpreter-so}"
SPIKE="${SPIKE:-${HERE}/../XiangShan/ready-to-run/riscv64-spike-so}"
CASES=(xs_csrr_x0_vl x0_readback read_vlenb x0_vtype_control)
MODES=(nodiff nemu)

mkdir -p "$OUT"

classify() {
    local log="$1" rc="$2"
    if grep -q "Assertion failed\|rfWen cannot be 1\|pdest for \$0 should be 0" "$log"; then
        echo ABORT
    elif grep -q "different pc\|different reg\|mismatch goldenMem\|MISMATCH" "$log"; then
        echo DIFF-MISMATCH
    elif [ "$rc" -eq 124 ]; then
        echo TIMEOUT
    elif [ "$rc" -eq 0 ]; then
        echo COMPLETED
    else
        echo "OTHER(rc=$rc)"
    fi
}

for case_name in "${CASES[@]}"; do
    elf="${HERE}/${case_name}.elf"
    [[ -f "$elf" ]] || { echo "missing $elf" >&2; continue; }
    for mode in "${MODES[@]}"; do
        args=(-i "$elf" -C 20000 -I 60)
        case "$mode" in
            nodiff) args+=(--no-diff) ;;
            nemu)   args+=(--diff "$NEMU") ;;
            spike)  args+=(--diff "$SPIKE") ;;
        esac
        log="${OUT}/${case_name}.${mode}.log"
        timeout 900 "$EMU" "${args[@]}" "${EXTRA[@]}" >"$log" 2>&1
        rc=$?
        echo "$rc" >"${log%.log}.rc"
        printf '%-20s %-7s rc=%-3s %s\n' "$case_name" "$mode" "$rc" "$(classify "$log" "$rc")"
    done
done
