### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [ ] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

Per AIA spec 5.5 (WFI):

> According to the base RISC-V Privileged Architecture, instruction execution must resume from a WFI whenever any interrupt is both pending and enabled in CSRs mip and mie, ignoring any delegation indicated by mideleg. **With the AIA, this succinct rule is no longer appropriate, due to the mechanisms the AIA adds for virtual interrupts**. Instead, execution must resume from a WFI whenever an interrupt is pending at any privilege level (regardless of whether the interrupt privilege level is higher or lower than the hart’s current privilege mode).
> 
> An interrupt is pending at machine level if register mtopi is not zero. If S-mode is implemented, an interrupt is pending at supervisor level if stopi is not zero. And if the H extension is implemented, an interrupt is pending at VS level if vstopi (Section 6.3.3) is not zero.

In XiangShan, the WFI wake-up signal is defined at `NewCSR.scala:1158`:

```scala
io.status.wfiEvent := debugIntr || (mie.rdata.asUInt & mip.rdata.asUInt).orR || nmip.asUInt.orR
```

AIA introduces virtual-interrupt injection paths that make stopi or vstopi non-zero while leaving mip untouched. I suspect there are indeed some paths in XiangShan's own implementation：

such as: hvictl.VTI = 1 direct injection at VS level. When hvictl.VTI = 1 and hvictl.IID = k , Candidate5 in [InterruptFilter.scala] fires and drives vstopi.IID directly from hvictl.IID, bypassing every *ip register:

```scala
// InterruptFilter.scala:287
val Candidate5: Bool = (hvictl.VTI.asUInt === 1.U) && (hvictl.IID.asUInt =/= 9.U)

// InterruptFilter.scala:347
iidOnlyC5 := hvictlReg.IID.asUInt

// InterruptFilter.scala:432-445
io.out.vstopi.IID := Mux(CandidateNoValidReg,
  0.U,
  Mux1H(Seq(
    ...
    onlyC5EnableReg -> iidOnlyC5,   // hvictl.VTI=1 path hits here
    ...
  ))
)
```

In this path, (mip & mie) = 0 while vstopi ≠ 0. wfiEvent therefore does not fire, and a hart stalled in WFI is not woken up by the pending interrupt.

### Expected behavior

an interrupt is in fact pending (vstopi ≠ 0), and wfiEvent should assert so that the hart resumes execution from WFI, rather than remaining stalled until thewfi_cycles counter (Rob.scala:462) saturates at its 2^20-cycle upper bound.

### Environment

Branch: kunminghu-v3

### To Reproduce

I'm still debugging the POC and will provide it as soon as possible.

### Additional context

_No response_
