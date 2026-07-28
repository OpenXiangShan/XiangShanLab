# 香山 GitHub 当前 Bug 类型汇总

> 数据采集时间：2026 年 7 月 28 日（北京时间）  
> 仓库：`OpenXiangShan/XiangShan`  
> 默认分支：`kunminghu-v3`

## 一、采集范围与口径

本报告使用 `get-xiangshan-bug` 技能流程采集 GitHub API 数据：

- 采集当前仍处于 `open` 状态的 Issue；Issue API 返回的 Pull Request 已剔除。
- 采集目标默认分支 `kunminghu-v3` 上当前开放的 Pull Request。
- Issue 的 `type: bug/reported` 和 `type: bug/confirmed` 是“明确 Bug”统计依据。
- `module:*` 标签用于模块统计；`module: unknown` 表示仓库尚未完成模块标注。
- 下文的“主题聚类”结合标题、标签和代表案例，属于工程归纳，不等同于 GitHub 官方标签；一个问题可能同时涉及多个主题。

## 二、总体盘点

| 指标 | 数量 | 说明 |
| --- | ---: | --- |
| 开放 Issue | 105 | 不含 Pull Request |
| 明确标记为 Bug | 92 | `type: bug/reported` 59 个，`type: bug/confirmed` 33 个 |
| 其他开放 Issue | 13 | `problem` 5、`feature/requested` 5、`question` 2、未分类 1 |
| 默认分支开放 PR | 74 | PR 不等于 Bug，包含修复、功能、重构和工具链变更 |
| Bug Issue 中 `module: unknown` | 61 | 约三分之二的明确 Bug 尚未完成模块标注 |

开放 Bug 的更新时间覆盖 **2025 年 8 月 28 日至 2026 年 7 月 28 日**。最新一批问题集中在 2026 年 7 月，说明当前问题库仍在持续增长，不能只按历史已关闭问题判断风险。

## 三、Bug 类型汇总

### 1. RVV/向量指令语义与状态机问题

这是当前最突出的主题之一，典型症状包括向量结果错误、异常后部分写回、`vl/vstart` 状态错误、fault-only-first 行为不一致，以及向量浮点舍入/装箱问题。

代表案例：

- [#6151](https://github.com/OpenXiangShan/XiangShan/issues/6151)：`vl=0` 的 segment 指令导致内存单元长时间停顿。
- [#6152](https://github.com/OpenXiangShan/XiangShan/issues/6152)：`vzext.vf4/vsext.vf4` 高 64 位写入为零。
- [#5934](https://github.com/OpenXiangShan/XiangShan/issues/5934)：masked segment load 覆盖活动目标 lane。
- [#5767](https://github.com/OpenXiangShan/XiangShan/issues/5767)：fault-only-first 向量 load 触发内部 critical error。
- [#5765](https://github.com/OpenXiangShan/XiangShan/issues/5765)：`vlm.v` 后 `v0` 尾部 bit 出现 difftest mismatch。

**判断：** RVV 问题横跨执行、向量寄存器写回、异常恢复和 difftest，适合按“指令类别 + 异常场景 + `vl/vstart` 状态”建立回归矩阵，而不是逐条修复。

### 2. 特权态、CSR、PMP/PMA、页表与异常处理

该类问题主要表现为异常类型、`mcause`、`mtval/mepc` 等 trap CSR、最终权限检查、页故障边界条件和调试触发行为不符合规范或参考模型。

代表案例：

- [#6288](https://github.com/OpenXiangShan/XiangShan/issues/6288)：M-mode 下 `sw` 报告错误的 `mcause`。
- [#6259](https://github.com/OpenXiangShan/XiangShan/issues/6259)：`HLV.WU` 忽略 `SPVP=VU` 的有效特权级进行最终 PMP 检查。
- [#6264](https://github.com/OpenXiangShan/XiangShan/issues/6264)：跨 Sv39 canonical boundary 的顺序取指未触发 instruction page fault。
- [#6227](https://github.com/OpenXiangShan/XiangShan/issues/6227)：Bare 模式 trap 地址 CSR 被截断/零扩展。
- [#6182](https://github.com/OpenXiangShan/XiangShan/issues/6182)：清除中断使能后仍取得过期中断。

**判断：** 这类问题的共同风险是“架构可见状态”和流水线内部 redirect/kill 不一致；应优先用 NEMU/Spike 对照，并覆盖 M/S/VS、Bare/分页、PMP 锁定区和多核场景。

### 3. Load/Store、缓存、原子操作和内存一致性

典型症状包括死锁、错误转发、错失 replay、缓存断言、MMIO/uncache 元数据错误，以及原子或预取指令的权限处理错误。

代表案例：

- [#6289](https://github.com/OpenXiangShan/XiangShan/issues/6289)：`vse32.v` store access fault 后 AMO deadlock。
- [#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267)：PMA 缺失地址上的 `fsw` 使 StoreUnit deadlock。
- [#6268](https://github.com/OpenXiangShan/XiangShan/issues/6268)：L1 DCache 连续 SC 失败断言。
- [#6229](https://github.com/OpenXiangShan/XiangShan/issues/6229)：跨 16B 边界的 misaligned load 未完成低半部 store-to-load forwarding。
- [#6154](https://github.com/OpenXiangShan/XiangShan/issues/6154)：redirect kill 后 scalar load 仍写回错误路径数据。

**判断：** 当前最需要关注的是异常/redirect 与 LoadUnit、StoreUnit、DCache 的交互，而不只是单一缓存命中逻辑；死锁类问题优先级应高于普通数据错误。

### 4. 前端取指与分支预测侧信道/控制问题

问题集中在 uTAGE/ITTAGE/RAS/ABTB 控制关闭后的残留访问、错误路径训练，以及预测行为导致的软件可见时序侧信道。

代表案例：

- [#6149](https://github.com/OpenXiangShan/XiangShan/issues/6149)：关闭 `RAS_ENABLE` 后仍进行 RAS/URAS 预测。
- [#6148](https://github.com/OpenXiangShan/XiangShan/issues/6148)：直接取指 block 访问 ITTAGE，形成时序通道。
- [#6138](https://github.com/OpenXiangShan/XiangShan/issues/6138)：错误路径分支训练 BPU 并泄露一 bit 时序信息。
- [#6159](https://github.com/OpenXiangShan/XiangShan/issues/6159)：关闭 ABTB 后 uTAGE 仍发起 SRAM 读请求。

**判断：** 这不是单纯功能 bug，而是“控制位关闭后是否真正阻断所有状态更新、SRAM 访问和错误路径影响”的系统性问题，建议作为独立安全回归套件维护。

### 5. Difftest、参考模型和验证基础设施

当前开放 Bug 中直接带有 difftest/reference 语义的条目数量不多，但它们往往是发现架构问题的入口；同时不少 RVV、CSR 和异常类问题本身也是与 NEMU/Spike 对照后暴露的。

代表案例：[#5765](https://github.com/OpenXiangShan/XiangShan/issues/5765)、[#5807](https://github.com/OpenXiangShan/XiangShan/issues/5807)。

**判断：** 不应把 difftest mismatch 视为独立根因；应继续向下追踪到具体的指令语义、提交状态或异常状态差异。

## 四、模块标签现状

在 92 个明确 Bug Issue 中，GitHub 当前模块标签分布如下。由于一个 Issue 可以有多个模块标签，合计不会等于 92。

| 模块标签 | Bug 数 | 备注 |
| --- | ---: | --- |
| `module: unknown` | 61 | 标注缺口最大，影响按模块分派和统计 |
| `module: frontend` | 10 | 取指、预测、RAS/uTAGE 等 |
| `module: backend` | 12 | 提交、CSR、执行和部分异常路径 |
| `module: memory` | 9 | Load/Store、Cache、MMU/访存路径 |
| `module: tool` | 3 | 工具或验证相关 |

开放 PR 的模块标签以 `memory`（32）、`top`（28）、`backend`（26）、`tool`（18）和 `frontend`（13）为主；其中包含功能开发、重构、submodule bump 等内容，不能直接当作 Bug 数量。

## 五、优先级建议

1. **P0：先处理死锁、无法退休和错误路径写回。** 重点关注 [#6289](https://github.com/OpenXiangShan/XiangShan/issues/6289)、[#6267](https://github.com/OpenXiangShan/XiangShan/issues/6267)、[#6151](https://github.com/OpenXiangShan/XiangShan/issues/6151)、[#6229](https://github.com/OpenXiangShan/XiangShan/issues/6229)。
2. **P1：集中修复架构可见异常状态。** 优先覆盖 `mcause`、PMP/PMA final check、页故障、trap CSR 和中断使能边界。
3. **P1：建立 RVV 异常/写回回归矩阵。** 将 segment、indexed、fault-only-first、masked、misaligned、`vl=0` 和 `vstart` 组合化测试。
4. **P1：单独维护安全回归集。** 对 RAS/BTB/TAGE/ITTAGE 的 disable、flush、wrong-path 访问和时序泄露进行统一验证。
5. **P2：补齐模块标签。** 61 个明确 Bug 仍为 `module: unknown`，建议在 triage 时至少补充 `frontend/backend/memory/tool` 之一，并增加 `topic: security`、`topic: difftest` 等主题标签。

## 六、数据文件

- `issues.jsonl`：105 个开放 Issue 的原始压缩记录。
- `pulls.jsonl`：默认分支 `kunminghu-v3` 上 74 个开放 PR 的原始压缩记录。
- `issue-index.md`：Issue 索引。
- `pr-index.md`：PR 索引。
- `bug-cause-summary.md`：脚本生成的启发式原因摘要；由于 Issue 模板字段会造成关键词过匹配，本报告未直接把该文件的计数当作最终结论。
