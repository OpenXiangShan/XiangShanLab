#!/usr/bin/env bash
# Rebuild the three self-written cases of XiangShan issue #6668.
#
# usage: build_cases.sh [toolchain-prefix]     e.g. riscv64-unknown-elf-
#
# The issue's own attachment (xs_csrr_x0_vl.elf) is kept byte-for-byte as
# published and is not rebuilt here.
#
# The rebuild uses the same sources and linker script as the committed ELFs.
# The instruction stream is identical (objdump of _start matches line by line),
# but the ELF file bytes depend on the toolchain version (section counts,
# symbol-table shape); the committed ELFs remain the reference artefacts and
# their hashes are recorded in ../SHA256SUMS.
#
# The two-step compile below (fixed .o name, then link) makes the output
# reproducible for a given output directory; a single gcc call would embed a
# random /tmp/ccXXXXXX.o name into .strtab.
set -euo pipefail

CROSS="${1:-riscv64-unknown-elf-}"
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUT="${OUT:-$HERE}"

command -v "${CROSS}gcc" >/dev/null || {
    echo "toolchain not found: ${CROSS}gcc (pass the prefix as the first argument)" >&2
    exit 2
}

mkdir -p "$OUT"
for c in x0_readback read_vlenb x0_vtype_control; do
    "${CROSS}gcc" -march=rv64gcv -mabi=lp64d -c -o "$OUT/$c.o" "$HERE/$c.S"
    "${CROSS}gcc" -march=rv64gcv -mabi=lp64d -nostdlib -nostartfiles \
        -T "$HERE/link.ld" -o "$OUT/$c.elf" "$OUT/$c.o"
    rm -f "$OUT/$c.o"
    "${CROSS}objdump" -d "$OUT/$c.elf" >"$OUT/$c.dis"
    printf '%-20s %s\n' "$c.elf" "$(sha256sum "$OUT/$c.elf" | cut -d' ' -f1)"
done
