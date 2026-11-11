#!/usr/bin/env bash
# run_cpu_sec_bench_nemu.sh - run the cpu-sec-bench memory-security corpus
# against XiangShan's NEMU ISA simulator, then summarize the published stamp.
#
# This is the runner of the XiangShanLab memory-safety registration
# (external-contribution-project/memory-safety). It is self-contained: the
# only inputs are a cpu-sec-bench checkout (anywhere on disk; it is never
# written to) and a built riscv64 system-mode NEMU interpreter (anywhere).
# The venue package under venue/ does the work:
#
#   venue/stage-corpus.sh   cross-build the corpus in a scratch copy
#   venue/build-image.sh    assemble the bootable guest image (pinned inputs
#                           are cloned/downloaded into the work dir once)
#   venue/boot.sh           boot the image under NEMU, extract the results
#
# Nothing here assumes a particular workspace layout: the bench and NEMU
# paths are user inputs, and every scratch artifact stays under --work.
#
# Usage:
#   ./run_cpu_sec_bench_nemu.sh --bench DIR [--nemu PATH] [--work DIR] [MODE]
#
#     --bench DIR   the cpu-sec-bench checkout (required; env CPU_SEC_BENCH)
#     --nemu PATH   the NEMU interpreter binary, or its checkout directory
#                   (env NEMU_BIN > NEMU_DIR > NEMU_HOME; without one of
#                   these the script explains how to build NEMU)
#     --work DIR    build/results root (default: <this script>/build;
#                   env WORK_DIR). Cleaned by --clean.
#
#   MODE is one of (default: full run):
#     (none)          stage corpus + build image + boot + summary
#     --deploy-only   stage corpus + build image (the slow legs)
#     --run-only      boot the already-built image + summary
#     --summary       summarize the newest published stamp only
#     --clean         remove the whole work dir (guarded)
#     --help          this text
#
# Bounds: each deploy leg carries a $DEPLOY_TIMEOUT outer bound (default
# 7200 s; a cold first run downloads pinned sources and builds the guest
# kernel - roughly an hour); the boot leg carries NEMU_TIMEOUT (default
# 3600 s) inside venue/boot.sh plus a 120 s backstop here. A boot timeout
# publishes nothing; any other boot failure publishes a boot.log-only
# evidence stamp; a complete run publishes the full stamp.
#
# Prebuilt image (optional): setting CPU_SEC_BENCH_IMAGE_URL and
# CPU_SEC_BENCH_REGISTRY_URL replaces both deploy legs with a verified
# download of a published image pair, so the first results arrive in
# minutes. The image bundles the corpus and must be published per bench
# commit (CPU_SEC_BENCH_IMAGE_BENCH_COMMIT, when set, is checked against
# --bench; CPU_SEC_BENCH_IMAGE_SHA256 / CPU_SEC_BENCH_REGISTRY_SHA256 pin
# the expected bytes, else "<url>.sha256" is fetched).
#
# Local seeds (optional): WORKLOAD_BUILDER_LOCAL, OPENSBI_LOCAL and
# LIB_CHECKPOINT_ALPHA_LOCAL point at existing checkouts of the pinned
# upstreams; they are cloned into the work dir as seeds - the seed trees
# themselves are never modified - and the pinned commit is fetched from the
# network only if the seed lacks it. CPU_SEC_BENCH_SEED_DIR is searched for
# the pinned kernel and busybox tarballs (sha256-verified before use). With
# all seeds present the deploy runs offline; without them everything is
# downloaded as usual.
#
# One run per work dir: every mutating mode (full/deploy-only/run-only/clean)
# takes an advisory flock on <work>/.lock and a second concurrent run fails
# fast naming the holder.
#
# Exit codes: 0 ok; the failing leg's code (124 = timeout, nothing
# published); 2 usage/refusal.

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
VENUE="$SCRIPT_DIR/venue"
# shellcheck source=venue/pins.sh
. "$VENUE/pins.sh"

usage() {
    sed -n '2,63p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

MODE=full
BENCH_ARG=${CPU_SEC_BENCH:-}
NEMU_ARG=${NEMU_BIN:-${NEMU_DIR:-${NEMU_HOME:-}}}
WORK_ARG=${WORK_DIR:-}
while [ $# -gt 0 ]; do
    case "$1" in
        --bench)   [ $# -ge 2 ] || { echo "--bench needs a directory" >&2; usage 2; }; BENCH_ARG=$2; shift 2 ;;
        --nemu)    [ $# -ge 2 ] || { echo "--nemu needs a path" >&2; usage 2; }; NEMU_ARG=$2; shift 2 ;;
        --work)    [ $# -ge 2 ] || { echo "--work needs a directory" >&2; usage 2; }; WORK_ARG=$2; shift 2 ;;
        --deploy-only|--run-only|--summary|--clean)
            [ "$MODE" = full ] || { echo "select only one mode (got both, incl. $1)" >&2; usage 2; }
            MODE=${1#--}; shift ;;
        --help|-h) usage 0 ;;
        *) echo "unknown argument: $1" >&2; usage 2 ;;
    esac
done

# ---- resolve the bench checkout (not needed for a benchless --clean/--summary) ----
if { [ "$MODE" = clean ] || [ "$MODE" = summary ]; } && [ -z "$BENCH_ARG" ]; then
    BENCH_DIR=""
else
    [ -n "$BENCH_ARG" ] || { echo "no cpu-sec-bench checkout given - pass --bench DIR (or export CPU_SEC_BENCH)" >&2; usage 2; }
    BENCH_DIR=$(cd -- "$BENCH_ARG" 2>/dev/null && pwd) || {
        echo "cpu-sec-bench not found at $BENCH_ARG" >&2
        echo "clone it first:  git clone https://github.com/comparch-security/cpu-sec-bench" >&2
        exit 2
    }
    for f in configure.json Makefile lib scheduler/run-test.cpp script; do
        [ -e "$BENCH_DIR/$f" ] || {
            echo "$BENCH_DIR does not look like a cpu-sec-bench checkout ($f missing)" >&2
            exit 2
        }
    done
fi

# ---- resolve the work dir ----
[ -n "$WORK_ARG" ] || WORK_ARG="$SCRIPT_DIR/build"
case "$WORK_ARG" in
    /*) ;;
    *) WORK_ARG="$PWD/$WORK_ARG" ;;
esac
WORK=$(cd -- "$(dirname -- "$WORK_ARG")" 2>/dev/null && pwd)/$(basename -- "$WORK_ARG") || {
    echo "cannot use work dir parent of $WORK_ARG" >&2; exit 2;
}
case "$WORK" in
    "/"|"$HOME") echo "refusing work dir '$WORK'" >&2; exit 2 ;;
    "$SCRIPT_DIR"|"$VENUE") echo "refusing work dir '$WORK' (it would swallow this project)" >&2; exit 2 ;;
esac
if [ -n "$BENCH_DIR" ]; then
    case "$WORK" in
        "$BENCH_DIR"|"$BENCH_DIR"/*) echo "refusing work dir inside the bench checkout: $WORK" >&2; exit 2 ;;
    esac
fi

# ---- resolve the NEMU interpreter ----
nemu_missing() {
    echo "NEMU interpreter not found ($*) - a built riscv64 system-mode NEMU is required" >&2
    echo "build one, e.g.:" >&2
    echo "  git clone https://github.com/OpenXiangShan/NEMU && cd NEMU" >&2
    echo "  make riscv64-xs_defconfig && make -j\$(nproc)   # -> build/riscv64-nemu-interpreter" >&2
    echo "then pass --nemu <NEMU checkout dir or interpreter binary>" >&2
    exit 1
}
resolve_nemu() {
    if [ -z "$NEMU_ARG" ]; then
        nemu_missing "no --nemu PATH and none of NEMU_BIN/NEMU_DIR/NEMU_HOME is set"
    fi
    if [ -f "$NEMU_ARG" ] && [ -x "$NEMU_ARG" ]; then
        NEMU_BIN=$(cd -- "$(dirname -- "$NEMU_ARG")" && pwd)/$(basename -- "$NEMU_ARG")
    elif [ -d "$NEMU_ARG" ]; then
        NEMU_BIN="$NEMU_ARG/build/riscv64-nemu-interpreter"
        NEMU_BIN=$(cd -- "$(dirname -- "$NEMU_BIN")" 2>/dev/null && pwd)/$(basename -- "$NEMU_BIN") || true
        [ -x "$NEMU_BIN" ] || nemu_missing "no $NEMU_ARG/build/riscv64-nemu-interpreter - build the checkout first (make riscv64-xs_defconfig && make -j\$(nproc))"
    else
        nemu_missing "$NEMU_ARG is neither an executable file nor a directory"
    fi
    NEMU_CHECKOUT=$(dirname -- "$(dirname -- "$NEMU_BIN")")
}

# ---- toolchain preflight: fail before any long leg, naming the gap ----
preflight_tools() {
    local -a tools
    if [ -n "${CPU_SEC_BENCH_IMAGE_URL:-}" ]; then
        # the prebuilt-image path compiles nothing: only the download and
        # boot machinery is needed
        tools=(python3 curl git sha256sum timeout)
    else
        tools=("$RISCV64_CXX" "$RISCV64_CC" "$RISCV64_AS" "$RISCV64_OBJDUMP" \
               g++ make readelf python3 dtc perl curl git sha256sum timeout cpio tar)
    fi
    local missing=() tool
    for tool in "${tools[@]}"; do
        command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
    done
    if [ "${#missing[@]}" -gt 0 ]; then
        echo "missing host tools: ${missing[*]}" >&2
        echo "the cross tools are Ubuntu's gcc-13 riscv64 packages, e.g.:" >&2
        echo "  sudo apt install g++ gcc-riscv64-linux-gnu g++-13-riscv64-linux-gnu \\"
        echo "                   gcc-13-riscv64-linux-gnu binutils-riscv64-linux-gnu \\"
        echo "                   device-tree-compiler cpio curl git python3 perl make" >&2
        exit 1
    fi
    if [ -z "${CPU_SEC_BENCH_IMAGE_URL:-}" ]; then
        local cpio_help
        cpio_help="$(cpio --help 2>/dev/null || true)"
        case "$cpio_help" in
            *--reproducible*) ;;
            *) echo "this host's cpio does not support --reproducible (GNU cpio is required)" >&2; exit 1 ;;
        esac
    fi
}

CXX="$RISCV64_CXX"; CC="$RISCV64_CC"; ASM="$RISCV64_AS"; OBJDUMP="$RISCV64_OBJDUMP"
CROSS_COMPILE="${RISCV64_OBJDUMP%-objdump}-"
READELF="${CROSS_COMPILE}readelf"
command -v "$READELF" >/dev/null 2>&1 || { echo "missing host tool: $READELF" >&2; exit 1; }

OUT="$WORK/platform"
CORPUS="$WORK/corpus"
SCRATCH_BENCH="$WORK/stage-tree"
RESULTS_ROOT="$WORK/results"
export CXX CC ASM OBJDUMP CROSS_COMPILE READELF OUT CORPUS SCRATCH_BENCH BENCH_DIR NEMU_BIN VENUE

newest_stamp() {
    local d best=""
    [ -d "$RESULTS_ROOT" ] || return 1
    for d in "$RESULTS_ROOT"/*/; do
        d=${d%/}
        [ -f "$d/versions.txt" ] || continue
        case "$(basename "$d")" in
            [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]T[0-9][0-9][0-9][0-9][0-9][0-9]Z) ;;
            *) continue ;;
        esac
        if [ -z "$best" ] || [[ "$d" > "$best" ]]; then best=$d; fi
    done
    [ -n "$best" ] && printf '%s\n' "$best"
}

# ---- stamp collection (the publish policy) ----
publish_stamp() { # run_status [run_dir]
    local status=$1 cand=${2:-} stamp dest f
    stamp=$(date -u +%Y%m%dT%H%M%SZ)
    dest="$RESULTS_ROOT/$stamp"
    if [ "$status" = none ]; then
        echo "run aborted on timeout: publishing nothing" >&2
        return 0
    fi
    mkdir -p "$dest"
    if [ -n "$cand" ] && [ -d "$cand" ]; then
        for f in results.json results.dat variables.json boot.log meta.json; do
            [ -f "$cand/$f" ] && cp -f "$cand/$f" "$dest/"
        done
    fi
    [ -f "$dest/boot.log" ] || [ "$status" = none ] || {
        echo "WARNING: no boot.log resolved for the evidence stamp" >&2
    }
    {
        echo "# cpu-sec-bench NEMU venue (XiangShanLab memory-safety)"
        echo "# pins (venue/pins.sh)"
        echo "bench: $BENCH_DIR"
        echo "bench-head: $(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
        echo "bench-tree: $(git -C "$BENCH_DIR" describe --always --dirty 2>/dev/null || echo unknown)"
        echo "nemu-bin: $(realpath "$NEMU_BIN" 2>/dev/null || printf '%s\n' "$NEMU_BIN")"
        echo "nemu-commit: $(git -C "$NEMU_CHECKOUT" rev-parse HEAD 2>/dev/null || echo unknown)"
        echo "nemu-describe: $(git -C "$NEMU_CHECKOUT" describe --always --dirty 2>/dev/null || echo unknown)"
        echo "nemu-sha256: $(sha256sum "$NEMU_BIN" 2>/dev/null | cut -d' ' -f1 || echo unknown)"
        echo "host-uname: $(uname -srmo)"
        echo "corpus-compiler: $("$CXX" --version | head -n1)"
        echo "run-status: $status"
        echo "# pins carried from the verified lane (see venue/pins.sh):"
        echo "#   workload-builder $WB_COMMIT"
        echo "#   opensbi $OPENSBI_COMMIT  libcheckpointalpha $LCA_COMMIT"
        echo "#   linux $KERNEL_VERSION  busybox $BUSYBOX_VERSION"
    } > "$dest/versions.txt"
    echo "collected venue artifacts into $dest"
    if [ "$status" = ok ]; then
        local miss=()
        for f in results.json results.dat boot.log meta.json versions.txt; do
            [ -f "$dest/$f" ] || miss+=("$f")
        done
        if [ "${#miss[@]}" -gt 0 ]; then
            echo "ERROR: the stamp $dest is missing mandatory file(s): ${miss[*]}" >&2
            return 1
        fi
    fi
}

# ---- result summary (the bench's documented result-code contract) ----
summarize() {
    local stamp
    stamp=$(newest_stamp) || { echo "no published stamp under $RESULTS_ROOT yet - run without --summary first" >&2; return 1; }
    if [ ! -f "$stamp/results.json" ]; then
        echo "newest stamp $stamp carries no results.json - not a completed run"
        echo "(an evidence-only stamp from a failed run; see its boot.log)"
        return 1
    fi
    python3 - "$stamp" <<'PY'
import collections, json, os, sys

stamp = sys.argv[1]
with open(os.path.join(stamp, "results.json"), encoding="utf-8") as f:
    res = json.load(f)
try:
    with open(os.path.join(stamp, "meta.json"), encoding="utf-8") as f:
        meta = json.load(f)
except (OSError, ValueError):
    meta = {}

run_status = "missing"
with open(os.path.join(stamp, "versions.txt"), encoding="utf-8", errors="replace") as f:
    for line in f:
        if line.startswith("run-status:"):
            run_status = line.split(":", 1)[1].strip()
            break

def band(c):
    # the result-code contract documented in the bench's README.md
    if c == 0:          return (0, "attack succeeded (no defense at this venue)")
    if c == 1:          return (1, "silent: effect absent, no platform signal")
    if 2 <= c <= 14:    return (2, "case-local failure (declared per record)")
    if 15 <= c <= 31:   return (15, "caught: platform/library responded")
    if 32 <= c <= 65:   return (32, "runtime parameter discovered (producer)")
    if 256 <= c <= 511: return (256, "killed by signal %d" % (c - 256))
    if c == 1024:       return (1024, "prerequisite case failed")
    if c == 1025:       return (1025, "not run (declared skip / not built)")
    return (10**6, "other (needs investigation)")

def short(sort):
    return {0: "0", 1: "1", 2: "2-14", 15: "15-31", 32: "32-65",
            256: "256+", 1024: "1024", 1025: "1025"}.get(sort, "other")

hist = collections.Counter()
cats = collections.defaultdict(collections.Counter)
for name, rec in res.items():
    code = rec.get("result") if isinstance(rec, dict) else int(rec)
    if not isinstance(code, int):
        code = -1
    hist[band(code)] += 1
    cats[name.split("-", 1)[0]][band(code)] += 1

nemu = meta.get("nemu_bin") or {}
image = meta.get("image") or {}
print("stamp:   %s" % stamp)
print("bench:   %s (tree %s)" % (meta.get("bench_commit", "?"), meta.get("bench_tree", "?")))
print("nemu:    sha256 %s" % ((nemu.get("sha256") or "?")[:16]))
print("image:   sha256 %s" % ((image.get("sha256") or "?")[:16]))
print("guest:   guest exit %s, nemu exit %s, good trap %s" % (
    meta.get("guest_exit_code", "?"), meta.get("nemu_exit_code", "?"),
    meta.get("hit_good_trap", "?")))
print("records: %s reported / %s expected   run-status: %s" % (
    meta.get("records_reported", len(res)), meta.get("records_expected", "?"), run_status))
print()
print("outcome histogram (result-code contract: cpu-sec-bench README.md):")
bands = sorted(hist)
for b in bands:
    print("  %-44s %5d" % (b[1], hist[b]))
print()
print("per category (columns: band, matching the histogram above):")
head = "  %-6s %8s" % ("cat", "records")
for b in bands:
    head += " %8s" % short(b[0])
print(head)
for cat in sorted(cats):
    row = "  %-6s %8d" % (cat, sum(cats[cat].values()))
    for b in bands:
        row += " %8d" % cats[cat].get(b, 0)
    print(row)
print()
print("console evidence: %s/boot.log" % stamp)
PY
}

# ---- venue lock: one run per work dir (the legs rm -rf shared targets) ----
acquire_venue_lock() {
    mkdir -p "$WORK"
    exec 9>>"$WORK/.lock"
    if ! flock -n 9; then
        echo "venue busy: $(cat "$WORK/.lock" 2>/dev/null || echo unknown) holds $WORK/.lock" >&2
        echo "(one run per work dir; wait for the holder or use another --work dir)" >&2
        exit 1
    fi
    printf 'pid=%s mode=%s\n' "$$" "$MODE" > "$WORK/.lock"
}

if [ "$MODE" = clean ]; then
    acquire_venue_lock
    echo "removing the work dir: $WORK"
    rm -rf -- "$WORK"
    exit 0
fi

if [ "$MODE" = summary ]; then
    summarize
    exit $?
fi

# ---- NEMU and toolchain checks: only the run/deploy modes need them ----
resolve_nemu
preflight_tools
acquire_venue_lock

# ---- plan header ----
echo "== cpu-sec-bench memory-safety run against XiangShan NEMU"
echo "   bench: $BENCH_DIR (head $(git -C "$BENCH_DIR" rev-parse --short HEAD 2>/dev/null || echo unknown))"
echo "   nemu:  $NEMU_BIN (checkout $(git -C "$NEMU_CHECKOUT" rev-parse --short HEAD 2>/dev/null || echo unknown))"
echo "   work:  $WORK"

# ---- prebuilt-image provisioning (optional) ----
# When CPU_SEC_BENCH_IMAGE_URL and CPU_SEC_BENCH_REGISTRY_URL are set, the
# two deploy legs (corpus staging and image build) are replaced by a download
# of a published image pair: fw_payload.bin plus the bench registry it was
# built from (the image bundles the corpus, so the pair must be published per
# bench commit). Each download is verified against "<url>.sha256" (or the
# CPU_SEC_BENCH_*_SHA256 override); a mismatch refuses the run before
# anything is booted, and the boot gate still compares the guest's results
# against the downloaded registry.
provision_prebuilt_image() {
    local image_url=$CPU_SEC_BENCH_IMAGE_URL
    local registry_url=$CPU_SEC_BENCH_REGISTRY_URL
    mkdir -p "$OUT/bench"
    fetch_verified() { # url dest [sha_override]
        local url=$1 dest=$2 want got
        if [ -n "${3:-}" ]; then
            want=$3
        else
            echo "   fetch $url.sha256"
            curl -fsL --retry 3 -o "$dest.sha256.dl" "$url.sha256" || {
                echo "ERROR: cannot fetch $url.sha256" >&2; return 1; }
            want=$(cut -d' ' -f1 "$dest.sha256.dl")
            rm -f "$dest.sha256.dl"
        fi
        echo "   fetch $url"
        curl -fL --retry 3 -o "$dest.part" "$url" || {
            echo "ERROR: cannot fetch $url" >&2; return 1; }
        got=$(sha256sum "$dest.part" | cut -d' ' -f1)
        if [ "$want" != "$got" ]; then
            echo "ERROR: sha256 mismatch for $url: expected $want, got $got" >&2
            rm -f "$dest.part"
            return 1
        fi
        mv "$dest.part" "$dest"
    }
    fetch_verified "$image_url" "$OUT/fw_payload.bin" "${CPU_SEC_BENCH_IMAGE_SHA256:-}" || exit 1
    fetch_verified "$registry_url" "$OUT/bench/configure.json" "${CPU_SEC_BENCH_REGISTRY_SHA256:-}" || exit 1
    if [ -n "${CPU_SEC_BENCH_IMAGE_BENCH_COMMIT:-}" ]; then
        local bench_head
        bench_head=$(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)
        [ "$bench_head" = "$CPU_SEC_BENCH_IMAGE_BENCH_COMMIT" ] || \
            echo "WARNING: the prebuilt image was built from bench $CPU_SEC_BENCH_IMAGE_BENCH_COMMIT but --bench points at $bench_head; results may not match this checkout" >&2
    fi
    # Record bench provenance in the deploy meta so the run stamp's summary
    # prints the real commit instead of "unknown" (fresh-machine finding):
    # the release's declared commit when given, else the --bench checkout.
    local pb_bench_commit pb_bench_tree
    pb_bench_commit=${CPU_SEC_BENCH_IMAGE_BENCH_COMMIT:-}
    if [ -z "$pb_bench_commit" ]; then
        pb_bench_commit=$(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)
    fi
    pb_bench_tree=$(git -C "$BENCH_DIR" describe --always --dirty 2>/dev/null || echo unknown)
    python3 - "$OUT" "$NEMU_BIN" "$image_url" "$pb_bench_commit" "$pb_bench_tree" <<'PY'
import hashlib, json, os, sys
out, nemu, url, bench_commit, bench_tree = sys.argv[1:6]
def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()
meta = {
    "platform": "cpu-sec-bench NEMU venue (XiangShanLab memory-safety, prebuilt image)",
    "prebuilt_image_url": url,
    "bench_commit": bench_commit,
    "bench_tree": bench_tree,
    "fw_payload_bin": {"path": os.path.join(out, "fw_payload.bin"),
                       "sha256": sha(os.path.join(out, "fw_payload.bin"))},
    "nemu_bin": {"path": nemu, "sha256": sha(nemu) if os.path.exists(nemu) else None},
}
with open(os.path.join(out, "meta.deploy.json"), "w") as f:
    json.dump(meta, f, indent=2, sort_keys=True)
    f.write("\n")
print("   image: %d bytes, sha256 %s" % (
    os.path.getsize(os.path.join(out, "fw_payload.bin")),
    meta["fw_payload_bin"]["sha256"][:16]))
PY
    echo "   prebuilt image provisioned; corpus staging and image build skipped"
}

if [ "$MODE" != run-only ]; then
    if [ -n "${CPU_SEC_BENCH_IMAGE_URL:-}" ]; then
        echo "== [1/3] provisioning the prebuilt guest image (CPU_SEC_BENCH_IMAGE_URL is set;"
        echo "         corpus staging and image build are skipped - the image bundles the corpus)"
        [ -n "${CPU_SEC_BENCH_REGISTRY_URL:-}" ] || {
            echo "ERROR: CPU_SEC_BENCH_IMAGE_URL requires CPU_SEC_BENCH_REGISTRY_URL as well (the boot gate compares the results against the image's registry)" >&2
            exit 1
        }
        provision_prebuilt_image
    else
        echo "== [1/3] staging the corpus (scratch-tree cross build; a cold first run downloads pinned"
        echo "         sources and builds the guest kernel - tens of minutes on a multi-core machine)"
        t0=$SECONDS
        timeout "$DEPLOY_TIMEOUT" bash "$VENUE/stage-corpus.sh" "$BENCH_DIR" "$SCRATCH_BENCH" "$CORPUS"
        echo "   corpus staged in $((SECONDS - t0)) s"

        echo "== [2/3] building the guest image (warm builds reuse the caches under $WORK/platform/src)"
        t0=$SECONDS
        timeout "$DEPLOY_TIMEOUT" bash "$VENUE/build-image.sh"
        echo "   image built in $((SECONDS - t0)) s"
    fi
else
    [ -f "$OUT/fw_payload.bin" ] || {
        echo "no image at $OUT/fw_payload.bin - run without --run-only (or --deploy-only) first" >&2
        exit 1
    }
fi

# ---- boot leg: outer backstop is NEMU_TIMEOUT + 120; the inner bound is
# boot.sh's own `timeout $NEMU_TIMEOUT` on the interpreter, so the venue
# diagnosis fires first and the backstop only catches a hung driver ----
echo "== [3/3] booting the image under NEMU (NEMU_TIMEOUT=${NEMU_TIMEOUT}s)"
RUNS_SNAP="$WORK/runs.snapshot"
: > "$RUNS_SNAP"
[ -d "$OUT/runs" ] && find "$OUT/runs" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort > "$RUNS_SNAP"
BOOT_LOG="$WORK/boot-leg.log"
: > "$BOOT_LOG"
t0=$SECONDS
set +e
timeout "$((NEMU_TIMEOUT + 120))" bash "$VENUE/boot.sh" >"$BOOT_LOG" 2>&1
leg_rc=$?
set -e
echo "   boot leg done in $((SECONDS - t0)) s (rc=$leg_rc)"

# The venue driver creates its run dir before booting; exactly one new run
# dir selects it (best effort on failure paths).
RUN_DIR=""
if [ -d "$OUT/runs" ]; then
    n=0; cand=""
    while IFS= read -r name; do
        grep -qxF -- "$name" "$RUNS_SNAP" || { n=$((n + 1)); cand="$OUT/runs/$name"; }
    done < <(find "$OUT/runs" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort)
    [ "$n" -eq 1 ] && RUN_DIR=$cand
fi

if [ "$leg_rc" -eq 124 ] || grep -q "NEMU timed out" "$BOOT_LOG"; then
    publish_stamp none
    exit 124
fi
if [ "$leg_rc" -ne 0 ]; then
    echo "the boot leg failed (rc=$leg_rc) - see $BOOT_LOG; publishing an evidence-only stamp" >&2
    publish_stamp "failed (rc=$leg_rc)" "$RUN_DIR"
    exit "$leg_rc"
fi
if [ -z "$RUN_DIR" ]; then
    echo "ERROR: the boot leg succeeded but left no unambiguous new run dir under $OUT/runs" >&2
    exit 1
fi
publish_stamp ok "$RUN_DIR" || exit 1

echo "== summary: newest published stamp"
summarize
