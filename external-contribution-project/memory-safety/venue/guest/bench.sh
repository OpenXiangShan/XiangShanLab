#!/bin/sh
# Guest-side driver for the cpu-sec-bench NEMU platform.  Runs as the ::once:
# action of /etc/inittab inside the initramfs guest (busybox sh; the console is
# the NEMU serial output).
#
# Verbatim port of cpu-sec-test-env scripts/riscv64_nemu/venue/guest/bench.sh
# (2026-10-31); its ===CSB-...=== marker format is load-bearing for the host
# boot driver (venue/boot.sh) - a change here must be mirrored there.
#
# It runs the prebuilt corpus through the scheduler, prints the result files
# between ===CSB-...=== markers (the host harness extracts them from the boot
# log), and finally hands the scheduler's exit code to NEMU through
# nemu-trap: 0 reports HIT GOOD TRAP, non-zero HIT BAD TRAP.
set -u

echo "===CSB-RUN-BEGIN==="

cd /bench || { echo "===CSB-EXIT-CODE===127==="; exec /bench/nemu-trap 127; }
./run-test no-make corpus exhausted-run
rc=$?
echo "===CSB-EXIT-CODE===${rc}==="

# keep kernel chatter out of the dumps below
dmesg -n 1 2>/dev/null || true

for f in results.json variables.json results.dat; do
    [ -f "$f" ] || continue
    case "$f" in
        results.json)   tag=RESULTS-JSON ;;
        variables.json) tag=VARIABLES-JSON ;;
        results.dat)    tag=RESULTS-DAT ;;
    esac
    echo "===CSB-${tag}-BEGIN==="
    cat "$f"
    echo ""   # the scheduler's json has no trailing newline: keep the END marker on its own line
    echo "===CSB-${tag}-END==="
done

echo "===CSB-RUN-END==="
exec /bench/nemu-trap "$rc"
