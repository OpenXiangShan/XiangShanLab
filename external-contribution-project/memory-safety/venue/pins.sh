# pins.sh - the single source of truth for the NEMU venue's upstream pins.
#
# Sourced by run_cpu_sec_bench_nemu.sh and every script under venue/.  POSIX
# sh only: no bashisms, no side effects.  One pin per input, nowhere else.
#
# Provenance: the values are carried from the cpu-sec-test-env riscv64-nemu
# lane's env.sh (verified there 2026-10-31, stamp 20261031T061145Z).  They
# name upstream projects, not the env repo; this venue is independent of it.
#
# NOT pinned here: the cpu-sec-bench checkout and the NEMU interpreter.  Both
# are the user's own clones at arbitrary locations (the driver takes their
# paths); the run records the bench HEAD/tree and the NEMU binary sha256 per
# stamp instead of pinning them.

# ---- guest image upstream inputs (fetched once into $WORK/src, reused) ----
# workload-builder: the assembler scripts (gcpt/OpenSBI/firmware packing) and
# the NEMU guest-kernel fragment the image build reads.
WB_COMMIT=b7c321dec6ad6f4c78abcaefc722b59b4043503c
WB_URL=https://github.com/OpenXiangShan/workload-builder
# OpenSBI fw_jump bootloader, built with workload-builder's config and patch.
OPENSBI_COMMIT=cbf9f6734dd85a982c63e3cb5db7ffe09da839ca
OPENSBI_URL=https://github.com/riscv-software-src/opensbi
# LibCheckpointAlpha - the gcpt startup payload the image carries at DRAM base.
LCA_COMMIT=5169da768d18f4682a489eaabe7c071df6d6ef88
LCA_URL=https://github.com/openxiangshan/LibCheckpointAlpha

# Guest kernel.  6.18 LTS, not an older pin: the cross glibc's memcpy executes
# vector instructions (the apt toolchain is built to the RVA23U64 profile), so
# the kernel must support userspace vector (Linux 6.5+) and default it on.
KERNEL_VERSION=6.18.56
KERNEL_URL=https://cdn.kernel.org/pub/linux/kernel/v6.x/linux-6.18.56.tar.xz
KERNEL_SHA256=4633bbda2ab95eaf2a7af4041236451d6759c72e6946265531af4b3aacefbf89

# Static guest userspace.
BUSYBOX_VERSION=1.36.1
BUSYBOX_URL=https://busybox.net/downloads/busybox-1.36.1.tar.bz2
BUSYBOX_SHA256=b8cc24c9574d809e7279c3be349795c5d5ceb6fdf19ca709f80cde50e47de314

# ---- cross toolchain (Ubuntu gcc-13 cross packages) ----
# CC= is pinned to the versioned gcc-13 everywhere: the corpus is built with
# g++-13, and an unversioned riscv64-linux-gnu-gcc may be a different major.
RISCV64_CXX=riscv64-linux-gnu-g++-13
RISCV64_CC=riscv64-linux-gnu-gcc-13
RISCV64_AS=riscv64-linux-gnu-as
RISCV64_OBJDUMP=riscv64-linux-gnu-objdump

# ---- the bench token this venue runs under ----
# The registry's skip rows are written against this token, and the guest
# image is the same cross build as the plain riscv64 platform - only the
# interpreter differs.
BENCH_ARCH=riscv64
BENCH_TOKEN=riscv64

# ---- bounds (seconds); a pre-set value in the environment survives ----
: "${NEMU_TIMEOUT:=3600}"    # the boot driver's inner NEMU bound
: "${DEPLOY_TIMEOUT:=7200}"  # the driver's outer bound per deploy leg

# ---- reproducibility identity pins (kernel/busybox/image bytes) ----
KBUILD_BUILD_TIMESTAMP="Tue Oct 12 00:00:00 UTC 2026"
KBUILD_BUILD_USER=cpu-sec-bench-nemu
KBUILD_BUILD_HOST=cpu-sec-bench-nemu
SOURCE_DATE_EPOCH=1791763200
