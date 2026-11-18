#!/usr/bin/env bash
set -euo pipefail
# stage-corpus.sh BENCH_DIR SCRATCH OUT_DIR
#
# Cross-build the cpu-sec-bench corpus for the riscv64 token and stage it
# under OUT_DIR, never writing into the user's BENCH_DIR checkout:
#   OUT_DIR/configure.json  the tracked bench registry, copied verbatim
#   OUT_DIR/manifest.json   verified manifest (units/side/support/skips)
#   OUT_DIR/test/<unit>     one test/<program>__<key> file per build unit
#   OUT_DIR/test/<side>     the registry-referenced side files
#   OUT_DIR/test/<support>  the declared plain-name support files
#
# Ported from cpu-sec-test-env scripts/common/stage_corpus.sh (2026-10-31),
# specialised to this venue's single riscv64 token and stripped of the lane
# lock: there is no shared bench worktree here - every build happens in
# SCRATCH, a copy of the bench tree wiped at the start of every run, so a
# second concurrent invocation would only waste CPU, not corrupt anything.
#
# The invariant is unchanged: the runner owns corpus keying and skip
# handling.  This script never recomputes a key, never derives a registry and
# never rewrites a skip token - it builds a HOST runner with the venue's skip
# token baked in, runs `./run-test corpus make-only exhausted-run` in the
# scratch tree with the cross toolchain in the environment, and verifies the
# outcome against the tracked registry (venue/corpus-manifest.py).
#
# Inputs from the environment (exported by the driver): CXX ASM OBJDUMP (the
# cross tools).  The venue's pins come from venue/pins.sh, sourced below.
# Inputs from argv: the three directories, all absolute.

if [ $# -ne 3 ]; then
  echo "usage: $0 BENCH_DIR SCRATCH OUT_DIR" >&2
  exit 2
fi
BENCH_DIR=$1 SCRATCH=$2 OUT_DIR=$3
VENUE=$(cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=pins.sh
. "$VENUE/pins.sh"

: "${CXX:?CXX must be exported (the cross compiler)}"
: "${ASM:?ASM must be exported}"
: "${OBJDUMP:?OBJDUMP must be exported}"

di() { echo "stage-corpus: $*" >&2; }

for d in "$OUT_DIR" "$SCRATCH"; do
  case "$d" in
    "$BENCH_DIR"|"$BENCH_DIR"/*) di "output dir '$d' must not live inside the bench checkout"; exit 2 ;;
  esac
done

# ---- [1/5] scratch tree: the worktree copied out, never built in place ----
echo "== stage-corpus: scratch tree -> $SCRATCH"
rm -rf "$SCRATCH"
mkdir -p "$SCRATCH"
tar -C "$BENCH_DIR" \
    --exclude=./.git --exclude=./tmp --exclude=./test --exclude=./trace-log \
    -cf - . | tar -C "$SCRATCH" -xf -
# Nothing generated may be inherited from the checkout: an earlier run's
# run-test, objects or libcfi.so in the copy would be staged as if this run
# had built them (the build products are gitignored, so a clean `git status`
# says nothing about them).  The clean recipe is `-rm -rf ...` -- the leading
# dash makes make ignore a failing `rm` -- so its exit status cannot carry the
# guarantee; assert the generated roots are actually gone instead.
env -u MAKEFLAGS -u MFLAGS -u ARCH make -C "$SCRATCH" clean >/dev/null
# The clean list is ARCH-scoped (lib/$(ARCH)/...), so a checkout cross-built
# before keeps lib/riscv64/*.o across this host-ARCH clean -- exactly the
# objects the corpus build would reuse.  Remove them directly; the assertion
# below stays the observable guarantee.
rm -f "$SCRATCH"/lib/*/assembly.o "$SCRATCH"/lib/*/indepassembly*.o
[ ! -e "$SCRATCH/run-test" ] || { di "inherited generated artifact $SCRATCH/run-test survived the scratch tree's clean"; exit 1; }
[ ! -e "$SCRATCH/lib/common/libcfi.so" ] || { di "inherited generated artifact $SCRATCH/lib/common/libcfi.so survived the scratch tree's clean"; exit 1; }
stale_obj="$(find "$SCRATCH/lib" -name '*.o' -print -quit)"
[ -z "$stale_obj" ] || { di "inherited generated artifact $stale_obj survived the scratch tree's clean"; exit 1; }

# ---- [2/5] host runner, the venue's skip token baked in ----
# The host leg must not inherit the cross toolchain or stray make variables:
# the Makefile's `?=` honours the environment, and MAKEFLAGS would carry ARCH
# into the child makes.  `env -u` restores the Makefile's own defaults for
# this one invocation; CSB_TARGET_ARCH on the command line wins over the
# Makefile's own assignment, so the runner decides skips by the token the
# registry's rows are written in.
echo "== stage-corpus: host runner (token $BENCH_TOKEN)"
(cd "$SCRATCH" && env -u CXX -u CC -u ASM -u OBJDUMP -u CROSS_COMPILE -u READELF \
    -u MAKEFLAGS -u MFLAGS -u ARCH make -B run-test CSB_TARGET_ARCH="$BENCH_TOKEN" >/dev/null)
machine=$(readelf -h "$SCRATCH/run-test" | awk -F: '/Machine:/{gsub(/^[ \t]+/,"",$2); print $2}')
case "$machine" in
  *X86-64*) ;;
  *) di "the host runner is '$machine', not x86-64 - the host scheduler build went cross"; exit 1 ;;
esac

# ---- [3/5] corpus build: the runner owns keying and skip handling ----
# ARCH/CXX/ASM/OBJDUMP ride the ENVIRONMENT (the scheduler spawns its child
# makes without extra arguments); MAKEFLAGS/MFLAGS are cleared so no stray
# ARCH leaks in.  Bare `objdump` (the bench's opcode-extraction helpers call
# it) must resolve to the cross objdump: put a shim dir first on PATH.
echo "== stage-corpus: corpus build (ARCH=$BENCH_ARCH)"
LOG_DIR=$(dirname "$OUT_DIR")
LOG="$LOG_DIR/corpus-build.log"
mkdir -p "$LOG_DIR"
SHIM_DIR="$LOG_DIR/shim"
target=$(command -v "${OBJDUMP%-objdump}-objdump") || { di "${OBJDUMP%-objdump}-objdump not found on PATH"; exit 1; }
mkdir -p "$SHIM_DIR"
ln -sfn "$target" "$SHIM_DIR/objdump"
cd "$SCRATCH"
unset MAKEFLAGS MFLAGS ARCH
export ARCH="$BENCH_ARCH"
export PATH="$SHIM_DIR:$PATH"
set +e
./run-test corpus make-only exhausted-run 2>&1 | tee "$LOG"
rc=${PIPESTATUS[0]}
set -e
[ "$rc" -eq 0 ] ||
  { di "the corpus run './run-test corpus make-only exhausted-run' (ARCH=$BENCH_ARCH) exited $rc - see $LOG"; exit 1; }
[ -f results.json ] || { di "the corpus run wrote no results.json - see $LOG"; exit 1; }

# A record the venue does not skip must not fail to build: the tracked
# registry declares every platform skip explicitly, so any make-status here
# is an undeclared build failure and a hard staging error.
if grep -q '"make-status"' results.json; then
  python3 -c 'import json,sys; [print("  build failed: " + k + " (make-status " + str(v.get("make-status")) + ")", file=sys.stderr) for k,v in json.load(open(sys.argv[1])).items() if "make-status" in v]' results.json >&2 || true
  di "the corpus run reports undeclared build failures (make-status) - see $LOG"
  exit 1
fi

# ---- [4/5] stage OUT_DIR from the manifest plan ----
# The plan comes from the tracked registry and the venue's skip token, so the
# staged set is exactly the expected registry-minus-skips set: keyed units,
# registry side files, and the declared plain-name support files.  Non-unit
# build byproducts (plain binaries, objects, the scratch sys_info) are never
# staged.
mapfile -t PLAN < <(python3 "$VENUE/corpus-manifest.py" \
  --registry "$BENCH_DIR/configure.json" --lane "$BENCH_TOKEN" --print-plan)
[ "${#PLAN[@]}" -gt 0 ] || { di "the registry enumerates no stageable artifact for token $BENCH_TOKEN"; exit 1; }

echo "== stage-corpus: staging -> $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "$BENCH_DIR/configure.json" "$OUT_DIR/configure.json"
for row in "${PLAN[@]}"; do
  kind=${row%%$'\t'*}
  rel=${row#*$'\t'}
  [ -f "$SCRATCH/$rel" ] ||
    { di "$rel ($kind) was not produced by the corpus build - see $LOG"; exit 1; }
  mkdir -p "$OUT_DIR/$(dirname "$rel")"
  install -m 0755 "$SCRATCH/$rel" "$OUT_DIR/$rel"
done

# ---- [5/5] verify the staged set and publish the manifest ----
# corpus-manifest.py publishes manifest.json only when every check passes, so
# its presence is the deploy's proof that staging completed.  The bench
# provenance tolerates a non-git checkout: "unknown" is recorded, never
# guessed.
bench_commit=$(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)
bench_tree=$(git -C "$BENCH_DIR" describe --always --dirty 2>/dev/null || echo unknown)
python3 "$VENUE/corpus-manifest.py" \
  --registry "$BENCH_DIR/configure.json" \
  --lane "$BENCH_TOKEN" \
  --tree "$OUT_DIR" \
  --out "$OUT_DIR/manifest.json" \
  --bench-commit "$bench_commit" \
  --bench-tree "$bench_tree" \
  --toolchain "$("$CXX" --version | head -n1)" \
  --objdump "$("$OBJDUMP" --version | head -n1)" ||
  { di "the staged corpus failed verification - see the PASS/FAIL lines above"; exit 1; }

echo "stage-corpus: ok - $(find "$OUT_DIR/test" -type f | wc -l) files staged under $OUT_DIR/test"
