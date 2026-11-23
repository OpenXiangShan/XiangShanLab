#!/usr/bin/env bash
# Run the OpenXiangShan/XiangShan#6668 cases against one emu build.
#
# usage: run_cases.sh <emu> <out-dir> [emu-args...]
#
#   <emu>      path to build/emu (any kunminghu-v3 build works)
#   <out-dir>  directory for one log + one .rc per run, plus summary.txt
#
# Every case is run twice:
#   nodiff : --no-diff            (DUT only, assertions still enabled)
#   nemu   : --diff <NEMU so>     (Difftest against the pinned NEMU)
#
# The NEMU shared object is taken from $NEMU, or discovered in this order:
#   $NOOP_HOME/ready-to-run/riscv64-nemu-interpreter-so
#   $PWD/ready-to-run/riscv64-nemu-interpreter-so
#   <case dir>/../../ready-to-run/riscv64-nemu-interpreter-so   (this package
#       dropped into a XiangShan checkout)
#   <case dir>/../XiangShan/ready-to-run/...                    (workdir layout)
#
# Result column:
#   ABORT          the RTL assertion / XSError fired
#   DIFF-MISMATCH  Difftest reported a mismatch
#   COMPLETED      reached the instruction limit, no abort, no mismatch
#   TIMEOUT        wall-clock timeout
#   OTHER(rc=N)    anything else (see the log)
set -uo pipefail

EMU="${1:?usage: run_cases.sh <emu> <out-dir> [emu-args...]}"
OUT="${2:?usage: run_cases.sh <emu> <out-dir> [emu-args...]}"
shift 2 || true
EXTRA=("$@")

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
EMU="$(cd -- "$(dirname -- "$EMU")" && pwd)/$(basename -- "$EMU")"
[[ -x "$EMU" ]] || { echo "emu not executable: $EMU" >&2; exit 2; }

# resolve_so <explicit path or empty> <so name>
resolve_so() {
    local explicit="$1" name="$2" c
    for c in \
        "$explicit" \
        "${NOOP_HOME:-}/ready-to-run/$name" \
        "$PWD/ready-to-run/$name" \
        "$HERE/../../ready-to-run/$name" \
        "$HERE/../XiangShan/ready-to-run/$name" ; do
        [[ -n "$c" && -f "$c" ]] && { echo "$c"; return 0; }
    done
    return 1
}

if NEMU="$(resolve_so "${NEMU:-}" riscv64-nemu-interpreter-so)"; then
    :
else
    echo "cannot find riscv64-nemu-interpreter-so; set NEMU=/path/to/riscv64-nemu-interpreter-so" >&2
    exit 2
fi
# Spike is not used by this case: the bundled spike-so reset vector (0x10000000)
# does not match these images (linked at 0x80000000).
SPIKE="$(resolve_so "${SPIKE:-}" riscv64-spike-so || true)"

CASES=(xs_csrr_x0_vl x0_readback read_vlenb x0_vtype_control)
MODES=(nodiff nemu)
LIMIT_C="${LIMIT_C:-20000}"
LIMIT_I="${LIMIT_I:-60}"

mkdir -p "$OUT"

classify() {
    local log="$1" rc="$2"
    if grep -qE "Assertion failed|rfWen cannot be 1|pdest for \\\$0 should be 0" "$log"; then
        echo ABORT
    elif grep -qE "different pc|different reg|different csr|goldenMem mismatch|\bMISMATCH\b|mismatch at" "$log"; then
        echo DIFF-MISMATCH
    elif [ "$rc" -eq 124 ]; then
        echo TIMEOUT
    elif [ "$rc" -eq 0 ]; then
        echo COMPLETED
    else
        echo "OTHER(rc=$rc)"
    fi
}

counters() {
    local log="$1"
    grep -oE "instrCnt = [0-9,]+, cycleCnt = [0-9,]+" "$log" | tail -1
}

printf 'emu:  %s\nnemu: %s\n' "$EMU" "$NEMU"
: >"$OUT/summary.txt"
for case_name in "${CASES[@]}"; do
    elf="${HERE}/${case_name}.elf"
    [[ -f "$elf" ]] || { echo "missing $elf" >&2; continue; }
    for mode in "${MODES[@]}"; do
        args=(-i "$elf" -C "$LIMIT_C" -I "$LIMIT_I")
        case "$mode" in
            nodiff) args+=(--no-diff) ;;
            nemu)   args+=(--diff "$NEMU") ;;
        esac
        log="${OUT}/${case_name}.${mode}.log"
        timeout 900 "$EMU" "${args[@]}" "${EXTRA[@]}" >"$log" 2>&1
        rc=$?
        echo "$rc" >"${log%.log}.rc"
        result="$(classify "$log" "$rc")"
        cnt="$(counters "$log")"
        line="$(printf '%-20s %-7s rc=%-3s %-14s %s' "$case_name" "$mode" "$rc" "$result" "$cnt")"
        printf '%s\n' "$line"
        printf '%s\n' "$line" >>"$OUT/summary.txt"
    done
done
printf '\nsummary: %s/summary.txt\n' "$OUT"
