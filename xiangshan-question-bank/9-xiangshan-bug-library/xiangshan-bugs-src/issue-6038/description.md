### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In `TrapHandleModule.scala`, the trap-target PC computation does not account for the case where a pending interrupt is upgraded into a double-trap exception and redirected to M-mode. As a result, `mcause` correctly reports a double-trap exception, but the trap target PC is computed using the original interrupt's vectored offset, producing a mismatch between the reported cause and the PC actually taken.

`xiangshan/backend/fu/NewCSR/TrapHandleModule.scala` lines 96-101
```scala
private val adjustinterruptNO = Mux(
  InterruptNO.getVS.map(_.U === interruptNO).reduce(_ || _) && vsHasIR,
  interruptNO - 1.U, // map VSSIP, VSTIP, VSEIP to SSIP, STIP, SEIP
  interruptNO,
)
private val pcFromXtvec = Cat(
  xtvec.addr.asUInt + Mux(xtvec.mode === XtvecMode.Vectored && hasIR,
                          adjustinterruptNO(5, 0), 0.U),
  0.U(2.W)
)
```

The two relevant gate signals are:
1) `adjustinterruptNO` is gated on `vsHasIR` (whether the original trap source is a VS-level interrupt).
2) The vectored-offset selection in `pcFromXtvec` is gated on `hasIR` (whether the original trap is an interrupt).

Neither condition references `hasDTExcp`, `traptoVS`, or `trapToHS`. So when a double-trap redirect promotes the trap into an M-mode exception, both gates remain true and the original "interrupt + vectored offset" arithmetic is still applied.

### Trigger conditions
A representative VS→M case:
1) CPU is in VS-mode, `privState.isVirtual = 1`
2) A VS-level interrupt is pending
3) After interrupt filtering, `irToVS = 1`
4) `vsstatus.SDT = 1` (VS-mode is already handling a trap → double-trap fires)
5) `mtvec.mode = Vectored`
6) `mnstatus.NMIE = 1`

### Example (VS→M, VSTI=6)
| Signal                  | Line    | Value                                            |
| ----------------------- | ------- | ------------------------------------------------ |
| `vsHasIR`               | 60      | `1`                                              |
| `vs_EX_DT`              | 84      | `1`                                              |
| `hasDTExcp`             | 87      | `1`                                              |
| `trapToHS`              | 89      | `0`                                              |
| `traptoVS`              | 90      | `0` (because `vs_EX_DT = 1`)                     |
| `xtvec`                 | 92–95   | `mtvec` (default branch, both selectors false)   |
| `adjustinterruptNO`     | 96–100  | `interruptNO - 1 = 5`                            |
| `hasIR` (gating offset) | 101     | `1`                                              |
| `pcFromXtvec`           | 101     | `mtvec + 5*4 = mtvec + 20`                       |
| `entryPrivState`        | 103–106 | `ModeM`                                          |



### Expected behavior

When a pending interrupt is converted into a double-trap exception and redirected to M-mode:

- `mcause.Interrupt = 0`
- `mcause.ExceptionCode = 16` (EX_DT)
- Trap target PC = **`mtvec.BASE`** (i.e. `mtvec + 0`)

Per the RISC-V Privileged Spec, vectored mode applies only to interrupts. Once the trap is encoded as an exception, the BASE address must be used regardless of the original interrupt source.

The PC computation should be consistent with the cause encoding produced by `TrapEntryMEvent`.


### Environment

Detected by a static analysis tool.

### To Reproduce

Detected by a static analysis tool.

### Additional context

_No response_
