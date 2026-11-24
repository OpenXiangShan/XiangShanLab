### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

On `kunminghu-v3` with `DefaultConfig`, `vsha2ch.vv`, `vsha2cl.vv` and `vsha2ms.vv` at SEW 32 raise no exception and are not committed: the core stops committing at the instruction. The decoder's extension list includes Zvknha, but since [`b397ec61`](https://github.com/OpenXiangShan/XiangShan/commit/b397ec61ddb43346ebf426ca8db509a01ada9602) no execution unit lists `VSha256msCfg` or `VSha256cCfg`, which [`474cb4f3`](https://github.com/OpenXiangShan/XiangShan/commit/474cb4f3ae79601a4c9b609205f6cd17c3b16e91) had placed in `VFEX2` and `VFEX3`. Zvknha is not in the ISA string, and the reference raises an illegal-instruction exception.

A U-mode program stops the core the same way, so an unprivileged program gets no illegal-instruction exception for these instructions. A machine timer interrupt that comes due while the core is stopped is taken, with `mepc` at the vsha2 instruction; we did not run a handler that returns to the instruction.

[#6644](https://github.com/OpenXiangShan/XiangShan/issues/6644) is related but is not this bug. It reports `vfredusum.vs` accepted with no issue-queue selection, which stops the core the same way. At this commit the reduction uops of `vfredusum.vs` are of type `vredu`, and no `FuConfig` has that type, so that instruction is not implemented yet. Here the extension is not declared, so the expected behavior is an illegal-instruction exception whether or not the SHA-256 units are ported to the new function-unit framework.

No pull-request head numbered up to 6709, open or closed, names either configuration in `Parameters.scala` or `BackendParams.scala`, gives either unit a `VecFuConfig` generator or ports its wrapper to `VecFuConfig`. Pull requests 6277 and 6282 add a `VcryptoCfg` unit in the new framework, for Zvbc only.

#### Declared and decoded

The ISA string names `zvbb` and `zvkt` and no `zvknha`, `zvknhb` or `zvkn`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L305-L318
  def ISAExtensions = Seq(
    // single letter extensions, in canonical order
    "i", "m", "a", "f", "d", "c", "b", "v", "h",
    // multi-letter extensions, sorted alphanumerically
    "sdext", "sdtrig", "sha", "shcounterenw", "shgatpa", "shlcofideleg", "shtvala", "shvsatpa", "shvstvala",
    "shvstvecd", "smaia", "smcdeleg", "smcntrpmf", "smcsrind", "smdbltrp", "smmpm", "smnpm", "smrnmi", "smstateen",
    "ss1p13", "ssaia", "ssccfg", "ssccptr", "sscofpmf", "sscounterenw", "sscsrind", "ssdbltrp", "ssnpm",
    "sspm", "ssstateen", "ssstrict", "sstc", "sstvala", "sstvecd", "ssu64xl", "supm", "sv39",
    "sv48", "svade", "svbare", "svinval", "svnapot", "svpbmt", "za64rs", "zabha", "zacas", "zama16b", "zawrs", "zba",
    "zbb", "zbc", "zbkb", "zbkc", "zbkx", "zbs", "zcb", "zcmop", "zfa", "zfh", "zfhmin", "zic64b",
    "zicbom", "zicbop", "zicboz", "ziccamoa", "ziccamoc", "ziccif", "zicclsm", "ziccrse", "zicntr", "zicond",
    "zicsr", "zifencei", "zihintntl", "zihintpause", "zihpm", "zimop", "zkn", "zknd", "zkne", "zknh",
    "zksed", "zksh", "zkt", "zvbb", "zvfh", "zvfhmin", "zvkt", "zvl128b", "zvl32b", "zvl64b"
  )
```

The decoder's extension list includes `Zvknha`, and the vector decode channel takes its instructions:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/decode/isa/Extensions.scala#L156-L170
  def extensions(implicit p: Parameters): Seq[ExtBase] = Seq(
    I, M, A, F, D, Zicsr,
    System, S, Svinval,
    Za64rs, Zabha, Zacas, ZacasZabha, Zawrs,
    Zba, Zbb, Zbc, Zbs, Zbkb, Zbkc, Zbkx,
    V, H,
    Zvknha,
    Zvbb,
    XSTrap,
    Zicbo,
    Zicond,
    Zifencei, Zknd, Zkne, Zknh, Zksed, Zksh,
    // Zcb, Zcmop,
    ZfaF, ZfaD, ZfaZfh, Zfh, Zfhmin, ZfhminD,
  ) ++ Option.when(p(XSCoreParamsKey).HasMptCheck && !p(XSCoreParamsKey).HasBitmapCheck)(Smmtt).toSeq
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeChannels.scala#L58
  val vectorExts = extensions.intersect(Seq(V, Zvbb, Zvknha, Zacas, ZacasZabha))
```

#### The runs

The program under "To Reproduce" sets `vl` to 4 at SEW 32 with `vsetivli`, fills `v1`, `v2` and `v4` with `vmv.v.i`, executes one instruction at `0x80000024`, reads `v4` with `vmv.x.s`, and ends on the emulator's trap instruction at `0x80000030`. Its trap handler saves `mcause` in `a1` and ends the run on the same instruction at `0x8000003c`, so the pc in the emulator's trap line shows whether an exception was taken; at this commit that line reads `HIT GOOD TRAP` at both ends, although the handler sets `a0` to 1. With each instruction in turn at `0x80000024`:

| Instruction at `0x80000024` | Without DiffTest | With DiffTest |
| --- | --- | --- |
| `vadd.vv` | `HIT GOOD TRAP at pc = 0x80000030`, the program's end | `HIT GOOD TRAP at pc = 0x80000030`, the program's end |
| `vghsh.vv` | `HIT GOOD TRAP at pc = 0x8000003c`, the handler's end | `HIT GOOD TRAP at pc = 0x8000003c`, the handler's end |
| `vsha2ch.vv` | no trap; `instrCnt = 15` at the cycle limit, 30,000 and 60,000 cycles | no commit for 15,000 cycles, `instrCnt = 15`; last commit at `0x80000020` |
| `vsha2cl.vv` | no trap; `instrCnt = 15` at the cycle limit, 30,000 and 60,000 cycles | no commit for 15,000 cycles, `instrCnt = 15`; last commit at `0x80000020` |
| `vsha2ms.vv` | no trap; `instrCnt = 15` at the cycle limit, 30,000 and 60,000 cycles | no commit for 15,000 cycles, `instrCnt = 15`; last commit at `0x80000020` |

`vadd.vv` runs to the end. `vghsh.vv` raises an exception, and the handler ends the run; under DiffTest the reference takes the same path. With each of the three vsha2 instructions there is no trap line, and the commit count is the same at both cycle limits. Under DiffTest the commit trace ends at `0x80000020` (`vmv.v.i v4,3`), the instruction before the vsha2 instruction, and the reference, which DiffTest then runs one more instruction, takes an illegal-instruction exception (`mcause` 2) at `0x80000024` with the vsha2 instruction in `mtval`.

<details>
<summary>Emulator output for <code>vsha2ch.vv</code>, without and with DiffTest</summary>

With `vsha2ch.vv`, without DiffTest:

```
Core 0: EXCEEDING CYCLE/INSTR LIMIT at pc = 0x164cad89b81ef
Core-0 instrCnt = 15, cycleCnt = 30000, IPC = 0.000500
```

With DiffTest (the program's part of the commit trace, and the reference's registers after its extra instruction):

```
No instruction of core 0 commits for 15000 cycles, maybe get stuck
Let REF run one more instruction.
...
[06] commit pc 0000000080000000 inst 00000297 wen 1 dst 05 data 0000000080000000 idx 006
[07] commit pc 0000000080000004 inst 03428293 wen 1 dst 05 data 0000000080000034 idx 006
[08] commit pc 0000000080000008 inst 30529073 wen 0 dst 00 data 0000000080000000 idx 007
[09] commit pc 000000008000000c inst 60000293 wen 1 dst 05 data 0000000000000600 idx 008
[10] commit pc 0000000080000010 inst 3002a073 wen 0 dst 00 data 0000000000000600 idx 009
[11] commit pc 0000000080000014 inst cd027057 wen 0 dst 00 data 0000000000000600 idx 00a
[12] commit pc 0000000080000018 inst 5e00b0d7 wen 1 dst 01 data 0000000000000600 idx 00b
[13] commit pc 000000008000001c inst 5e013157 wen 1 dst 02 data 0000000000000600 idx 00c
[14] commit pc 0000000080000020 inst 5e01b257 wen 1 dst 04 data 0000000000000600 idx 00d <--
...
pc: 0x0000000080000034  privilege mode: M (mode: 3  v: 0  debug: 0)
mcause: 0x0000000000000002      mepc: 0x0000000080000024     mtval: 0x00000000ba20a277
```

The pc printed with the limit and abort lines is not a program address; the commit trace is where the core stopped.

`instrCnt = 15` counts the six instructions the emulator's flash runs before it jumps to `0x80000000`, and the nine instructions of the program before the vsha2 instruction.

</details>

Without `vmv.x.s`, so with nothing reading `v4`, the program stops at the same place: its commit trace also ends at `0x80000020`. The stop does not depend on a later instruction reading the result.

The same emulator also ran `vsha2ch.vv` in U mode, with the machine timer armed in M mode and in U mode, and at SEW 64, each beside a control:

| Program | Mode | Timer | SEW | Without DiffTest | With DiffTest |
| --- | --- | --- | --- | --- | --- |
| `vadd.vv` | U | no | 32 | trap at `0x80000058`, the program's end | trap at `0x80000058`, the program's end |
| `vghsh.vv` | U | no | 32 | trap at `0x80000070`, the exception end | trap at `0x80000070`, the exception end |
| `vsha2ch.vv` | U | no | 32 | no trap; `instrCnt = 26` at the cycle limit, 30,000 and 60,000 cycles | no commit for 15,000 cycles, `instrCnt = 26`; last commit at `0x8000004c` |
| `vadd.vv` | M | armed | 32 | trap at `0x80000088`, the interrupt end, with `mepc` elsewhere | trap at `0x80000088`, the interrupt end, with `mepc` elsewhere |
| `vsha2ch.vv` | M | armed | 32 | trap at `0x80000080`, the interrupt end, with `mepc` at the vsha2 instruction | trap at `0x80000080`, the interrupt end, with `mepc` at the vsha2 instruction |
| `vadd.vv` | U | armed | 32 | trap at `0x800000b0`, the interrupt end, with `mepc` elsewhere | trap at `0x800000b0`, the interrupt end, with `mepc` elsewhere |
| `vsha2ch.vv` | U | armed | 32 | trap at `0x800000a8`, the interrupt end, with `mepc` at the vsha2 instruction | trap at `0x800000a8`, the interrupt end, with `mepc` at the vsha2 instruction |
| `vsha2ch.vv` | M | no | 64 | trap at `0x80000044`, the exception end | trap at `0x80000044`, the exception end |

In U mode `vsha2ch.vv` stops the core the same way. With the timer armed, the interrupt is taken in both modes, with `mepc` at the vsha2 instruction. At SEW 64, `vsha2ch.vv` reaches the exception handler, which ends on its exception word; the run does not print `mcause`, but DiffTest compares it with NEMU's after the trap and found no difference. This row shows only that the instruction has a trap path, not which check takes it: the use is invalid in three ways, given in the collapsed block below.

<details>
<summary>How these programs are set up, why the SEW 64 row is not a valid use, and their ELF digests</summary>

These programs have no `vmv.x.s` after the instruction, which the stop does not depend on. The U-mode programs enter U mode through `mret`, with PMP entry 0 covering all memory. The timer programs read `mtime` (`0x3800bff8`), set `mtimecmp` (`0x38004000`) 100 ticks later, which is about 10,000 cycles since the RTC runs at a hundredth of the core clock, enable MTIE (and MIE in M mode) before the instruction, and spin after it. The handler ends an interrupt at its own trap word, one for `mepc` at the vsha2 instruction and one for any other `mepc`.

The SEW 64 row is not a valid use under either extension: `vl` is 2, not a multiple of the element group size 4, which [is reserved](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/unpriv/zvk.adoc#L253-L257); under Zvknha any SEW other than 32 is a [reserved encoding](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/unpriv/zvk.adoc#L2333); and under Zvknhb SHA-512's 256-bit element group is wider than LMUL×VLEN = 128, for which the [vector crypto chapter](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/unpriv/zvk.adoc#L274-L275) requires an illegal-instruction exception.

The ELFs of these programs, built from sources in the same layout, have these sha256 digests:

- `vsha2_minimal_vadd_u.elf` (vadd.vv, U mode, no timer, SEW 32): `54973155252d650bc6c231cd5c60328e6c98c879710e54704d29ab3d0ecfb4c1`
- `vsha2_minimal_vghsh_u.elf` (vghsh.vv, U mode, no timer, SEW 32): `9beada58006a8ddfe6f32865a692d3cd219c56ef483179cb268ca1f82d1abf5a`
- `vsha2_minimal_vsha2ch_u.elf` (vsha2ch.vv, U mode, no timer, SEW 32): `3aeaf5a9d7bfb308c7ba955cf46f4376088cf2e81f9539091178924dc076428b`
- `vsha2_minimal_vadd_irq.elf` (vadd.vv, M mode, timer armed, SEW 32): `6b43db879e786c10dea74b0267a452ba4a90b24131d8ac1909c1e38688f99496`
- `vsha2_minimal_vsha2ch_irq.elf` (vsha2ch.vv, M mode, timer armed, SEW 32): `9727d0ef206fd342bfdc092a05fed1c2a4446152f65789e5c3d7a9c6097a8a0d`
- `vsha2_minimal_vadd_u_irq.elf` (vadd.vv, U mode, timer armed, SEW 32): `2ee71142834b447c06fdcfb0aaf4398d9ab56177f64dcf57aad3ef9232b00317`
- `vsha2_minimal_vsha2ch_u_irq.elf` (vsha2ch.vv, U mode, timer armed, SEW 32): `3543e1b77e0d80a37e87f81a9ca1e88cde1feea4d37cdd005dcbdbc0612749da`
- `vsha2_minimal_vsha2ch_e64.elf` (vsha2ch.vv, M mode, no timer, SEW 64): `bdfc034237e6ecf6487c1fff90004bd2bcc11bac577b1640163bb8f313eae6df`

</details>

<details>
<summary><code>vsha2_minimal_vsha2ch_u_irq.S</code>, the U-mode program with the timer armed</summary>

```asm
# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
# Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
# end_interrupt_at_subject or end_interrupt_elsewhere.
    .section .text
    .globl _start
_start:
    la      t0, on_trap
    csrw    mtvec, t0
    li      t0, 0x600               # mstatus.VS = 3: the vector unit is on
    csrs    mstatus, t0
    li      t1, 0x3800bff8         # mtime
    ld      t2, 0(t1)
    addi    t2, t2, 100             # due in 100 ticks, about 10,000 cycles
    li      t1, 0x38004000         # mtimecmp
    sd      t2, 0(t1)
    li      t0, 0x80                # mie.MTIE
    csrs    mie, t0
    li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
    csrw    pmpaddr0, t0
    li      t0, 0x1f
    csrw    pmpcfg0, t0
    li      t0, 0x1800              # mstatus.MPP = U
    csrc    mstatus, t0
    la      t0, user
    csrw    mepc, t0
    mret
user:
    vsetivli zero, 4, e32, m1, ta, ma
    vmv.v.i v1, 1
    vmv.v.i v2, 2
    vmv.v.i v4, 3
subject:
    vsha2ch.vv v4, v2, v1
spin:
    j       spin                    # waits for the timer
finish:
    .word   0x0005006b              # the program's end
on_trap:
    csrr    a1, mcause
    bltz    a1, on_interrupt
    li      t0, 8                   # ecall from U mode
    beq     a1, t0, finish
    li      a0, 1
end_exception:
    .word   0x0005006b              # the end when an exception is taken
on_interrupt:
    csrr    t1, mepc
    la      t2, subject
    bne     t1, t2, interrupted_elsewhere
    li      a0, 2
end_interrupt_at_subject:
    .word   0x0005006b              # an interrupt taken with mepc at the subject
interrupted_elsewhere:
    li      a0, 3
end_interrupt_elsewhere:
    .word   0x0005006b              # an interrupt taken anywhere else
```

</details>

<details>
<summary>How each other program's source differs from <code>vsha2_minimal_vsha2ch_u_irq.S</code></summary>

```diff
--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vadd_u.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vadd.vv v4, v2, v1 at SEW 32 in U mode, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -10,9 +10,2 @@
     csrs    mstatus, t0
-    li      t1, 0x3800bff8         # mtime
-    ld      t2, 0(t1)
-    addi    t2, t2, 100             # due in 100 ticks, about 10,000 cycles
-    li      t1, 0x38004000         # mtimecmp
-    sd      t2, 0(t1)
-    li      t0, 0x80                # mie.MTIE
-    csrs    mie, t0
     li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
@@ -32,5 +25,4 @@
 subject:
-    vsha2ch.vv v4, v2, v1
-spin:
-    j       spin                    # waits for the timer
+    vadd.vv v4, v2, v1
+    ecall                           # the user program's end: the handler goes to finish
 finish:

--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vghsh_u.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vghsh.vv v4, v2, v1 at SEW 32 in U mode, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -10,9 +10,2 @@
     csrs    mstatus, t0
-    li      t1, 0x3800bff8         # mtime
-    ld      t2, 0(t1)
-    addi    t2, t2, 100             # due in 100 ticks, about 10,000 cycles
-    li      t1, 0x38004000         # mtimecmp
-    sd      t2, 0(t1)
-    li      t0, 0x80                # mie.MTIE
-    csrs    mie, t0
     li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
@@ -32,5 +25,4 @@
 subject:
-    vsha2ch.vv v4, v2, v1
-spin:
-    j       spin                    # waits for the timer
+    vghsh.vv v4, v2, v1
+    ecall                           # the user program's end: the handler goes to finish
 finish:

--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vsha2ch_u.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -10,9 +10,2 @@
     csrs    mstatus, t0
-    li      t1, 0x3800bff8         # mtime
-    ld      t2, 0(t1)
-    addi    t2, t2, 100             # due in 100 ticks, about 10,000 cycles
-    li      t1, 0x38004000         # mtimecmp
-    sd      t2, 0(t1)
-    li      t0, 0x80                # mie.MTIE
-    csrs    mie, t0
     li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
@@ -33,4 +26,3 @@
     vsha2ch.vv v4, v2, v1
-spin:
-    j       spin                    # waits for the timer
+    ecall                           # the user program's end: the handler goes to finish
 finish:

--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vadd_irq.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vadd.vv v4, v2, v1 at SEW 32 in M mode, with the machine timer armed, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -17,12 +17,3 @@
     csrs    mie, t0
-    li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
-    csrw    pmpaddr0, t0
-    li      t0, 0x1f
-    csrw    pmpcfg0, t0
-    li      t0, 0x1800              # mstatus.MPP = U
-    csrc    mstatus, t0
-    la      t0, user
-    csrw    mepc, t0
-    mret
-user:
+    csrsi   mstatus, 8              # mstatus.MIE
     vsetivli zero, 4, e32, m1, ta, ma
@@ -32,3 +23,3 @@
 subject:
-    vsha2ch.vv v4, v2, v1
+    vadd.vv v4, v2, v1
 spin:

--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vsha2ch_irq.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vsha2ch.vv v4, v2, v1 at SEW 32 in M mode, with the machine timer armed, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -17,12 +17,3 @@
     csrs    mie, t0
-    li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
-    csrw    pmpaddr0, t0
-    li      t0, 0x1f
-    csrw    pmpcfg0, t0
-    li      t0, 0x1800              # mstatus.MPP = U
-    csrc    mstatus, t0
-    la      t0, user
-    csrw    mepc, t0
-    mret
-user:
+    csrsi   mstatus, 8              # mstatus.MIE
     vsetivli zero, 4, e32, m1, ta, ma

--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vadd_u_irq.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vadd.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -32,3 +32,3 @@
 subject:
-    vsha2ch.vv v4, v2, v1
+    vadd.vv v4, v2, v1
 spin:

--- vsha2_minimal_vsha2ch_u_irq.S
+++ vsha2_minimal_vsha2ch_e64.S
@@ -1,2 +1,2 @@
-# vsha2ch.vv v4, v2, v1 at SEW 32 in U mode, with the machine timer armed, with nothing reading v4 afterwards.
+# vsha2ch.vv v4, v2, v1 at SEW 64 in M mode, with nothing reading v4 afterwards.
 # Each end of the run is a trap word 0x0005006b at its own pc: finish, end_exception,
@@ -10,20 +10,3 @@
     csrs    mstatus, t0
-    li      t1, 0x3800bff8         # mtime
-    ld      t2, 0(t1)
-    addi    t2, t2, 100             # due in 100 ticks, about 10,000 cycles
-    li      t1, 0x38004000         # mtimecmp
-    sd      t2, 0(t1)
-    li      t0, 0x80                # mie.MTIE
-    csrs    mie, t0
-    li      t0, -1                  # PMP entry 0 covers all memory, RWX, so U mode may run
-    csrw    pmpaddr0, t0
-    li      t0, 0x1f
-    csrw    pmpcfg0, t0
-    li      t0, 0x1800              # mstatus.MPP = U
-    csrc    mstatus, t0
-    la      t0, user
-    csrw    mepc, t0
-    mret
-user:
-    vsetivli zero, 4, e32, m1, ta, ma
+    vsetivli zero, 2, e64, m1, ta, ma
     vmv.v.i v1, 1
@@ -33,4 +16,3 @@
     vsha2ch.vv v4, v2, v1
-spin:
-    j       spin                    # waits for the timer
+    li      a0, 0
 finish:
```

</details>

#### Where it comes from

The decoder splits these instructions into uops of function-unit types `vsha256c` and `vsha256ms`, and `FuConfig.scala` defines `VSha256cCfg` and `VSha256msCfg` for them.

<details>
<summary>The decoder's split table and function-unit types, and the two configurations</summary>

The decoder splits these instructions into uops whose function-unit types are `vsha256c` and `vsha256ms`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Decoder/Split/SplitTable.scala#L1123-L1125
      VSHA2MS_VV -> dup(null, null, vsha256ms, null)(_.S1v),
      VSHA2CL_VV -> dup(null, null, vsha256cl, null)(_.S1v),
      VSHA2CH_VV -> dup(null, null, vsha256ch, null)(_.S1v),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Decoder/DecodeFields/VecDecodeChannel/FuTypeField.scala#L88-L89
      case _: Opcode.VSha256msOpcodes.type => FuType.vsha256ms
      case _: Opcode.VSha256cOpcodes.type => FuType.vsha256c
```

`FuConfig.scala` defines a configuration for each of the two types:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/FuConfig.scala#L724-L727
  val VSha256msCfg = FuConfig(
    name = "vsha256ms",
    fuType = FuType.vsha256ms,
    fuGen = (p: Parameters, cfg: FuConfig) => Module(new VSha256msWrapper(cfg)(p).suggestName("VSha256ms")),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/FuConfig.scala#L745-L748
  val VSha256cCfg = FuConfig(
    name = "vsha256c",
    fuType = FuType.vsha256c,
    fuGen = (p: Parameters, cfg: FuConfig) => Module(new VSha256cWrapper(cfg)(p).suggestName("VSha256c")),
```

</details>

No execution unit of the vector scheduler lists either configuration. These are the function-unit lists of `VFEX0` to `VFEX3`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L489-L490
          "VFEX0",
          Seq(VialuCfg, VimacCfg, VmoveCfg, VfcvtCfg, VfmacCfg),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L500-L501
          "VFEX1",
          Seq(VialuCfg, VidivCfg, VfmacCfg, VfdivCfg),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L511-L512
          "VFEX2",
          Seq(VialuCfg),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L522-L523
          "VFEX3",
          Seq(VialuCfg),
```

`DefaultConfig` uses `vecSchdParams`, because `EnableBackendV2Config` is `false` by default:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L586
      VecScheduler() -> (if (EnableBackendV2Config) backend.BackendV2SchdParams.vecSchdParams else vecSchdParams),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L120
  EnableBackendV2Config: Boolean = false,
```

<details>
<summary>The whole <code>vecSchdParams</code> block</summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L484-L553
  val vecSchdParams = {
    implicit val schdType: SchedulerType = VecScheduler()
    SchdBlockParams(Seq(
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX0",
          Seq(VialuCfg, VimacCfg, VmoveCfg, VfcvtCfg, VfmacCfg),
          Seq(VfWB(port = 3, 0), IntWB(port = 4, 1), FpWB(port = 6, 0)),
          Seq(Seq(VfRD(0, 0), FpRD(12, 1)), Seq(VfRD(1, 0)), Seq(VfRD(2, 0))),
          vlRD = VlRD(0, 0),
          v0WB = V0WB(0, 0),
          v0RD = V0RD(0, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX1",
          Seq(VialuCfg, VidivCfg, VfmacCfg, VfdivCfg),
          Seq(VfWB(port = 4, 0)),
          Seq(Seq(VfRD(3, 0), FpRD(13, 1)), Seq(VfRD(4, 0)), Seq(VfRD(5, 0))),
          vlRD = VlRD(1, 0),
          v0WB = V0WB(1, 0),
          v0RD = V0RD(1, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX2",
          Seq(VialuCfg),
          Seq(VfWB(port = 5, 0)),
          Seq(Seq(VfRD(6, 0)), Seq(VfRD(7, 0)), Seq(VfRD(8, 0))),
          vlRD = VlRD(2, 0),
          v0WB = V0WB(2, 0),
          v0RD = V0RD(2, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX3",
          Seq(VialuCfg),
          Seq(VfWB(port = 6, 0)),
          Seq(Seq(VfRD(9, 0)), Seq(VfRD(10, 0)), Seq(VfRD(11, 0))),
          vlRD = VlRD(3, 0),
          v0WB = V0WB(3, 0),
          v0RD = V0RD(3, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VSTD0",
          Seq(VStdCfg),
          Seq(),
          Seq(Seq(VfRD(12, 0))),
        )
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VSTD1",
          Seq(VStdCfg),
          Seq(),
          Seq(Seq(VfRD(13, 0))),
        )
      ), numEntries = 16, numEnq = 2, numComp = 14),
    ),
      numPregs = vfPreg.numEntries,
      numDeqOutside = 0,
      schdType = schdType,
      rfDataWidth = vfPreg.dataCfg.dataWidth,
    )
  }
```

</details>

`BackendV2SchdParams.vecSchdParams`, which `EnableBackendV2Config` selects, has two vector units and lists neither configuration either:

<details>
<summary><code>BackendV2SchdParams.vecSchdParams</code></summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/BackendParams.scala#L771-L799
  def vecSchdParams = {
    implicit val schdType: SchedulerType = VecScheduler()
    SchdBlockParams(Seq(
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX0",
          Seq(VialuCfg, VimacCfg, VfcvtCfg, VSetRvfWvfCfg, VmoveCfg),
          Seq(VfWB(port = 0, 0), V0WB(port = 0, 0), IntWB(port = 7, 0), FpWB(port = 6, 0)),
          Seq(Seq(VfRD(0, 0)), Seq(VfRD(1, 0)), Seq(VfRD(2, 0)), Seq(V0RD(0, 0))),
          vlWB = VlWB(port = vfSchdVlWbPort, 0),
          vlRD = VlRD(0, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 12),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX1",
          Seq(VialuCfg, VfdivCfg, VidivCfg),
          Seq(VfWB(port = 1, 0), V0WB(port = 1, 0), FpWB(port = 7, 0)),
          Seq(Seq(VfRD(3, 0)), Seq(VfRD(4, 0)), Seq(VfRD(5, 0)), Seq(V0RD(1, 0))),
          vlRD = VlRD(1, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 12),
    ),
      numPregs = numPregsVec,
      numDeqOutside = 0,
      schdType = schdType,
      rfDataWidth = rfDataWidthVec,
    )
  }
```

</details>

The two configurations were in `vecSchdParams`. [`474cb4f3`](https://github.com/OpenXiangShan/XiangShan/commit/474cb4f3ae79601a4c9b609205f6cd17c3b16e91) put `VSha256msCfg` in `VFEX2` and `VSha256cCfg` in `VFEX3`, and [`b397ec61`](https://github.com/OpenXiangShan/XiangShan/commit/b397ec61ddb43346ebf426ca8db509a01ada9602) took them out:

```diff
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -510,3 +510,3 @@ case class XSCoreParameters
           "VFEX2",
-          Seq(VialuCfg, VfmaCfg, VSha256msCfg),
+          Seq(VialuCfg),
           Seq(VfWB(port = 5, 0)),
@@ -521,3 +521,3 @@ case class XSCoreParameters
           "VFEX3",
-          Seq(VialuCfg, VfmaCfg, VSha256cCfg),
+          Seq(VialuCfg),
           Seq(VfWB(port = 6, 0)),
```

Its message keeps `VialuCfg` and `VmoveCfg` and lists seven configurations still to support; neither SHA-256 configuration is among them.

<details>
<summary>The message of <code>b397ec61</code></summary>

Its message says:

```
* Only keep VialuCfg and VmoveCfg in Parameters.scala
  * Todo: support VfaluCfg, VfmaCfg, VimacCfg, VppuCfg, VfcvtCfg, VfdivCfg, VidivCfg
```

Four of the configurations that Todo names, `VfcvtCfg`, `VfdivCfg`, `VidivCfg` and `VimacCfg`, are in the vector scheduler's units at this commit. The two SHA-256 configurations, which it does not name, are not.

</details>

Putting the two configurations back in a unit list would not be enough at this commit, because the two SHA-256 units were not ported to the new function-unit framework; the collapsed block below shows where. This is a reading of the source; we did not elaborate a build with the configurations put back.

<details>
<summary>Why putting the two configurations back is not enough</summary>

The vector region builds each unit's function units through the new framework: `ExuParam` maps each configuration in a unit list to its `VecFuConfig`, and `Exu` calls that configuration's `fuGen2`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/ExuParam.scala#L322
      fuConfigs = exeUnitParams.fuConfigs.map(x => VecFuConfig.allConfigs.find(_.fuConfig == x).get),
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Exu.scala#L41
  val fus: Seq[Func] = param.fuConfigs.map(cfg => cfg.fuGen2(p, cfg))
```

The working configurations pass a generator to `fromFuConfig`; the two SHA-256 ones do not, so theirs is the default, `null`. [`b397ec61`](https://github.com/OpenXiangShan/XiangShan/commit/b397ec61ddb43346ebf426ca8db509a01ada9602) added them that way:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/fu/VecFuConfig.scala#L231
  def fromFuConfig(cfg: FuConfig, fuGen2: (Parameters, VecFuConfig) => Func = null, _latency: Option[Int] = None): VecFuConfig = {
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/fu/VecFuConfig.scala#L297-L305
  val VialuCfg = VecFuConfig.fromFuConfig(FuConfig.VialuCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VIAluWrapper(cfg)(p).suggestName("Vialu")))
  val VimacCfg = VecFuConfig.fromFuConfig(FuConfig.VimacCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VIMacU(cfg)(p).suggestName("Vimac")))
  val VidivCfg = VecFuConfig.fromFuConfig(FuConfig.VidivCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VIDiv(cfg)(p).suggestName("Vidiv")))
  val VmoveCfg = VecFuConfig.fromFuConfig(FuConfig.VmoveCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VMove(cfg)(p).suggestName("Vmove")))
  val VfmacCfg = VecFuConfig.fromFuConfig(FuConfig.VfmacCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VFMacWrapper(cfg)(p).suggestName("Vfmac")))
  val VfdivCfg = VecFuConfig.fromFuConfig(FuConfig.VfdivCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VFDivWrapper(cfg)(p).suggestName("Vfdiv")))
  val VfcvtCfg = VecFuConfig.fromFuConfig(FuConfig.VfcvtCfg, (p: Parameters, cfg: VecFuConfig) => Module(new VCVTWrapper(cfg)(p).suggestName("Vfcvt")))
  val VSha256msCfg = VecFuConfig.fromFuConfig(FuConfig.VSha256msCfg)
  val VSha256cCfg = VecFuConfig.fromFuConfig(FuConfig.VSha256cCfg)
```

Their wrappers still take the old `FuConfig` and extend `VecPipedFuncUnit`, where `VIAluWrapper` takes a `VecFuConfig` and extends `VecFixLatFunc`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/wrapper/VSha256cWrapper.scala#L12
class VSha256cWrapper(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg) {
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/wrapper/VSha256msWrapper.scala#L12
class VSha256msWrapper(cfg: FuConfig)(implicit p: Parameters) extends VecPipedFuncUnit(cfg) {
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/wrapper/VIAluWrapper.scala#L14
class VIAluWrapper(cfg: VecFuConfig)(implicit p: Parameters) extends VecFixLatFunc(cfg) {
```

</details>

Our reading is also that no issue queue accepts these uops; we did not trace it in a waveform.

### Expected behavior

The declared list above includes `ssstrict`. The Ssstrict extension says:

> If the Ssstrict extension is implemented, then no non-conforming extensions are present.
>
> Furthermore, attempts to execute unimplemented opcodes or access unimplemented CSRs in the standard or reserved encoding spaces raises an illegal instruction exception that results in a contained trap to the supervisor-mode trap handler.
>
> — [ssstrict.adoc, lines 5-12](https://github.com/riscv/riscv-isa-manual/blob/1b9ca13819259df4b3bc87a7d7dcda06dd2fb6a9/src/priv/ssstrict.adoc#L5-L12)

With Zvknha not declared, `vsha2ch.vv`, `vsha2cl.vv` and `vsha2ms.vv` are unimplemented opcodes in the standard encoding space, so each should raise an illegal-instruction exception; `vghsh.vv` (Zvkg, also not declared) traps in the table above. If Zvknha is meant to be supported, the instructions should execute and the ISA string should name Zvknha. In neither case should the core stop committing. Of the two directions, only taking `Zvknha` out of the decoder's extension list stands apart from the vector rework. Supporting Zvknha means porting the two units to the new function-unit framework, putting them in an execution unit and declaring Zvknha. We did not build either change.

### Environment

- Hardware
  - Memory (GB): 16, with 2 cores, for the runs. The emulator was built in a separate job with 256 GB and 16 cores.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container the emulator ran in.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17.
- Repo
  - XiangShan commit id: [`6714a69d001dbb9e213ff077a518fb78e598c48a`](https://github.com/OpenXiangShan/XiangShan/commit/6714a69d001dbb9e213ff077a518fb78e598c48a). The emulator prints `Core  0's Commit SHA is: 6714a69d00, dirty: 0`. The `difftest` submodule is at [`99f77ff455766d18cd05927b144641bd9744b19a`](https://github.com/OpenXiangShan/difftest/commit/99f77ff455766d18cd05927b144641bd9744b19a).
  - NEMU commit id (if difftest failed with NEMU): the head's own `ready-to-run/riscv64-nemu-interpreter-so`, from `ready-to-run` at [`4cf9983569432035d41956c65e6a58724b5954be`](https://github.com/OpenXiangShan/ready-to-run/commit/4cf9983569432035d41956c65e6a58724b5954be).
- Build & Run
  - Build command: suggested `make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"`, not run by us as written. The emulator we ran was built by the build steps under "To Reproduce", with `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
  - Run command (if applicable): `./build/emu -i vsha2_minimal_vsha2ch.elf --no-diff --seed 1 --max-cycles 30000 --ram-size 256MB`, and with DiffTest `./build/emu -i vsha2_minimal_vsha2ch.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 30000 --ram-size 256MB`.

### To Reproduce

One bare-metal program of thirteen instructions and a three-instruction trap handler, run in M mode on a single hart. It prints nothing; the emulator's own trap and limit lines are the output.

1. Assemble and link the program and linker script below:

```sh
riscv64-unknown-elf-gcc -march=rv64imafdv_zicsr_zvknha_zvkg -mabi=lp64d -c -o vsha2_minimal_vsha2ch.o vsha2_minimal_vsha2ch.S
riscv64-unknown-elf-gcc -march=rv64imafdv_zicsr_zvknha_zvkg -mabi=lp64d -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T vsha2_minimal.ld -o vsha2_minimal_vsha2ch.elf vsha2_minimal_vsha2ch.o
```

The ELF we ran was built with `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0` and has sha256 `b507f2e452ad17298ea1b2b91a79cc85df77f353c32333a8f70157dedd189e9d`.

2. Build the emulator at the commit under "Environment", then run it without and with DiffTest. Suggested build command, with the GCC under "Environment"; we did not run it as written:

```sh
make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"
```

<details>
<summary>Build steps we ran</summary>

```sh
mill -i -Djvm-xmx=40G -Djvm-xss=256m xiangshan.test.runMain top.XiangShanSim --target-dir build/chirrtl --config DefaultConfig --issue E.b --num-cores 1 --target chirrtl --enable-difftest --full-stacktrace
firtool build/chirrtl/SimTop.fir -o build/rtl --split-verilog -O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none --default-layer-specialization=enable
# then, as the Makefile's RTL rule does for this configuration: a header from git on SimTop.sv,
# $fatal replaced by xs_assert_v2(`__FILE__, `__LINE__), and $error( by $fwrite(32'h80000002,
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" -j16
```

firtool was version 1.149.0. Those steps used this cluster's absolute paths, shortened here, and the `make` step also set `CXX`, `LINK` and `AR` to the GCC 15.2.0 toolchain named under "Environment", and `PGO_CFLAGS` and `PGO_LDFLAGS` to header and library directories for the native dependencies; those five are left out.

</details>

```sh
./build/emu -i vsha2_minimal_vsha2ch.elf --no-diff --seed 1 --max-cycles 30000 --ram-size 256MB
./build/emu -i vsha2_minimal_vsha2ch.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 30000 --ram-size 256MB
```

3. The run without DiffTest reaches its cycle limit with no trap line, and the run with DiffTest stops with the no-commit message and the commit trace in the collapsed emulator output under "Describe the bug".

<details>
<summary><code>vsha2_minimal_vsha2ch.S</code>, which assembles to the ELF above</summary>

```asm
# vsha2ch.vv v4, v2, v1, then vmv.x.s reads v4.
# Loaded at 0x80000000, where the emulator's flash jumps after its own six instructions. The word
# 0x0005006b is the emulator's trap instruction; the pc it ends at says which end was reached.
    .section .text
    .globl _start
_start:
    la      t0, on_trap
    csrw    mtvec, t0
    li      t0, 0x600               # mstatus.VS = 3: the vector unit is on
    csrs    mstatus, t0
    vsetivli zero, 4, e32, m1, ta, ma
    vmv.v.i v1, 1
    vmv.v.i v2, 2
    vmv.v.i v4, 3
subject:
    vsha2ch.vv v4, v2, v1
    vmv.x.s a1, v4                  # reads the result
    li      a0, 0
finish:
    .word   0x0005006b              # the end when no exception is taken
on_trap:
    csrr    a1, mcause              # any exception comes here: a1 = mcause
    li      a0, 1
    .word   0x0005006b              # the end when an exception is taken
```

</details>

<details>
<summary>The programs of the first table's other rows</summary>

The other rows of the first table use the same program with the instruction at `0x80000024` replaced by:

- `vadd.vv v4, v2, v1` (`0x02208257`), ELF sha256 `0ee17f031a5f4c37722ea7221f45899d066e090b8fb503c89f5dcf6bb0936703`
- `vghsh.vv v4, v2, v1` (`0xb220a277`), ELF sha256 `2510aed2bfbc0bd5b1f79ea03d89702f198fefda1aef412328be42d0208c6ce9`
- `vsha2cl.vv v4, v2, v1` (`0xbe20a277`), ELF sha256 `90e65e9e863e8ca93f8f99905e9e844df64f8709af03fdaceb2acc45d00b0a90`
- `vsha2ms.vv v4, v2, v1` (`0xb620a277`), ELF sha256 `280a0a06180e35fba096fbb9cd1a1f1f78278b0f7f4230676cbfdd1bd4bc161f`

</details>

<details>
<summary><code>vsha2_minimal.ld</code></summary>

```ld
OUTPUT_ARCH(riscv)
ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text : { *(.text*) }
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.riscv.attributes) *(.data*) *(.bss*) }
}
```

</details>

### Additional context

**Scope.** Each program ran once, on one build of `DefaultConfig`. The three vsha2 instructions ran at SEW 32 with `vl` = 4 and LMUL 1 in M mode; `vsha2ch.vv` also ran in U mode, with the machine timer armed in M and in U mode, and at SEW 64. S mode, other interrupt sources and other configurations were not run.

<details>
<summary>Configuration and build</summary>

`DefaultConfig`, with no local change. The emulator we ran was built by the steps in the collapsed block under "To Reproduce": CHIRRTL through mill, SystemVerilog through firtool called directly rather than from mill, and then `make emu`. `OPT_FAST` is set there and in the suggested command because difftest's default at this commit, `-O3 -fno-slp-vectorize`, is a clang option that GCC rejects; `-fno-tree-slp-vectorize` is GCC's name for the same pass. `SIM_VFLAGS` splits Verilator's output more finely than difftest's `--output-split 30000`, and is set in the environment because the difftest Makefile appends `+define+DIFFTEST` to it.

</details>

**AI assistance.** This report was prepared with Claude Opus 5.5 (Anthropic) in Claude Code.
