### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

 
## Affected code locations

- `XIANGSHAN_IOPMP_ERRCFG_MSI_MODE_WITHOUT_MSI_PATH`: `ChiselIOPMP/src/main/scala/IopmpChecker.scala:620-643, ChiselIOPMP/src/main/scala/IopmpChecker.scala:716-735, ChiselIOPMP/src/main/scala/IopmpChecker.scala:822-831, ChiselIOPMP/src/main/scala/IopmpChecker.scala:871-879, ChiselIOPMP/src/main/scala/IopmpChecker.scala:1307-1317, ChiselIOPMP/src/main/scala/Iopmp.scala:91-128`
- `XIANGSHAN_IOPMP_ERRINFO_STALE_ENTRY_ATTRIBUTE_INT`: `ChiselIOPMP/src/main/scala/IopmpChecker.scala`
- `XIANGSHAN_IOPMP_MDCFG_ENTRY_INDEX_WRAP_ALIAS`: `ChiselIOPMP/src/main/scala/IopmpChecker.scala`
- `XIANGSHAN_IOPMP_NAPOT_ALL_ONES_DECODER`: `ChiselIOPMP/src/main/scala/IopmpChecker.scala`

## Static analysis for maintainer review

### Potential issue observed

ChiselIOPMP exposes programmable RISC-V IOPMP state through APB registers. Several fields currently can outlive or exceed the hardware semantics they are supposed to describe:

- MDCFG stores 16-bit entry boundaries, but the implemented ENTRY table has 512 entries and a 9-bit SRAM address.
- The NAPOT decoder treats an all-ones encoded address like the ordinary PriorityEncoder zero case.
- ERRINFO is sticky, but interrupt suppression was evaluated from the live entry attribute of a later controller state.
- ERR_CFG accepts `msi_en/msidata`, while the implementation only exposes a wired interrupt and has no MSI write path.

### Proposed change for discussion

- Clamp MDCFG boundary writes to the implemented ENTRY table capacity.
- Preserve all-ones NAPOT as the full implemented address range.
- Capture `sire/siwe` with the first sticky ERRINFO fault and use those captured bits for interrupt generation.
- Clamp unsupported MSI programming fields to zero.

This patch is intentionally conservative. It does not add a new MSI delivery path and does not change non-IOPMP logic.


## Patch status

The package contains held patch for discussion after Issue feedback
