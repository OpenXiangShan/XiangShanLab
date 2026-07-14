---
name: fill-queues
description: Generate, build, run, and iterate XiangShan bare-metal assembly tests that try to fill microarchitectural queues and buffers using ELF/BIN images and existing emulator logs.
---

# Fill Queues

Use this skill when asked to create or improve XiangShan queue/buffer fill tests, confirm whether a queue filled, or iterate assembly based on emulator logs.

## Workflow

1. Work from the XiangShan repo root.
2. Generate or edit assembly under `fillQueues/asm/*.S`.
3. Build tests with `fillQueues/scripts/build_tests.sh`.
4. Run tests with `fillQueues/scripts/run_tests.sh`, or directly:
   `./build/emu --no-diff -i fillQueues/out/<test>.bin`.
5. Summarize markers with `fillQueues/scripts/check_logs.py`.
6. If the target marker is missing, inspect the log, then modify the relevant `fillQueues/asm/fill_*.S` case to create stronger backpressure.

Do not inspect or edit Verilator-generated C/C++ files for this workflow. Do not rebuild the emulator unless the user explicitly asks for RTL changes.

## Test Selection

- ROB: use a long-latency load at the head followed by many independent ALU uops.
- Load queue: stream independent loads across many cache lines/pages.
- Store queue / SBuffer: stream stores across many cache lines without an early fence.
- Issue queues: create dependency chains behind a long-latency producer.
- FTQ/IBuffer: use dense branch/fetch patterns.
- MSHR/miss queues: spread loads/stores across cache lines and pages to increase outstanding misses.

## Iteration Rules

- For a missing full marker, increase pressure in this order: more operations, wider address spread, stronger head-of-ROB stall, then specialized instruction mix.
- Record every run in `fillQueues/logs`, and use marker names from the log to decide whether the target queue actually filled.