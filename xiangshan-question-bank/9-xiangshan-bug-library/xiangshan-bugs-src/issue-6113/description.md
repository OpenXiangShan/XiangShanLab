### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

When VS-mode accesses sireg (aliased to vsireg) with vsiselect in the range 0x30-0x3F and mstateen0.AIA=0, XiangShan raises an illegal instruction exception (EX_II, cause=2). The spike raises a virtual instruction exception (EX_VI, cause=22) in this scenario. Based on the specification analysis, I think Spike's behavior is expected to be correct.

#### spec
<img width="858" height="84" alt="Image" src="https://github.com/user-attachments/assets/86a1abc3-251a-4466-b78f-d8aebf95f8ef" />

<img width="872" height="276" alt="Image" src="https://github.com/user-attachments/assets/4353d538-473f-417f-aeee-177eac247b09" />

#### code
src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala:
```
private val rwSireg_EX_II = (
    !privState.isVirtual && (
      Iselect.isInAIA(siselect) && Iselect.isOdd(siselect) ||
      Iselect.isInOthers(siselect)
    ) ||
    privState.isModeHS && (
      mvienSEIE && Iselect.isInImsic(siselect) ||
      !mstateen0.AIA.asBool && Iselect.isInAIA(siselect) ||    
      !mstateen0.IMSIC.asBool && Iselect.isInImsic(siselect) 
    ) ||
    privState.isVirtual && (
      Iselect.isInOthers(vsiselect) ||
      !mstateen0.AIA.asBool && Iselect.isInAIA(vsiselect) ||    // BUG: VS-mode should not raise EX_II
      !mstateen0.IMSIC.asBool && Iselect.isInImsic(vsiselect)   // BUG: VS-mode should not raise EX_II
    )
  ) && addr === CSRs.sireg.U
```
```
private val rwSireg_EX_VI = privState.isVirtual && (
  Iselect.isInAIA(vsiselect) ||
  Iselect.isInImsic(vsiselect) && !hstateen0.IMSIC.asBool
) && addr === CSRs.sireg.U
```

#### Trigger Conditions

- Privilege mode: VS-mode
- vsiselect ∈ [0x30, 0x3F]
- mstateen0.CSRIND = 1
- mstateen0.AIA = 0
- Access to CSR sireg (0x151)

When both rwSireg_EX_II and rwSireg_EX_VI evaluate to true simultaneously (which happens in this scenario — the EX_VI path correctly matches Iselect.isInAIA(vsiselect)), EX_II takes absolute priority and EX_VI is suppressed. The hart reports mcause=2 (illegal instruction).




### Expected behavior

Xiangshan Result: mcause=2 (illegal instruction). Expected: EX_VI (mcause=22) aligned with spike and spec.

### Environment

  - XiangShan commit id: `96f3a4d4b0`
  - SPIKE commit id (if difftest failed with SPIKE): `master`


### To Reproduce

Spike (reference model): raises trap_virtual_instruction at the csrr t1, sireg instruction (spike log line 4556: exception trap_virtual_instruction, epc 0x00000000800001a8), mcause=0x16=22 (line 4560: x7 0x0000000000000016).
XiangShan: reports trap_mcause=2 (EX_II) at the same PC 0x800001a8.

[poc.zip](https://github.com/user-attachments/files/29092931/poc.zip)

### Additional context

The line `!mstateen0.IMSIC.asBool && Iselect.isInImsic(vsiselect)` has the identical issue for `vsiselect ∈ [0x70, 0xFF]`.
