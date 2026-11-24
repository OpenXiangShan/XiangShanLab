### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

`zicfilp` ([PR #6801](https://github.com/OpenXiangShan/XiangShan/pull/6801), HEAD `e81fa337042e07330f36bb5158850d83c6cb2d32`) and `kunminghu-v3` (`Rob.scala:610-617, 768`)

### Describe the bug

According to the RISC-V Privileged Architecture Specification (Section 3.1.9 *"Machine Interrupt Registers (`mip` and `mie`)"* and Table 3.7 *"Exception Priority"*) and the `Zicfilp` Specification (Section 2.4 *"Landing Pad Faults and Interrupts"*):

1. Asynchronous interrupts that are pending and enabled at an instruction boundary **always take priority over any synchronous exception** (including `Zicfilp` Landing Pad `Software Check` fault, `mcause = 18, mtval = 2`) at that instruction.
2. When an asynchronous interrupt is taken while `ELP = LP_EXPECTED` (`1`), the processor saves `ELP = 1` into `mstatus.MPELP` (or `sstatus.SPELP`), traps to the interrupt handler with `mepc` pointing to the interrupted instruction, and upon `mret` restores `ELP = 1` so the target instruction subsequently raises `Software Check (mcause = 18)` (`trap_delta = 2`).

On XiangShan (`zicfilp` branch / [PR #6801](https://github.com/OpenXiangShan/XiangShan/pull/6801)), when `mret` restores `ELP = 1` and `mstatus.MIE = 1` with a pending enabled interrupt (`mip.SSIP = 1, mie.SSIE = 1`):
- If the target instruction at `mepc` is a normal ALU instruction (`addi`), an illegal instruction (`unimp`), or `lpad`, XiangShan correctly takes the asynchronous interrupt first (`mcause = 0x8000000000000001`) and then takes `Software Check (mcause = 18)` after returning from the interrupt (`trap_delta = 2`).
- **However, if the target instruction at `mepc` is a `waitForward` instruction (such as `sc.d`, `csrr`, `fence`, etc.), XiangShan suppresses the pending asynchronous interrupt and commits the lower-priority synchronous `Software Check` exception (`mcause = 18`) instead (`trap_delta = 1`), leaving `mip.SSIP = 1` and `mstatus.MIE = 1` unserviced!**

```asm
  # mseccfg.MLPE = 1, mstatus.MPELP = 1, mstatus.MPP = 3, mstatus.MPIE = 1
  # mip.SSIP = 1, mie.SSIE = 1, mepc = tgt_insn
  mret

tgt_insn:
  sc.d     x27, x29, 0(x30)             # Expected:
                                        #   1st trap: Async interrupt (mcause = 0x8000000000000001)
                                        #   2nd trap: Software Check (mcause = 18, mtval = 2)
                                        # Actual on XiangShan:
                                        #   Only 1 trap (mcause = 18)! Pending interrupt is dropped!
  csrr     x10, mstatus                 # reads mstatus.MIE = 1 (0x8)
  csrr     x28, mip                     # reads mip.SSIP = 1 (0x2) — still pending!
```

Execution comparison (`Sail` / `Spike` vs. `XiangShan zicfilp`):

```text
Sail / Spike reference:
  trap_count          = 2   <-- 1st trap: SSIP interrupt (0x8000000000000001); 2nd trap: Software Check (18)
  last_mcause         = 18  (Software Check)
  mtval               = 2   (Landing pad fault)
  mip     (x28)       = 0x0 (SSIP cleared by interrupt handler)
  mstatus (x10)       = 0x0000000a00000080 (MIE = 0, MPIE = 1)

XiangShan (zicfilp):
  trap_count          = 1   <-- Asynchronous interrupt was DROPPED!
  last_mcause         = 18  (Software Check committed ahead of pending interrupt)
  mtval               = 2
  mip     (x28)       = 0x2 <-- mip.SSIP is STILL pending!
  mstatus (x10)       = 0x0000000a00000088 <-- mstatus.MIE is STILL 1!
```

### Expected behavior

When a `waitForward` instruction (`sc.d`, `csrr`, `fence`, etc.) already carries an enqueue-time synchronous exception (`enqHasException === true.B`, such as `Zicfilp` `softwareCheckFault = 18` or `illegalInstr = 2`), it will never execute in any functional unit and must not set `hasWaitForward := true.B` to suppress `intrEnable` (`Rob.scala:768`). The pending asynchronous interrupt must be taken first (`trap_count = 2`).

### Environment

- Software
  - Operating system: Ubuntu 24.04.3 LTS
  - gcc version: `gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`
  - riscv toolchain: `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0`
  - java version: `openjdk version "21.0.2" 2024-01-16`
  - verilator version: `Verilator 5.047` / `Verilator 4.210`
- Repo
  - XiangShan commit id: `zicfilp` branch ([PR #6801](https://github.com/OpenXiangShan/XiangShan/pull/6801)), reproduced on `47b79d8` and verified unchanged in `Rob.scala` on latest `zicfilp` HEAD `e81fa337042e07330f36bb5158850d83c6cb2d32` and `kunminghu-v3`
  - Reference models: `sail_riscv_sim` (`0.16 @ cd3f185`) and `SPIKE` (`riscv-isa-sim --isa=rv64imafdcv_zicsr_zifencei_zicbom_zicboz_zicfilp_zicfiss`)
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig` (on `zicfilp` branch with `HasZicfilp = true`)
  - Run command: `./build/emu -i repro.bin --no-diff`

### To Reproduce

1. Build `emu` on the `zicfilp` branch ([PR #6801](https://github.com/OpenXiangShan/XiangShan/pull/6801)).
2. Save `repro.S` and `link.ld` below and compile `repro.bin`:
   ```bash
   riscv64-unknown-elf-gcc -march=rv64imafdcv_zicntr_zicsr_zifencei -mabi=lp64d \
       -nostdlib -nostartfiles -T link.ld repro.S -o repro.elf
   riscv64-unknown-elf-objcopy -O binary repro.elf repro.bin
   ```
3. Run `repro.bin` on `emu` (or run `bash reproduce.sh`):
   ```bash
   ./build/emu -i repro.bin --no-diff
   ```
4. Observe that `emu` stops at **`pc = 0x80000300`** (`bug_triggered`) instead of `pc = 0x80000200` (`spec_passed`): `sig_out[0]` (`trap_count`) is `1` instead of `2`, and `sig_out[2]` (`mip`) is `0x2` (`mip.SSIP` remains pending while `mstatus.MIE = 1`).

<details>
<summary>Full standalone workload (<code>repro.S</code> and <code>link.ld</code>)</summary>

**`repro.S`**:
```asm
.section .text.init
.option norvc
.globl _start
_start:
    # 0. Cold-boot initialization for standalone XiangShan ./build/emu:
    csrw mstatus, x0
    csrw mie, x0
    li t0, 0x3fffffff
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0

    # 1. Enable Zicfilp Landing Pad in M-mode (mseccfg.MLPE = bit 10 of CSR 0x747)
    li t0, 0x400
    csrs 0x747, t0            # mseccfg.MLPE = 1

    # 2. Set M-mode trap handler
    la t0, trap_handler
    csrw mtvec, t0
    li s0, 0                  # s0: total trap counter
    li s1, 0                  # s1: 1st trap mcause
    li s2, 0                  # s2: last trap mcause

    # 3. Prepare MRET with:
    #    - mstatus.MPELP = 1 (bit 41 -> sets ELP = LP_EXPECTED on mret)
    #    - mstatus.MPP   = 3 (M-mode)
    #    - mstatus.MPIE  = 1 (bit 7  -> enables mstatus.MIE = 1 on mret)
    #    - mip.SSIP      = 1 (bit 1) and mie.SSIE = 1 (bit 1) -> pending enabled interrupt!
    li t0, 1
    slli t0, t0, 41
    csrs mstatus, t0          # mstatus.MPELP = 1
    li t0, (3 << 11) | 0x80
    csrs mstatus, t0          # mstatus.MPP = 3, mstatus.MPIE = 1

    li t0, 0x2
    csrs mip, t0              # mip.SSIP = 1
    csrs mie, t0              # mie.SSIE = 1

    la x30, scratch_line
    la t0, fault_insn
    csrw mepc, t0
    mret

.align 4
.global fault_insn
fault_insn:
    # At fault_insn:
    #   - Asynchronous interrupt (SSIP) is pending and enabled (mip.SSIP=1, mie.SSIE=1, mstatus.MIE=1)
    #   - Zicfilp ELP = 1 (LP_EXPECTED), and sc.d is NOT an lpad instruction (EX_SWC = 18)
    # - Expected (RISC-V Priv Spec §3.1.9, Sail & Spike):
    #   Trap 1: Asynchronous interrupt (mcause = 0x8000000000000001) takes priority over synchronous exception;
    #   Trap 2: Upon returning to fault_insn with ELP = 1, sc.d raises Software Check (mcause = 18).
    #   Total traps (s0) = 2.
    # - Actual on XiangShan (kunminghu-v3 & zicfilp PR #6801):
    #   Rob.scala:610 sets hasWaitForward := true.B on sc.d without checking !enqHasException,
    #   forcing intrEnable = false.B at Rob.scala:768. The pending asynchronous interrupt is dropped (s0 = 1)!
    sc.d x27, x29, 0(x30)

    csrr s3, mstatus
    csrr x28, mip
    andi x28, x28, 0x2a

    csrw mie, x0
    li t0, 0x22
    csrc mip, t0
    j finish

.align 4
trap_handler:
    addi s0, s0, 1
    csrr t1, mcause
    mv s2, t1
    li t2, 1
    bne s0, t2, 1f
    mv s1, t1                 # record 1st trap mcause in s1
1:
    bltz t1, async_irq_ret    # if mcause < 0 (asynchronous interrupt), handle IRQ

    # Synchronous exception (mcause = 18): clear mstatus.MPELP (bit 41) and advance mepc + 4
    li t0, 1
    slli t0, t0, 41
    csrc mstatus, t0
    csrr t0, mepc
    addi t0, t0, 4
    csrw mepc, t0
    mret

async_irq_ret:
    # Acknowledge asynchronous interrupt, preserve MPELP and mepc
    csrw mie, x0
    li t0, 0x22
    csrc mip, t0
    li t0, 0x88
    csrc mstatus, t0
    mret

finish:
    la t0, sig_out
    sd s0,  0(t0)             # sig_out[0]: total trap_count (Expected: 2; Actual on XiangShan: 1)
    sd s1,  8(t0)             # sig_out[1]: 1st mcause (Expected: 0x8000000000000001; Actual: 18)
    sd x28, 16(t0)            # sig_out[2]: mip & 0x2a after fault_insn (Expected: 0; Actual: 2)
    sd s3,  24(t0)            # sig_out[3]: mstatus after fault_insn

    la t0, 1f
    csrw mtvec, t0
    la t1, tohost
    li t2, 1
    sd t2, 0(t1)

    li t3, 2
    bne s0, t3, bug_triggered

.balign 256
.globl spec_passed
spec_passed:
    .word 0x0000006b          # PC = 0x80000200: Spec-compliant (trap_count == 2)
1:  j 1b

.balign 256
.globl bug_triggered
bug_triggered:
    .word 0x0000006b          # PC = 0x80000300: BUG TRIGGERED (pending IRQ dropped, trap_count == 1!)
    j 1b

.section .tohost,"aw",@progbits
.align 6
.globl tohost
tohost: .dword 0
.globl fromhost
fromhost: .dword 0

.section .data
.align 6
.globl begin_signature
begin_signature:
.globl sig_out
sig_out:
    .dword 0, 0, 0, 0
.globl end_signature
end_signature:
scratch_line:
    .dword 0, 0
```

**`link.ld`**:
```ld
OUTPUT_ARCH( "riscv" )
ENTRY( _start )
SECTIONS
{
  . = 0x80000000;
  .text : { *(.text.init) *(.text) }
  . = ALIGN(0x1000);
  .tohost : { *(.tohost) }
  . = ALIGN(0x1000);
  .data : { *(.data) }
  .bss : { *(.bss) }
  _end = .;
}
```
</details>

### Possible cause

In [`src/main/scala/xiangshan/backend/rob/Rob.scala`](https://github.com/OpenXiangShan/XiangShan/blob/e81fa337042e07330f36bb5158850d83c6cb2d32/src/main/scala/xiangshan/backend/rob/Rob.scala#L610-L617) (lines 610–617 and line 768):

```scala
      when(enqUop.waitForward) {
        hasWaitForward := true.B
      }
      val enqTriggerActionIsDebugMode = TriggerAction.isDmode(io.enq.req(i).bits.trigger)
      val enqHasException = enqUop.exceptionVec.orR
      when(enqUop.isWFI && !enqHasException && !enqTriggerActionIsDebugMode) {
        hasWFI := true.B
      }
```

and at line 768:

```scala
  val intrBitSetReg = RegNext(io.csr.intrBitSet)
  val intrEnable = intrBitSetReg && !hasWaitForward && deqPtrEntry.interruptSafe && !deqHasFlushed
```

1. **Compare line 610 (`hasWaitForward`) with line 615 (`hasWFI`)**:
   - On line 615, `hasWFI` is guarded by `&& !enqHasException && !enqTriggerActionIsDebugMode` because an instruction that already faulted in Fetch/Decode (`enqHasException = true.B`, such as `Zicfilp` `softwareCheckFault = 18` when `ELP = 1` on a non-`lpad` instruction) will never be issued to an execution unit.
   - On line 610 (four lines above), `when(enqUop.waitForward)` unconditionally sets `hasWaitForward := true.B` **without** checking `!enqHasException && !enqTriggerActionIsDebugMode`!
2. **Why the pending asynchronous interrupt is dropped**:
   - When `sc.d` (which has `waitForward = true.B`) is decoded at `tgt_insn` with `ELP = 1`, Decode flags `enqUop.exceptionVec(softwareCheckFault) = true.B` (`enqHasException = true.B`).
   - Enqueueing `sc.d` into the ROB sets `hasWaitForward := true.B` (`Rob.scala:610-611`), and `hasWaitForward` is only cleared after an instruction commits or walks (`Rob.scala:521-523`).
   - When `sc.d` reaches `deqPtr`, `!hasWaitForward` is `false.B`, forcing `intrEnable = false.B` (`Rob.scala:768` and `RobDeqPtrWrapper.scala:65`).
   - Consequently, the ROB refuses to take the pending asynchronous interrupt at `sc.d` and instead commits `sc.d`'s lower-priority synchronous `Software Check` exception (`mcause = 18`). Then, after the handler returns via `mret` to `csrr x10, mstatus; csrr x28, mip; csrw mie, x0`, each of those CSR instructions also has `waitForward = true.B`, keeping `hasWaitForward = true.B` until `csrw mie, x0` disables interrupts!
3. **Suggested fix**:
   Gate `hasWaitForward := true.B` (and `hasBlockBackward := true.B` on line 623) with `!enqHasException && !enqTriggerActionIsDebugMode`, matching `hasWFI` on line 615:
   ```scala
      val enqTriggerActionIsDebugMode = TriggerAction.isDmode(io.enq.req(i).bits.trigger)
      val enqHasException = enqUop.exceptionVec.orR
      when(enqUop.waitForward && !enqHasException && !enqTriggerActionIsDebugMode) {
        hasWaitForward := true.B
      }
   ```


### Mainline (`kunminghu-v3`) & `zicfilp` ([PR #6801](https://github.com/OpenXiangShan/XiangShan/pull/6801)) Impact

- In `kunminghu-v3` (`893e158a596c`), [`Rob.scala:610-617`](https://github.com/OpenXiangShan/XiangShan/blob/893e158a596c003f23143cf9c367c6a12416f177/src/main/scala/xiangshan/backend/rob/Rob.scala#L610-L617) has the exact same `when(enqUop.waitForward) { hasWaitForward := true.B }` without `!enqHasException && !enqTriggerActionIsDebugMode` (whereas line 615 `hasWFI` checks `when(enqUop.isWFI && !enqHasException && !enqTriggerActionIsDebugMode)`), and `DecodeStage.scala:160` assigns `bits.waitForward := uopInfo.noSpec` unconditionally even when `bits.exceptionVec.orR === true.B`.
- In `zicfilp` ([PR #6801](https://github.com/OpenXiangShan/XiangShan/pull/6801), `e81fa337`), `DecodeStage.scala:197` sets `bits.exceptionVec(softwareCheck) := info.exception && uopInfo.isFirstUop` while preserving `bits.waitForward := uopInfo.noSpec`, directly triggering this bug whenever `ELP = 1` meets a `waitForward` instruction (`sc.d`, `csrr*`, `fence`, etc.) with a pending asynchronous interrupt.
