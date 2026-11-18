#!/usr/bin/env bash
set -euo pipefail
# build-image.sh - assemble the bootable guest image $OUT/fw_payload.bin from
# the staged corpus, for the cpu-sec-bench memory-safety venue (XiangShanLab
# external-contribution-project/memory-safety).
#
# Ported from cpu-sec-test-env scripts/riscv64_nemu/venue/build-image.sh
# (2026-10-31); the pins, the memory layout and the build recipes are
# identical.  One deliberate change: the cross-built guest scheduler comes
# from SCRATCH_BENCH (the staging scratch copy), so the user's BENCH_DIR
# checkout is never written.
#
# The image is the classic NEMU layout assembled by workload-builder:
#
#   0x80000000 +0     gcpt (LibCheckpointAlpha startup, <= 1 MiB)
#   0x80100000 +1 MiB OpenSBI fw_jump
#   0x80200000 +2 MiB device tree (xiangshan, 128 MiB DRAM, 1 hart)
#   0x80400000 +4 MiB Linux Image
#   ...        MiB-aligned  initramfs (rootfs.cpio, uncompressed newc)
#
# and $OUT/meta.deploy.json records the image/input provenance.
#
# Pinned inputs (cloned/downloaded once into $OUT/src, then reused; the pins
# live in venue/pins.sh): workload-builder, OpenSBI, LibCheckpointAlpha, the
# kernel and busybox tarballs.  A cold build needs the network; a warm one is
# offline.
#
# Kernel 6.18.56, not an older pin: the guest userspace is built by the apt
# cross toolchain whose glibc is RVA23U64 vector-using code, and kernel-side
# userspace vector support only exists from Linux 6.5.  On 6.1.83 every guest
# process dies with SIGILL on its first vector instruction -- measured
# 2026-10-20 on this exact recipe: the static busybox dies in a glibc memset
# `vsetivli` at init.  The kernel fragment forces userspace vector on.
#
# Inputs from the environment (exported by the driver):
#   OUT           the platform build dir (created)
#   CORPUS        the staged corpus dir (venue/stage-corpus.sh output)
#   SCRATCH_BENCH the staging scratch copy of the bench tree
#   BENCH_DIR     the user's bench checkout (read-only; provenance reads only)
#   NEMU_BIN      the interpreter (recorded in meta.deploy.json)
#   CXX CC ASM OBJDUMP CROSS_COMPILE READELF  the cross tool pair

VENUE=$(cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=pins.sh
. "$VENUE/pins.sh"

die() { echo "build-image: $*" >&2; exit 1; }

OUT="${OUT:?OUT must be exported by the driver}"
CORPUS="${CORPUS:?CORPUS must be exported by the driver (the staged corpus dir)}"
SCRATCH_BENCH="${SCRATCH_BENCH:?SCRATCH_BENCH must be exported by the driver}"
BENCH_DIR="${BENCH_DIR:?BENCH_DIR must be exported by the driver}"
NEMU_BIN="${NEMU_BIN:?NEMU_BIN must be exported by the driver}"
DEST="$OUT"
SRC="$OUT/src"
DL="$SRC/downloads"
JOBS="${JOBS:-$(nproc)}"

# The pins are pins.sh's (one pin per input, nowhere else).
KERNEL_VER="$KERNEL_VERSION"
KERNEL_TARBALL="linux-$KERNEL_VER.tar.xz"
BUSYBOX_VER="$BUSYBOX_VERSION"
BUSYBOX_TARBALL="busybox-$BUSYBOX_VER.tar.bz2"
export SOURCE_DATE_EPOCH
# Stray make variables would select the wrong lib/ subdirectory in child
# makes; the cross knobs below are the only overrides the build takes.
unset ARCH MAKEFLAGS MFLAGS

# The staged corpus is the input: its manifest is written only after every
# staging step succeeded, so a half-staged tree can never be consumed.
[ -f "$CORPUS/manifest.json" ] || die "$CORPUS/manifest.json missing; stage-corpus.sh did not complete"
[ -d "$CORPUS/test" ] || die "$CORPUS/test missing; stage-corpus.sh did not complete"
[ -d "$SCRATCH_BENCH" ] || die "scratch bench tree $SCRATCH_BENCH missing; stage-corpus.sh did not complete"

# The packing step below requires GNU cpio's --reproducible; probe it here,
# before any fetch or build, so a host without it fails with a diagnostic
# instead of dying at the archive step after the long build.  Capture the
# help text rather than piping it into grep -- the capture-then-case form
# keeps a nonzero `--help` from masking the match.
CPIO_HELP="$(cpio --help 2>/dev/null || true)"
case "$CPIO_HELP" in
    *--reproducible*) ;;
    *) die "this host's cpio does not support --reproducible (GNU cpio is required)" ;;
esac

# Provenance from the untouched checkout, captured before any build: the
# scratch tree the corpus was staged in is not provenance the guest should
# see, and a `-dirty` suffix here means the corpus really did come from an
# uncommitted bench tree (or a non-git download: "unknown").
BENCH_COMMIT="$(git -C "$BENCH_DIR" rev-parse HEAD 2>/dev/null || echo unknown)"
BENCH_TREE="$(git -C "$BENCH_DIR" describe --always --dirty 2>/dev/null || echo unknown)"

mkdir -p "$SRC" "$DL" "$OUT/generated-dts"

# Optional local seeds: when the user already has these checkouts or tarballs
# on disk (common in a XiangShan workspace), they are reused as *seeds* -
# cloned into this venue's own cache and sha256-verified before use - so the
# user's trees are never checked out, mutated or trusted blindly.  Without a
# seed (or when the seed lacks the pinned commit), the pinned upstream is
# fetched from the network as before.
WB_LOCAL=${WORKLOAD_BUILDER_LOCAL:-}
OPENSBI_LOCAL=${OPENSBI_LOCAL:-}
LCA_LOCAL=${LIB_CHECKPOINT_ALPHA_LOCAL:-}
TARBALL_SEED_DIR=${CPU_SEC_BENCH_SEED_DIR:-}

ensure_checkout() { # url dir commit [local_seed]
    local url="$1" dir="$2" commit="$3" seed="${4:-}" head attempt
    # A previously interrupted clone can leave a stub without .git; it is
    # never useful, and git clone refuses a non-empty target - remove it.
    if [ -d "$dir" ] && [ ! -d "$dir/.git" ]; then
        echo "   removing stub $dir (a previous clone did not finish)"
        rm -rf "$dir"
    fi
    for attempt in 1 2 3; do
        if [ -d "$dir/.git" ]; then break; fi
        if [ -n "$seed" ] && [ -d "$seed/.git" ]; then
            # Seed from the local checkout into this venue's own cache; the
            # seed tree is only ever read.  origin is repointed at the pinned
            # upstream, so a commit missing from the seed still fetches from
            # the network in the loop below.
            echo "   seed clone from $seed (attempt $attempt)"
            if git clone --quiet "$seed" "$dir"; then
                git -C "$dir" remote set-url origin "$url"
                break
            fi
            rm -rf "$dir"
            [ "$attempt" -eq 3 ] && { echo "ERROR: seed-cloning $seed failed after 3 attempts" >&2; return 1; }
            sleep $((attempt * 10))
        else
            echo "   clone $url (attempt $attempt)"
            if git clone --quiet "$url" "$dir"; then break; fi
            rm -rf "$dir"
            [ "$attempt" -eq 3 ] && { echo "ERROR: cloning $url failed after 3 attempts" >&2; return 1; }
            sleep $((attempt * 10))
        fi
    done
    for attempt in 1 2 3; do
        git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null && break
        echo "   fetch $commit (attempt $attempt)"
        if git -C "$dir" fetch --quiet origin "$commit"; then break; fi
        [ "$attempt" -eq 3 ] && { echo "ERROR: fetching $commit into $dir failed after 3 attempts" >&2; return 1; }
        sleep $((attempt * 10))
    done
    git -C "$dir" checkout --quiet --detach "$commit" || return 1
    head="$(git -C "$dir" rev-parse HEAD)"
    [ "$head" = "$commit" ] || { echo "ERROR: $dir is at $head, expected $commit" >&2; return 1; }
}

fetch_tarball() { # tarball sha256 url
    local name="$1" sha="$2" url="$3"
    if [ -f "$DL/$name" ] && echo "$sha  $DL/$name" | sha256sum -c --quiet -; then
        echo "   cached $name"; return 0
    fi
    if [ -n "$TARBALL_SEED_DIR" ] && [ -f "$TARBALL_SEED_DIR/$name" ] && \
       echo "$sha  $TARBALL_SEED_DIR/$name" | sha256sum -c --quiet -; then
        echo "   seed $name from $TARBALL_SEED_DIR"
        cp "$TARBALL_SEED_DIR/$name" "$DL/$name"
        return 0
    fi
    echo "   download $url"
    curl -fL --retry 3 -o "$DL/$name.part" "$url"
    echo "$sha  $DL/$name.part" | sha256sum -c --quiet - || { rm -f "$DL/$name.part"; die "sha256 mismatch for $name"; }
    mv "$DL/$name.part" "$DL/$name"
}

is_riscv_elf() { # file -> 0 when ELF64 riscv
    python3 - "$1" <<'PY'
import sys
h = open(sys.argv[1], "rb").read(20)
ok = len(h) >= 20 and h[:4] == b"\x7fELF" and h[4] == 2 and (h[18] | (h[19] << 8)) == 243
sys.exit(0 if ok else 1)
PY
}

echo "== [1/8] fetch pinned inputs into $SRC"
ensure_checkout "$WB_URL" "$SRC/workload-builder" "$WB_COMMIT" "$WB_LOCAL"
mkdir -p "$SRC/bootloader"
ensure_checkout "$OPENSBI_URL" "$SRC/bootloader/opensbi" "$OPENSBI_COMMIT" "$OPENSBI_LOCAL"
ensure_checkout "$LCA_URL" "$SRC/bootloader/LibCheckpointAlpha" "$LCA_COMMIT" "$LCA_LOCAL"
# build-sbi.sh reads its config and patch from the SBI source's parent dir
cp "$SRC/workload-builder/bootloader/opensbi.config" "$SRC/bootloader/opensbi.config"
cp "$SRC/workload-builder/bootloader/opensbi.patch" "$SRC/bootloader/opensbi.patch"
WB="$SRC/workload-builder"
fetch_tarball "$KERNEL_TARBALL" "$KERNEL_SHA256" "$KERNEL_URL"
fetch_tarball "$BUSYBOX_TARBALL" "$BUSYBOX_SHA256" "$BUSYBOX_URL"

echo "== [2/8] busybox (static)"
if [ ! -x "$OUT/busybox-$BUSYBOX_VER/_install/bin/busybox" ]; then
    [ -d "$OUT/busybox-$BUSYBOX_VER" ] || tar -C "$OUT" -xf "$DL/$BUSYBOX_TARBALL"
    make -C "$OUT/busybox-$BUSYBOX_VER" defconfig >/dev/null
    sed -i 's/^# CONFIG_STATIC is not set$/CONFIG_STATIC=y/' "$OUT/busybox-$BUSYBOX_VER/.config"
    # busybox 1.36.1's tc applet no longer compiles against modern kernel
    # headers (the CBQ constants are gone); the guest needs no traffic control
    sed -i 's/^CONFIG_TC=y$/# CONFIG_TC is not set/' "$OUT/busybox-$BUSYBOX_VER/.config"
    # CC= pins the versioned gcc-13: the corpus is built with g++-13, so the
    # guest toolchain must be the same major
    make -C "$OUT/busybox-$BUSYBOX_VER" -j"$JOBS" CROSS_COMPILE="$CROSS_COMPILE" CC="$CC" >/dev/null
    make -C "$OUT/busybox-$BUSYBOX_VER" install CROSS_COMPILE="$CROSS_COMPILE" CC="$CC" >/dev/null
fi
if "$READELF" -d "$OUT/busybox-$BUSYBOX_VER/_install/bin/busybox" | grep -q NEEDED; then
    die "busybox is not statically linked"
fi
is_riscv_elf "$OUT/busybox-$BUSYBOX_VER/_install/bin/busybox" || die "busybox is not a riscv64 ELF"
[ -e "$OUT/busybox-$BUSYBOX_VER/_install/sbin/init" ] || die "busybox has no init applet"

echo "== [3/8] Linux $KERNEL_VER Image"
mkdir -p "$OUT/kernel"
# the cache key carries the version, so a kernel bump can never silently
# reuse the previous kernel's Image; out/kernel/Image is the working copy
# step [8/8] pads, the cached per-version file stays the pristine build
# product
if [ ! -f "$OUT/kernel/Image-$KERNEL_VER" ]; then
    [ -d "$OUT/linux-$KERNEL_VER" ] || tar -C "$OUT" -xf "$DL/$KERNEL_TARBALL"
    # wb's NEMU fragment minus CONFIG_INITRAMFS_SOURCE: the rootfs travels in
    # the DTB /chosen node instead (the firmware packer patches it in)
    grep -v '^CONFIG_INITRAMFS_SOURCE=' "$WB/br2-external/board/openxiangshan/nemu/linux.config" \
        > "$OUT/linux-$KERNEL_VER/.config"
    # userspace vector, the load-bearing pair: RISCV_ISA_V is the kernel-side
    # vector support, RISCV_ISA_V_DEFAULT_ENABLE makes riscv_v_default_allow
    # default to allow -- the RVA23 glibc never issues the V prctl itself
    printf 'CONFIG_RISCV_ISA_V=y\nCONFIG_RISCV_ISA_V_DEFAULT_ENABLE=y\n' \
        >> "$OUT/linux-$KERNEL_VER/.config"
    make -C "$OUT/linux-$KERNEL_VER" ARCH=riscv CROSS_COMPILE="$CROSS_COMPILE" CC="$CC" olddefconfig >/dev/null
    make -C "$OUT/linux-$KERNEL_VER" ARCH=riscv CROSS_COMPILE="$CROSS_COMPILE" CC="$CC" -j"$JOBS" WERROR=0 Image >/dev/null
    for sym in CONFIG_RISCV_ISA_V CONFIG_RISCV_ISA_V_DEFAULT_ENABLE; do
        grep -qx "$sym=y" "$OUT/linux-$KERNEL_VER/.config" \
            || die "built .config lacks $sym=y; guest userspace vector would SIGILL"
    done
    cp "$OUT/linux-$KERNEL_VER/arch/riscv/boot/Image" "$OUT/kernel/Image-$KERNEL_VER"
fi
cp "$OUT/kernel/Image-$KERNEL_VER" "$OUT/kernel/Image"

echo "== [4/8] gcpt and OpenSBI (DEFAULT_DTB=xiangshan)"
# workload-builder's gcpt/OpenSBI makes resolve CC = $(CROSS_COMPILE)gcc -
# the *unversioned* cross gcc, which Ubuntu ships only in the separate
# gcc-riscv64-linux-gnu package (fresh-machine finding: installing only the
# versioned gcc-13 packages leaves these builds dying with "No such file or
# directory" after the long kernel build).  Shim the unversioned name to the
# venue's pinned versioned compiler instead of requiring that package: the
# firmware then uses the same gcc major as the corpus, and no extra
# dependency is needed on any distro.
XGCC_SHIM="$OUT/xgcc-shim"
mkdir -p "$XGCC_SHIM"
ln -sfn "$(command -v "$CC")" "$XGCC_SHIM/riscv64-linux-gnu-gcc"
echo "   cross-gcc shim: riscv64-linux-gnu-gcc -> $(command -v "$CC")"
if [ ! -f "$OUT/gcpt/build/gcpt.bin" ]; then
    PATH="$XGCC_SHIM:$PATH" CROSS_COMPILE="$CROSS_COMPILE" DEFAULT_DTB=xiangshan DTS_TEMPLATE_DIR="$OUT/generated-dts" \
        bash "$WB/scripts/build-gcpt.sh" "$SRC/bootloader/LibCheckpointAlpha" "$OUT/gcpt"
fi
if [ ! -f "$OUT/opensbi/build/platform/generic/firmware/fw_jump.bin" ]; then
    PATH="$XGCC_SHIM:$PATH" CROSS_COMPILE="$CROSS_COMPILE" DEFAULT_DTB=xiangshan DTS_TEMPLATE_DIR="$OUT/generated-dts" \
        bash "$WB/scripts/build-sbi.sh" "$SRC/bootloader/opensbi" "$OUT/opensbi"
fi
# gcpt.bin is the raw payload the image carries; the ELF it was objcopy'd
# from is the sibling `gcpt`, and that one must be riscv64 (a host-compiled
# gcpt would boot nothing)
is_riscv_elf "$OUT/gcpt/build/gcpt" || die "gcpt (the ELF) is not a riscv64 ELF"
[ -s "$OUT/gcpt/build/gcpt.bin" ] || die "gcpt.bin is empty"

echo "== [5/8] stage the guest bench tree (cross scheduler, libcfi, corpus)"
rm -rf "$DEST/bench" "$DEST/lib"   # no stale staging from an earlier deploy may survive
rm -f "$DEST/manifest.json"        # and a stale manifest must not satisfy a consumer's guard
mkdir -p "$DEST/bench/test" "$DEST/bench/lib/common" "$DEST/lib"
# Cross-built guest scheduler: `-B` because GNU make does not rebuild on
# variable changes alone, and the scratch tree carries a host build.  The
# scheduler's own CSB_TARGET_ARCH is $BENCH_ARCH here, i.e. the token the
# tracked registry's skip lists are written in.  Built in the SCRATCH copy,
# never in the user's checkout.
make -B -C "$SCRATCH_BENCH" run-test lib/common/libcfi.so \
    ARCH="$BENCH_ARCH" CXX="$CXX" ASM="$ASM" OBJDUMP="$OBJDUMP" >"$DEST/scheduler-crossbuild.log" 2>&1
install -m 0755 "$SCRATCH_BENCH/run-test" "$DEST/bench/run-test"
cp -p "$SCRATCH_BENCH/lib/common/libcfi.so" "$DEST/bench/lib/common/libcfi.so"
is_riscv_elf "$DEST/bench/run-test" || die "the guest scheduler is not a riscv64 ELF - the cross build leaked host mode"
# The guest's registry is the tracked bench registry, copied verbatim - never
# a derived subset (stage-corpus.sh verified it byte-identical).
cp "$CORPUS/configure.json" "$DEST/bench/configure.json"
cp -a "$CORPUS/test/." "$DEST/bench/test/"
install -m 0644 "$CORPUS/manifest.json" "$DEST/manifest.json"

# The guest's own system description: report_gen() prefixes results.dat with
# this file, so it must describe the venue the results came from -- not the
# host that built them.
{
    echo "Venue: cpu-sec-bench inside a booted Linux guest under XiangShan NEMU (system mode, riscv64)"
    echo "Venue package: XiangShanLab external-contribution-project/memory-safety (venue/)"
    echo "Corpus compiler: $("$CXX" --version | head -n1)"
    echo "Corpus objdump: $("$OBJDUMP" --version | head -n1)"
    echo "Bench commit: $BENCH_COMMIT"
    echo "Bench tree: $BENCH_TREE"
} > "$DEST/bench/test/sys_info.txt"

echo "== [6/8] runtime libraries (NEEDED + PT_INTERP closure)"
# Runtime libraries: closure over NEEDED plus the PT_INTERP loader, resolved
# through the cross compiler's own search path.  libcfi.so is NOT staged to
# /lib: the cpi/cfi binaries reach it through their relative RUNPATH
# (lib/common/) with the guest's cwd at /bench.
is_elf() { [ "$(head -c4 "$1" | od -An -tx1 | tr -d ' \n')" = "7f454c46" ]; }
declare -a queue=()
queue+=("$DEST/bench/run-test")
for f in "$DEST/bench/test/"*; do queue+=("$f"); done
[ -f "$DEST/bench/lib/common/libcfi.so" ] && queue+=("$DEST/bench/lib/common/libcfi.so")
declare -A staged_seen=()
while [ "${#queue[@]}" -gt 0 ]; do
    f="${queue[0]}"; queue=("${queue[@]:1}")
    [ -n "${staged_seen[$f]:-}" ] && continue
    staged_seen[$f]=1
    is_elf "$f" || continue
    interp="$("$READELF" -l "$f" | sed -n 's/.*Requesting program interpreter: \(.*\)\]/\1/p')"
    for soname in $interp $("$OBJDUMP" -p "$f" | awk '$1=="NEEDED"{print $2}'); do
        base="$(basename "$soname")"
        [ -e "$DEST/lib/$base" ] && continue
        [ -e "$DEST/bench/lib/common/$base" ] && continue   # relative-RUNPATH library
        src="$("$CXX" -print-file-name="$base")"
        if [ ! -f "$src" ]; then
            die "unresolved library $base (needed by $f)"
        fi
        cp -pL "$src" "$DEST/lib/$base"
        queue+=("$DEST/lib/$base")
    done
done
echo "   staged libs: $(ls "$DEST/lib" | tr '\n' ' ')"

echo "== [7/8] rootfs tree and cpio"
rm -rf "$OUT/rootfs"
mkdir -p "$OUT/rootfs"/{bench,lib,etc,proc,sys,tmp,dev}
cp -a "$OUT/busybox-$BUSYBOX_VER/_install/." "$OUT/rootfs/"
ln -sf bin/busybox "$OUT/rootfs/init"
install -m 0644 "$VENUE/guest/inittab" "$OUT/rootfs/etc/inittab"
cp -a "$DEST/bench/." "$OUT/rootfs/bench/"
cp -a "$DEST/lib/." "$OUT/rootfs/lib/"
"$CC" -O2 -static -o "$OUT/rootfs/bench/nemu-trap" "$VENUE/guest/nemu-trap.c"
install -m 0755 "$VENUE/guest/bench.sh" "$OUT/rootfs/bench/bench.sh"
is_riscv_elf "$OUT/rootfs/bench/nemu-trap" || die "nemu-trap is not a riscv64 ELF"

# Byte reproducibility: the tree above was just assembled by mkdir/cp/install,
# whose wall-clock mtimes would otherwise be archived by cpio (the kernel and
# busybox builds already honour SOURCE_DATE_EPOCH).  Stamp every entry -- -h
# so symlinks are stamped themselves, not their targets -- and feed cpio a
# sorted file list so the archive does not depend on readdir order either.
# --reproducible (GNU cpio: --ignore-devno --ignore-dirnlink --renumber-inodes)
# finishes the job: device numbers are zeroed, inode numbers are renumbered
# deterministically in list order and directory link counts are pinned, so
# the host's own inode numbers never reach the newc headers.
find "$OUT/rootfs" -exec touch -h -d "@$SOURCE_DATE_EPOCH" {} +
( cd "$OUT/rootfs" && find . | LC_ALL=C sort | cpio -o -H newc --owner=0:0 --reproducible ) > "$DEST/rootfs.cpio" 2>/dev/null
echo "   rootfs.cpio: $(stat -c%s "$DEST/rootfs.cpio") bytes"

echo "== [8/8] assemble fw_payload.bin"
# The kernel's runtime footprint is larger than its Image file -- BSS lives
# past the last PROGBITS section -- so workload-builder's assembler, which
# places the initramfs at the first MiB boundary after the *file*, can land it
# inside the kernel's own reserved range; the kernel then discards it
# ("INITRD: ... overlaps in-use memory region") and panics with no root fs.
# Pad the Image until that boundary clears _end by a full MiB; the padded
# tail is zeros beyond the kernel that nothing ever reads (the kernel zeroes
# its own BSS).
source "$WB/scripts/dts-config.sh"
read -r MEM_BEGIN MEM_SIZE CLINT_MMIO <<< "$(dts_extract_config "$OUT/generated-dts/xiangshan.dts.in")"
# both spellings normalised here: dts_linux_kernel_address prints a DECIMAL
# address while System.map addresses are bare hex
KERNEL_BEGIN=$(( $(dts_linux_kernel_address "$MEM_BEGIN" $((4 * 1024 * 1024))) ))
KERNEL_START_V=$(( 0x$(awk '$3=="_start"{print $1; exit}' "$OUT/linux-$KERNEL_VER/System.map") ))
KERNEL_END_V=$(( 0x$(awk '$3=="_end"{print $1; exit}' "$OUT/linux-$KERNEL_VER/System.map") ))
python3 - "$OUT/kernel/Image" "$KERNEL_BEGIN" "$KERNEL_START_V" "$KERNEL_END_V" <<'PY'
import os, sys
image = sys.argv[1]
begin, start_v, end_v = (int(a) for a in sys.argv[2:5])
footprint = end_v - start_v      # link-time virtual span == the physical span here
mib = 1024 * 1024
need = (footprint + mib + mib - 1) // mib * mib
size = os.path.getsize(image)
if size < need:
    with open(image, "ab") as f:
        f.write(b"\0" * (need - size))
    print("   kernel Image padded %d -> %d bytes (clears _end by 1 MiB;" % (size, need))
    print("   initramfs will land at 0x%x, kernel _end at 0x%x)" % (begin + need, begin + footprint))
PY

CROSS_COMPILE="$CROSS_COMPILE" DTC=dtc DEFAULT_DTB=xiangshan \
    FIRMWARE_OUTPUT="$DEST/fw_payload.bin" \
    bash "$WB/scripts/build-firmware-linux.sh" \
        "$OUT/gcpt/build/gcpt.bin" "$OUT/opensbi" "$OUT/generated-dts" "$OUT/kernel/Image" "$DEST"

python3 - "$DEST" "$NEMU_BIN" "$BENCH_DIR" "$CXX" "$VENUE" \
    "$WB_COMMIT" "$OPENSBI_COMMIT" "$LCA_COMMIT" \
    "$KERNEL_VERSION ($KERNEL_SHA256)" "$BUSYBOX_VERSION ($BUSYBOX_SHA256)" <<'PY'
import hashlib, json, os, subprocess, sys
dest, nemu, bench, cxx, venue = sys.argv[1:6]
wb, opensbi, lca, linux_pin, busybox_pin = sys.argv[6:11]
def sha(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""): h.update(c)
    return h.hexdigest()
meta = {
    "platform": "cpu-sec-bench NEMU venue (XiangShanLab memory-safety)",
    "bench_commit": subprocess.run(["git", "-C", bench, "rev-parse", "HEAD"],
                                   capture_output=True, text=True).stdout.strip() or "unknown",
    # `-dirty` means the image was built from an uncommitted bench tree: the
    # commit alone would not reproduce the scheduler baked into it.
    "bench_tree": subprocess.run(["git", "-C", bench, "describe", "--always", "--dirty"],
                                 capture_output=True, text=True).stdout.strip() or "unknown",
    "toolchain": subprocess.run([cxx, "--version"], capture_output=True, text=True).stdout.splitlines()[0].strip(),
    "fw_payload_bin": {"path": os.path.join(dest, "fw_payload.bin"), "sha256": sha(os.path.join(dest, "fw_payload.bin"))},
    "rootfs_cpio": {"path": os.path.join(dest, "rootfs.cpio"), "sha256": sha(os.path.join(dest, "rootfs.cpio"))},
    "nemu_bin": {"path": nemu, "sha256": sha(nemu) if os.path.exists(nemu) else None},
    "pins": {
        "workload_builder": wb,
        "opensbi": opensbi,
        "libcheckpointalpha": lca,
        "linux": linux_pin,
        "busybox": busybox_pin,
    },
}
with open(os.path.join(dest, "meta.deploy.json"), "w") as f:
    json.dump(meta, f, indent=2, sort_keys=True); f.write("\n")
print("   fw_payload.bin: %d bytes, sha256 %s" % (os.path.getsize(os.path.join(dest, "fw_payload.bin")), meta["fw_payload_bin"]["sha256"][:16]))
PY

echo "== done: $DEST/fw_payload.bin"
