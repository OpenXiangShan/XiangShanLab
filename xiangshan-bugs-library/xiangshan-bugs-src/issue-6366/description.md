### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest). 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。

### Describe the question

Hello XiangShan maintainers,

Could you please help confirm whether the code path below represents an unintended behavior? This report comes from a static source review, and I may have missed a project-specific invariant, configuration constraint, or design assumption.

 



## Affected code locations

- `XIANGSHAN_IMSIC_ADDRESS_NOT_SUBTRACTED_FROM_PERIPHERAL_RANGE`: `src/main/scala/system/SoC.scala:94-108, src/main/scala/system/SoC.scala:116-123, src/main/scala/system/SoC.scala:205-224, src/main/scala/system/SoC.scala:377-400, src/main/scala/system/SoC.scala:405-431, src/main/scala/top/XSNoCTop.scala:470-485, src/main/scala/top/XSNoCTop.scala:377-407, src/main/scala/device/imsic_axi_top.scala:27-70, src/main/scala/xiangshan/backend/fu/PMA.scala:135-140, src/main/scala/top/ArgParser.scala:154-157, src/main/scala/top/YamlParser.scala:151-159`

## Static analysis for maintainer review

## Flags

```yaml
owner_review_only: true
email_sent: false
online_pr_created: false
dynamic_validation_performed: false
community_confirmed: false
dco_signoff_present: false
```

## Candidate

- Candidate id: `XIANGSHAN_IMSIC_ADDRESS_NOT_SUBTRACTED_FROM_PERIPHERAL_RANGE`
- Source revision audited: `8a42e727cc58db2b00d4bb2de7b397355f89257c`
- Affected area: XiangShan SoC address ownership for RISC-V AIA IMSIC AXI windows and the generic peripheral-range generator.

### Source evidence

Default SoC parameters enable IMSIC as a RISC-V AXI-visible interrupt controller:

- `src/main/scala/system/SoC.scala:98-108`: `IMSICBusType` defaults to `device.IMSICBusType.AXI`; `IMSICParams` defaults to `mAddr = 0x3A800000` and `sgAddr = 0x3B000000`.
- `src/main/scala/device/imsic_axi_top.scala:27-29`: `IMSICBusType` supports `NONE`, `TL`, and `AXI`.
- `src/main/scala/device/imsic_axi_top.scala:55-70`: when `IMSICBusType == AXI`, `imsic_bus_top` instantiates the AXI IMSIC wrapper and exposes an AXI master-side node.

The same address neighborhood is treated as IMSIC in the RISC-V PMA memory map:

- `src/main/scala/xiangshan/backend/fu/PMA.scala:135-140`: the commented memory map labels `0x3A800000-0x3AFFFFFF` as `IMSIC(M)` and `0x3B000000-0x3BFFFFFF` as `IMSIC(S/VS)`.

The catch-all peripheral-range computation does not remove IMSIC:

- `src/main/scala/system/SoC.scala:194-224`: `HasPeripheralRanges` starts from `AddressSet(0x0, 0x7fffffffL)` and subtracts `TIMER`, `SYSCNT`, `BEU`, `PLIC`, `PLL`, `UARTLITE`, `UART16550`, `DEBUG`, `MMPMA`, and optional `MEMENC`. It does not subtract `IMSICParams.mAddr`, `IMSICParams.sgAddr`, or any IMSIC-derived window.
- `src/main/scala/system/SoC.scala:377-386`: `HaveAXI4PeripheralPort` publishes the remaining `peripheralRange` through a generic `AXI4SlaveNode`.

The default top-level also exposes a separate IMSIC path:

- `src/main/scala/top/XSNoCTop.scala:377-380`: `HasIMSIC` creates an `imsic_bus_top`.
- `src/main/scala/top/XSNoCTop.scala:382-407`: `HasIMSICImp` exposes IMSIC AXI/TL/bare IOs and connects IMSIC MSI information into the core.
- `src/main/scala/top/XSNoCTop.scala:470-485`: `XSNoCTop` mixes in `HasIMSIC` and `HasIMSICImp`.

Configuration frontends expose the relevant knobs:

- `src/main/scala/top/ArgParser.scala:154-157`: command-line configuration can update `IMSICBusType`.
- `src/main/scala/top/YamlParser.scala:151-159`: YAML configuration can update `IMSICBusType` and `IMSICParams`.

## Static Interpretation

The source-level ownership model currently has two independent statements about the same IMSIC address neighborhood:

1. The IMSIC-specific path says these addresses are RISC-V IMSIC windows.
2. The generic catch-all peripheral range still includes them because IMSIC is not subtracted from the low-2GB peripheral universe.

This is not merely a cosmetic comment mismatch. The generic `peripheralNode` is built from the computed `peripheralRange`, while the top-level IMSIC path is also instantiated for the default AXI IMSIC configuration. That creates an address-ownership ambiguity in the generated SoC metadata/topology unless another owner-level invariant proves the generic peripheral path is unreachable for the IMSIC windows.

 
 

## Owner Review Questions

- What are the authoritative IMSIC M and S/VS address windows for the current XiangShan AXI IMSIC configuration?
- Should `HasPeripheralRanges.onChipPeripheralRanges` include IMSIC when `IMSICBusType == AXI`?
- Is the generic `peripheralNode` intended to own all unclaimed low-2GB ranges, or only ranges that are not claimed by specialized devices?
- Should `IMSICParams` expose address-set helpers so PMA, top-level routing, DTS, and catch-all range subtraction share one source of truth?
- Is QEMU's XiangShan Kunminghu IMSIC map intended to mirror the current RTL/source configuration, or only one FPGA platform instance?
