# 香山 Bug 按架构与微架构分类

> 数据来源：`OpenXiangShan/XiangShan` 当前开放 Bug Issue  
> 数据时间：2026 年 7 月 28 日（北京时间）  
> 分类对象：92 个带有 `type: bug/reported` 或 `type: bug/confirmed` 的开放 Issue

## 一、分类口径

本报告采用“主责层级”分类，每个 Bug 只计入一个主分类：

- **架构 Bug**：外部可观察的 ISA、特权架构、CSR、异常、内存保护、虚拟内存、调试语义或参考模型行为不符合规范/预期。
- **微架构 Bug**：流水线控制、缓存、Load/Store、预测器、队列、SRAM、redirect/kill、replay、时序、死锁或内部断言等实现行为错误。
- **交叉问题**：某些微架构故障最终表现为架构错误，例如异常后的错误写回、向量 fault 恢复和错误路径访问；报告按最直接的故障机制归入主责层级。

## 二、总体结果

| 主分类 | 数量 | 占明确 Bug | 核心特征 |
| --- | ---: | ---: | --- |
| 架构 Bug | 52 | 56.5% | 指令结果、异常、CSR、PMP/PMA、MMU、调试和 difftest 差异 |
| 微架构 Bug | 40 | 43.5% | 死锁、缓存/访存控制、预测器、流水线 kill/replay 和内部状态机 |
| 合计 | 92 | 100% | 每个 Issue 按主责层级单计 |

结论是：**当前开放 Bug 以架构可见语义问题略多，但微架构稳定性问题占比仍然很高**。尤其是死锁、错误路径写回和预测器 disable 后残留访问，不能仅按普通功能错误处理。

## 三、架构 Bug：52 个

### 3.1 特权架构、CSR、异常和调试

主要问题是 trap 类型、`mcause`、`mepc/mtval`、中断使能、PMP/PMA final check、页故障和 debug trigger 行为不符合架构要求。

代表案例：

- [#6288](https://github.com/OpenXiangShan/XiangShan/issues/6288)：`sw` 在 M-mode 报告错误的 `mcause`。
- [#6259](https://github.com/OpenXiangShan/XiangShan/issues/6259)：`HLV.WU` 忽略 `SPVP=VU` 的有效特权级进行最终 PMP 检查。
- [#6264](https://github.com/OpenXiangShan/XiangShan/issues/6264)：跨 Sv39 canonical boundary 的顺序取指未产生 instruction page fault。
- [#6227](https://github.com/OpenXiangShan/XiangShan/issues/6227)：Bare 模式 trap 地址 CSR 被截断/零扩展。
- [#6182](https://github.com/OpenXiangShan/XiangShan/issues/6182)：清除中断使能后仍取得过期中断。
- [#6057](https://github.com/OpenXiangShan/XiangShan/issues/6057)：非法指令的 `mtval` 使用了更年轻指令的编码。

### 3.2 RVV 指令和向量状态语义

主要问题是向量结果、`vstart`、`vl`、异常部分写回、reserved encoding、NaN-boxing、`frm` 检查和 fault-only-first 语义错误。

代表案例：

- [#6152](https://github.com/OpenXiangShan/XiangShan/issues/6152)：`vzext.vf4/vsext.vf4` 的高 64 位写入为零。
- [#6042](https://github.com/OpenXiangShan/XiangShan/issues/6042)：`vle16.v` fault handling 将 `vstart` 错误设置为 `vl`。
- [#5919](https://github.com/OpenXiangShan/XiangShan/issues/5919)：reserved 的 `vmv<nr>r.v` 编码被执行而不是触发非法指令异常。
- [#5766](https://github.com/OpenXiangShan/XiangShan/issues/5766)：fault-only-first 后立即读取 `vl` 得到错误结果。
- [#5830](https://github.com/OpenXiangShan/XiangShan/issues/5830)：`vfmv.f.s` 未正确执行 32-bit 浮点值的 NaN-boxing。

### 3.3 参考模型和架构一致性

这类问题通常通过 NEMU/Spike 对照暴露，最终仍应定位到 RTL 的架构状态或指令语义，而不是简单归因于 difftest。

- [#5807](https://github.com/OpenXiangShan/XiangShan/issues/5807)：CSR `mcause` exception code 与 NEMU 不一致。
- [#5765](https://github.com/OpenXiangShan/XiangShan/issues/5765)：`vlm.v` 后 `v0` 尾部 bit 出现 difftest mismatch。

## 四、微架构 Bug：40 个

### 4.1 Load/Store、缓存和内存控制

主要问题是 StoreUnit/LoadUnit 死锁、DCache 断言、store-to-load forwarding、MMIO 元数据、MSHR 响应处理和跨边界访存控制。

代表案例：

- [#6289](https://github.com/OpenXiangShan/XiangShan/issues/6289)：store access fault 后 AMO deadlock。
- [#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267)：PMA 缺失地址上的 `fsw` 造成 StoreUnit deadlock。
- [#6268](https://github.com/OpenXiangShan/XiangShan/issues/6268)：L1 DCache 连续 SC 失败断言。
- [#6229](https://github.com/OpenXiangShan/XiangShan/issues/6229)：misaligned load 跨 16B 边界时遗漏低半部 forwarding。
- [#6130](https://github.com/OpenXiangShan/XiangShan/issues/6130)：MSHR 在 RXDAT/RXRSP 同周期响应时丢失错误标志。

### 4.2 流水线、redirect/kill、replay 和退休

这类问题通常不改变正常路径上的 ISA 定义，但在异常、redirect、flush 或资源冲突下暴露内部控制状态机缺陷。

- [#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154)：redirect kill 后 scalar load 仍向 RF 写回错误路径数据。
- [#6151](https://github.com/OpenXiangShan/XiangShan/issues/6151)：`vl=0` 的 segment 指令使内存单元长时间停顿。
- [#6000](https://github.com/OpenXiangShan/XiangShan/issues/6000)：JumpUnit 的压缩 `c.jr` redirect 清除了 `isRVC`。
- [#6039](https://github.com/OpenXiangShan/XiangShan/issues/6039)：`vsetvl rd=zero` 导致 core hang。

### 4.3 分支预测、错误路径和时序安全

重点不是预测结果本身是否改变 ISA，而是 disable、flush 或 wrong-path 条件下是否仍访问 SRAM、更新预测状态或驱动 I/D-cache。

- [#6149](https://github.com/OpenXiangShan/XiangShan/issues/6149)：关闭 `RAS_ENABLE` 后仍进行 RAS/URAS 预测。
- [#6148](https://github.com/OpenXiangShan/XiangShan/issues/6148)：直接取指 block 访问 ITTAGE，形成软件可见时序通道。
- [#6138](https://github.com/OpenXiangShan/XiangShan/issues/6138)：错误路径分支训练 BPU 并泄露一 bit 时序信息。
- [#6159](https://github.com/OpenXiangShan/XiangShan/issues/6159)：关闭 ABTB 后 uTAGE 仍发起 SRAM 读请求。

## 五、架构与微架构的边界问题

以下问题建议采用“双层定位”而不是只归入一个团队：

| 表现 | 架构层检查 | 微架构层检查 |
| --- | --- | --- |
| 异常后 `mcause/mtval/vstart` 错误 | trap/向量规范和状态更新规则 | flush、kill、提交顺序和异常信息旁路 |
| 访存死锁或无法退休 | 应有的 fault/retire 结果 | LoadUnit、StoreUnit、ReplayQueue、MSHR 和 backpressure |
| 错误路径写回或时序泄露 | 软件可观察的安全/架构约束 | predictor、SRAM、cache 访问抑制和 wrong-path 清理 |
| difftest mismatch | NEMU/Spike 参考语义 | RTL 提交、difftest 打包和状态采样时机 |

## 六、修复优先级

1. **最高优先级：** 死锁、无法退休、错误路径写回，优先处理 [#6289](https://github.com/OpenXiangShan/XiangShan/issues/6289)、[#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267)、[#6151](https://github.com/OpenXiangShan/XiangShan/issues/6151)、[#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154)。
2. **架构一致性优先：** 集中处理 `mcause`、trap CSR、PMP/PMA final check、页故障、中断和 RVV `vstart/vl`。
3. **建立 RVV 组合回归：** 覆盖 segment、indexed、fault-only-first、masked、misaligned、异常后部分写回和 `vl=0`。
4. **建立安全回归：** 覆盖 RAS/BTB/TAGE/ITTAGE 的 disable、flush、wrong-path 访问和时序泄露。
5. **补充 Issue 标签：** 目前 61 个明确 Bug 仍为 `module: unknown`，建议在 triage 时同时标注模块和架构/微架构层级。

## 七、主分类编号清单

### 架构层（52）

`#6288, #6264, #6259, #6227, #6215, #6214, #6212, #6209, #6199, #6182, #6168, #6156, #6152, #6143, #6141, #6139, #6127, #6126, #6113, #6093, #6079, #6068, #6066, #6063, #6061, #6057, #6042, #6035, #5995, #5943, #5930, #5928, #5921, #5919, #5916, #5865, #5840, #5832, #5831, #5830, #5829, #5808, #5807, #5773, #5772, #5770, #5769, #5768, #5766, #5765, #5279, #4980`

> 注：编号清单保留了跨层问题的主责归类；个别 Issue 同时具备架构和微架构证据，具体修复仍需结合 RTL 和参考模型确认。

### 微架构层（40）

`#6289, #6268, #6267, #6265, #6229, #6159, #6158, #6155, #6154, #6153, #6151, #6150, #6149, #6148, #6138, #6137, #6135, #6133, #6130, #6085, #6065, #6064, #6060, #6059, #6048, #6039, #6022, #6018, #6015, #6002, #6000, #5960, #5958, #5934, #5933, #5932, #5931, #5845, #5767, #5137`
