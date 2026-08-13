### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

`InterruptFilter` computes the interrupt-request vector combinationally, gated by the *current* interrupt-enable bits, and then pushes it through a multi-cycle delay pipeline (`RegInit` + `DelayN`) before handing it to the ROB. When software clears an enable bit (`mstatus.MIE` / `sstatus.SIE` / `vsstatus.SIE`) via a plain CSR write, the combinational gate stops *new* requests from entering, but the vector already latched in the delay pipeline is **not** flushed. As a result `io.out.interruptVec` keeps asserting a stale interrupt for several cycles after the enable bit is already 0, and the ROB can take that interrupt with the enable bit cleared.

This violates the RISC-V privileged spec: with `MIE=0` no M-mode interrupt may be taken (likewise `SIE=0` for S/HS, `VSIE=0` for VS). The enable-clear must be effective before the next instruction commits.

## Location

`src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

- L463–467 `mIRVecTmp` — M vector gated by `mstatusMIE`
- L469–473 `hsIRVecTmp` — HS vector gated by `sstatusSIE`
- L475–479 `vsIRVecTmp` — VS vector gated by `vsstatusSIE`
- L533 `val normalIntrVec = mIRVec | hsIRVec | vsMapHostIRVec`
- L534 `val intrVec = Mux(disableAllIntr, 0.U, Mux(io.in.nmi, nmiVec, normalIntrVec))`
- L544 `val intrVecReg = RegInit(0.U(8.W))`
- L550 `intrVecReg := intrVec`
- L556 `val delayedIntrVec = DelayN(intrVecReg, 5)` ← delay pipeline, no flush on enable-clear
- L563–564 `io.out.interruptVec.valid := delayedIntrVec.orR || delayedDebugIntr` / `io.out.interruptVec.bits := delayedIntrVec`

Consumed by the ROB in `src/main/scala/xiangshan/backend/rob/Rob.scala`:

- L576 `val intrBitSetReg = RegNext(io.csr.intrBitSet)`
- L577 `val intrEnable = intrBitSetReg && !hasWaitForward && deqPtrEntry.interrupt_safe && !deqHasFlushed`

## Details

The interrupt vector reaching the ROB is delayed by roughly 1 (`intrVecReg`, L550) + 5 (`DelayN`, L556) + 1 (ROB `RegNext`, L576) cycles relative to the enable bits. The delay is intentional (L542, for `sret`/`mret` atomicity), but it is a plain shift register with no kill/flush path for the enable-clear transition.

The asymmetry is the problem:

- On the **1→0** edge of the enable bit, the combinational `*IRVecTmp` (L463/469/475) drops to 0 immediately, but `intrVecReg` (L550) and the `DelayN(5)` chain (L556) still hold the value that was latched from `intrVec` several cycles earlier.
- So `io.out.interruptVec` (L563–564) continues to assert the already-latched interrupt for the length of the delay pipeline after the enable is cleared.
- The ROB's `intrEnable` (`Rob.scala` L577) trusts this delayed vector and does not re-check the live enable bit, so the interrupt can be taken while `MIE`/`SIE`/`VSIE` is already 0.

### Expected behavior

Re-evaluate the gate at the output of the delay pipeline (or add a kill path) so that a 1→0 transition on the enable bit clears the in-flight vector, rather than only blocking new requests at the input.

### Environment

detected by a static analysis tool.

### To Reproduce

  detected by a static analysis tool.

### Additional context

_No response_
