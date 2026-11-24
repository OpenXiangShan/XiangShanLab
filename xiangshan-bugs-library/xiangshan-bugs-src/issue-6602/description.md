### Before start
- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A unit-stride vector load executed with `vl=0` right after `sstatus.VS` is written to `Clean` wrongly transitions VS to Dirty. Verified for `vle8.v` / `vle16.v` / `vle32.v` / `vle64.v`, **both unmasked and masked** (`, v0.t` with vd ≠ v0), and independent of vd. This is the same bug class as #6561 (Branch: kunminghu-v2, open) — but not a duplicate of it: the path reported there no longer reproduces, while the load path below still does — with the same mismatch signature:

```
mstatus different at pc = ..., right = 0x0000000a00000400, wrong = 0x8000000a00000600
sstatus different at pc = ..., right = 0x0000000200000400, wrong = 0x8000000200000600
```

diff = `0x8000000000000200` (SD set, VS Clean→Dirty), i.e. XiangShan marks VS Dirty although a `vl=0` load writes no vector register and no vector CSR. Two independent observations per case: (a) difftest aborts with the signature above; (b) with `--no-diff`, an in-program check of `sstatus.VS` after the load takes the "VS became Dirty" exit.

**What does not reproduce** (all verified by reaching the program's own "VS stayed Clean" exit, not merely by absence of a mismatch): `vse64.v v0, (t1)` (unit-stride store, vl=0, unmasked); `vse64.v v1, (t1), v0.t` (masked store); `vsm.v v0, (t1)` (whole-register store); the originally-reported `vsseg4e32.v` segment-store seed from #6561 — seems fixed.

Out of scope: `vle64.v v0, (t1), v0.t` (vd = v0 with mask) is a *reserved encoding* (masked vd=v0 rule, RVV 1.0) and correctly raises illegal-instruction — excluded.

### Expected behavior

Per the RISC-V Privileged Specification, `mstatus.VS` becomes Dirty only when a vector register or vector CSR is *written*. A `vl=0` load performs no memory access and writes no vector register and no vector CSR, so **VS must remain Clean** (as NEMU models it).

### Environment
- Hardware
  - CPU: AMD EPYC 7543 32-Core Processor (128 hardware threads)
  - Memory (GB): 247
  - Storage (GB): 3727
- Software
  - Operating system: Ubuntu 26.04.1 LTS
  - gcc version: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0
  - clang version: not used
  - java version: openjdk 25.0.2 (2026-01-20)
  - mill version: 0.12.3 (repository wrapper `./mill`)
- Repo
  - XiangShan commit id: `f86f50b834c26597426b5ede7c25afaff84d11ce` (branch `kunminghu-v2`)
  - NEMU commit id (if difftest failed with NEMU): ready-to-run submodule @ `5a63554d` (riscv64-nemu-interpreter-so)
  - SPIKE commit id (if difftest failed with SPIKE): not used
- Build & Run
  - Build command: `NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 -j112`
  - Run command (if applicable): `./build/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so --dump-commit-trace -C 100000`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable. (source inlined below; ELF available on request)

### To Reproduce

1. Build the emulator:

```bash
git clone https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan
git checkout kunminghu-v2
make init
NOOP_HOME=$(pwd) make emu CONFIG=MinimalConfig EMU_THREADS=4 -j$(nproc)
```

2. Save the PoC as `poc.S` (self-contained; a trap handler with an illegal-branch and three distinct GOODTRAP exits so every outcome is identifiable by the exit pc printed by the emulator):

```asm
.option norvc
.section .text.init
.globl _start
_start:
    csrr t0, mstatus
    li   t1, 0x600            # mstatus.VS = Dirty
    or   t0, t0, t1
    csrw mstatus, t0
    csrw medeleg, x0
    la   t0, trap_handler
    csrw mtvec, t0
    vsetivli zero, 0, e64, m1, tu, ma   # vl = 0
    li   t1, 0x80001000
    li   t0, 0x400            # sstatus.VS = Clean (10)
    csrw sstatus, t0
    vle64.v v0, (t1)          # <-- vl=0 load: writes no vector state
    csrr t0, sstatus
    li   t1, 0x600
    and  t0, t0, t1
    li   t1, 0x400            # expected: still Clean
    bne  t0, t1, 4f           # Dirty => bug reproduced
    .4byte 0x0000006b         # exit A (VS stayed Clean)  <- NOT taken when bug present
4:  .4byte 0x0000006b         # exit B (VS became Dirty)
trap_handler:
    csrr t0, mcause
    li   t1, 2
    bne  t0, t1, 5f
    .4byte 0x0000006b         # exit C (illegal-instruction trap taken)
5:  j 5b
```

3. Build and run (single copy-paste; includes the linker script):

```bash
cat > link.ld <<'LD'
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS {
  . = 0x80000000;
  .text.init : { KEEP(*(.text.init)) }
  . = ALIGN(0x1000); .text : { *(.text .text.*) }
  . = ALIGN(0x1000); .data : { *(.data .data.* .sdata .sdata.*) }
  .bss (NOLOAD) : { *(.bss .bss.* COMMON) }
  /DISCARD/ : { *(.comment) *(.note*) *(.riscv.attributes) }
}
LD
riscv64-unknown-elf-gcc -nostdlib -nostartfiles -static -fno-pic \
  -march=rv64gcv -mabi=lp64d -Wl,-T,link.ld -Wl,-e,_start -o poc.elf poc.S
./build/emu -i poc.elf --diff ready-to-run/riscv64-nemu-interpreter-so \
  --dump-commit-trace -C 100000
./build/emu -i poc.elf --no-diff -C 100000
```

4. Observed: with `--diff`, difftest aborts with the mismatch above (same signature as #6561). With `--no-diff`, the run exits at **exit B** ("HIT GOOD TRAP at pc = <exit B address>") — the program's own check confirms sstatus.VS became Dirty. The store variants exit at exit A (VS stays Clean).

5. Substitutions (swap the payload line; all verified with both observation methods): `vle8.v v0, (t1)`; `vle32.v v0, (t1)`; `vle64.v v1, (t1)`; **`vle64.v v1, (t1), v0.t` (masked — also reproduces)**. Non-reproducing contrast: `vse64.v v0, (t1)`, `vse64.v v1, (t1), v0.t` (both exit A). Reserved-encoding case (correctly traps, exit C): `vle64.v v0, (t1), v0.t`.

### Additional context

- Same bug class as #6561 (Branch: kunminghu-v2, open): identical mismatch signature (`diff = 0x8000000000000200`, SD set + VS Clean→Dirty).
- On this commit the originally-reported segment-store path no longer reproduces (the exact `vsseg4e32.v` seed exits at "VS stayed Clean"); unit-stride and whole-register stores are also clean. The **load path** — masked and unmasked — still transitions VS to Dirty.
- Suggested conclusion: the fix that addressed the store path appears not to cover the load path, including masked loads.
- Scope: reproduced on kunminghu-v2 @ `f86f50b83` (see Branch). On the default branch kunminghu-v3 @ `c8d7b3a5` the same PoC does not reach this path (an unrelated vtype/vill difftest divergence fires first), so this report is scoped to v2.
- This report was prepared with AI assistance; all commands and quoted outputs were re-executed and verified on the commits above.
