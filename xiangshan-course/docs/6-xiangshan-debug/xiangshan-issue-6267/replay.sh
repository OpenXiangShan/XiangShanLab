#!/usr/bin/env bash
set -Eeuo pipefail

ISSUE_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd -- "${ISSUE_DIR}"

XS_ENV_SHA='33d5f6f611d15a65c6194290fa62caf0c0c27f41'
XS_SHA='b90dbba40d16d54f2814870ff8b4d8809a41e680'
XS_ENV_URL='https://github.com/OpenXiangShan/xs-env.git'
XS_URL='https://github.com/OpenXiangShan/XiangShan.git'
TEST_INPUT="${ISSUE_DIR}/case/seeds_565_.elf"
INPUT_SHA='2688f1ca2fbf7b92eb38bb5f237d2d461297e3eec49b6e28fdc343579ba7e053'
CONFIG='DefaultConfig'
JOBS=${JOBS:-$(( $(nproc) - 2 ))}
(( JOBS > 0 )) || JOBS=1

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'Missing prerequisite: %s\n' "$1" >&2
    exit 127
  }
}

for cmd in bash git make java mill verilator nproc sha256sum tee; do
  need_cmd "$cmd"
done

[[ -f "${TEST_INPUT}" ]] || { printf 'Missing prepared input: %s\n' "${TEST_INPUT}" >&2; exit 1; }
[[ "$(sha256sum "${TEST_INPUT}" | cut -d' ' -f1)" == "${INPUT_SHA}" ]] || {
  printf 'Input checksum mismatch: expected %s\n' "${INPUT_SHA}" >&2
  exit 1
}

if [[ ! -d xs-env ]]; then
  git clone --no-recurse-submodules "${XS_ENV_URL}" xs-env
fi
git -C xs-env checkout --detach "${XS_ENV_SHA}"

XS_DIR="${ISSUE_DIR}/xs-env/XiangShan"
if [[ ! -e "${XS_DIR}/.git" ]]; then
  [[ ! -d "${XS_DIR}" ]] || rmdir "${XS_DIR}"
  git clone --no-recurse-submodules "${XS_URL}" "${XS_DIR}"
fi
git -C "${XS_DIR}" fetch --no-tags origin "${XS_SHA}"
git -C "${XS_DIR}" checkout --detach "${XS_SHA}"

# Initialize xs-env modules without changing its older XiangShan gitlink.
git -C xs-env submodule update --init -- NEMU nexus-am NutShell DRAMsim3

# Initialize XiangShan's direct submodules before their nested dependencies.
git -C "${XS_DIR}" submodule update --init

# XiangShan's Makefile documents these required nested dependencies.
git -C "${XS_DIR}/rocket-chip" submodule update --init cde hardfloat
git -C "${XS_DIR}/XSCache" submodule update --init OpenNCB

source "${ISSUE_DIR}/xs-env/env.sh"

EMU="${XS_DIR}/build/emu"
NEMU_SO="${XS_DIR}/ready-to-run/riscv64-nemu-interpreter-so"
make -C "${XS_DIR}" emu CONFIG="${CONFIG}" EMU_TRACE=fst -j"${JOBS}"
[[ -x "${EMU}" ]] || { printf 'Emulator was not produced: %s\n' "${EMU}" >&2; exit 1; }
[[ -f "${NEMU_SO}" ]] || { printf 'Pinned reference library is missing: %s\n' "${NEMU_SO}" >&2; exit 1; }

exec > >(tee "${ISSUE_DIR}/replay.log") 2>&1
exec "${EMU}" -i "${TEST_INPUT}" --diff "${NEMU_SO}" \
  --dump-wave-full --wave-path "${ISSUE_DIR}/replay.fst"
