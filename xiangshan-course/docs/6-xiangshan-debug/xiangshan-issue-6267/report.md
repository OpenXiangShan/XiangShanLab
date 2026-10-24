# XiangShan Issue #6267：异常 vector store 卡死 Store Queue，导致后续 `fsw` 永久不提交

来源：[OpenXiangShan/XiangShan issue #6267](https://github.com/OpenXiangShan/XiangShan/issues/6267)

## 摘要

在 XiangShan `kunminghu-v3` 的固定版本中，测试程序对非法地址执行一条普通存储指令：

```asm
0x800013bc: fsw fa1, 716(t6)    # t6 = 0，实际地址为 0x2cc
```

参考模型将该访问报告为精确的 store/AMO access fault（`mcause=7`，`mtval=0x2cc`）；测试程序的异常处理程序会将 `mepc` 加 4，以跳过故障指令。DUT 没有产生可提交的异常，也没有完成该 store；仿真在提交 `0x800013b8` 后停止前进，最终触发 15000 周期无提交的 deadlock 检测。

波形分析表明，`fsw` 不是最早造成阻塞的指令。更早的一条异常 vector store 在物理 Store Queue 的槽位 `[4,8)` 中留下了 `hasException=1、committed=0` 的 entries。virtual queue 通过 `vecMbCommit` 提前释放了对应 entry，随后异常 redirect 又保留了这四个物理槽位；它们既不能提交，也不能写回或取消，因而永久占据队列头，堵住包括 `fsw` 在内的所有后续 store。

## 影响

- 触发条件是异常 vector memory store 位于 Store Queue 头部，且异常处理与 vector merge-buffer 的 retire/redirect 指针不同步。
- 受影响的后续 store 无法出队，ROB 最终停在第一条受阻的普通 store 上。
- 这是功能性死锁，程序不会自行恢复；需要终止仿真。

## 复现环境

| 项目 | 值 |
|---|---|
| XiangShan | `b90dbba40d16d54f2814870ff8b4d8809a41e680` |
| xs-env | `33d5f6f611d15a65c6194290fa62caf0c0c27f41` |
| 配置 | `DefaultConfig` |
| Trace | FST（`EMU_TRACE=fst`） |
| 输入 | [`case/seeds_565_.elf`](case/seeds_565_.elf) |
| 输入 SHA-256 | `2688f1ca2fbf7b92eb38bb5f237d2d461297e3eec49b6e28fdc343579ba7e053` |
| 参考模型 | `ready-to-run/riscv64-nemu-interpreter-so` |

原始输入附件和日志保存在 [`case/seed.zip`](case/seed.zip) 与 [`case/seeds.log`](case/seeds.log)。

## 复现步骤

在 Linux 环境准备 `bash`、Git、GNU make、Java、Mill、Verilator 和 XiangShan 所需工具，然后执行：

```bash
cd xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6267
./replay.sh
```

脚本会检查 ELF 的 SHA-256，checkout 固定版本，初始化子模块，构建 `DefaultConfig` emulator，并运行：

```bash
xs-env/XiangShan/build/emu -i case/seeds_565_.elf \
  --diff xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so \
  --dump-wave-full --wave-path replay.fst
```

预期会生成 `replay.log` 和 `replay.fst`。完整构建需要网络访问 GitHub；脚本不会自动安装系统软件。

## 实际行为与预期行为

### 实际行为

- `amoxor.w.rl`（`0x8000138c`）和 `sc.d.aqrl`（`0x800013a0`）的异常可以被处理并恢复。
- `0x800013bc` 的 `fsw` 进入 StoreUnit，地址为 `0x2cc`，但没有完成、提交或产生 DUT 可见的精确异常。
- DUT 最后提交 `0x800013b4` 和 `0x800013b8`，之后 15000 个采样周期没有有效提交。
- deadlock 检测推进 REF 一条指令后，REF 报告 `mcause=7, mepc=0x800013bc, mtval=0x2cc`，与停滞的 DUT 不一致并 abort。

### 预期行为

ROB 处理异常 vector store 后，Store Queue 应清理对应物理 entry，保持 virtual/physical queue 状态一致，并允许后续指令继续执行。后续 `fsw` 最终应完成访问或按平台规则报告精确异常，不能因遗留的异常队头失去进展。

## 波形证据与故障链

以下 cycle 为 `replay.fst` 中的上升沿采样值。

| cycle | 事件 | 关键观测 |
|---:|---|---|
| 6688 | 异常 vector store 入队 | virtual entry 4：`isVec=1`、`reqNum=4`、ROB=145；物理指针 `4→8` |
| 6704–6705 | STA 完成并标记异常 | 物理槽 4 写入 ROB=145；槽 `[4,8)` 的 `hasException` 置 1，`committed` 仍为 0 |
| 6712–6714 | virtual queue 提前释放 | `vecMbCommit=1`、`retireVec_0=1`；virtual entry 被释放，`toPhysicalQueueRetirePtr` 从 4 推到 8；ROB 尚未提交 ROB145 |
| 6713、6720 | 异常送达 ROB | `exceptionWBsVec_8` 携带 ROB145 和 `exceptionVec_7=1`；ROB 随后接受 store access fault |
| 6724–6725 | redirect 清理范围错误 | redirect pointer 为 8，只取消槽 `[8,…)`；异常槽 `[4,8)` 被保留 |
| 7142–7145 | `fsw` 到达 StoreUnit | ROB=187，地址 `0x2cc`；`pmp.st=0`、`pmp.mmio=1`、`hasException=0`，被视为无异常 MMIO store |
| 7158–7161 | 架构进展停止 | ROB 头到达 187；最后有效提交为 `0x800013b4/0x800013b8` |
| 7162–22164 | 死锁窗口 | 15002 个采样周期无提交；物理 `deqPtr/rdataPtr/commitPtr` 恒为 4 |

物理队列头的异常槽满足 `hasException && committed=0`。固定版本源码中（[NewStoreQueue.scala](https://github.com/OpenXiangShan/XiangShan/blob/b90dbba40d16d54f2814870ff8b4d8809a41e680/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L963)）：

- `uncacheCanHandle` 要求 `!hasException && committed`；
- `toSbufferValid` 要求 `committed`；
- commit 路径排除 `hasException`；
- 队列指针只在 writeback、sbuffer、uncache 或 vector inactive 路径推进。

因此槽 `[4,8)` 没有任何可达的出队路径，`uncacheState` 停在 idle，后续槽 8（即 `fsw`）永远无法成为可处理的队列头。

## 根因判断

[VirtualStoreQueue.scala](https://github.com/OpenXiangShan/XiangShan/blob/b90dbba40d16d54f2814870ff8b4d8809a41e680/src/main/scala/xiangshan/mem/lsqueue/VirtualStoreQueue.scala#L206) 的完成反馈与 [redirect 恢复指针选择](https://github.com/OpenXiangShan/XiangShan/blob/b90dbba40d16d54f2814870ff8b4d8809a41e680/src/main/scala/xiangshan/mem/lsqueue/VirtualStoreQueue.scala#L390) 共同构成故障链：virtual Store Queue 将一个已经带有 store access fault、但尚未由 ROB 提交的 vector store 当作 `vecMbCommit` 完成项释放，并把物理 redirect 的恢复指针推进到该 vector store 之后。redirect 的保留区间正好覆盖异常槽 `[4,8)`，导致这些槽同时满足：

1. 已标记异常，不能走正常 commit/writeback；
2. 已从 virtual queue 释放，不能再由 vector retire 路径处理；
3. 位于物理队列头，后续 store 不能越过；
4. redirect 没有取消它们。

`fsw` 只是被该队列状态阻塞的第一个普通 store。旧的“`fsw` 自身 MMIO 请求没有返回”解释与波形不符：在 `fsw` 的 S2/S3 样本中没有 store fault，且它根本没有机会成为物理队列头。

## 证据和限制

- 原始日志：[`case/seeds.log`](case/seeds.log)
- 仿真日志：[`replay.log`](replay.log)
- 原始波形：`replay.fst`（56,165,502 bytes，未纳入课程仓库；运行 `replay.sh` 可重新生成）
- 波形证据：[`store-fault-analysis/evidence.json`](store-fault-analysis/evidence.json)、[`event_state.json`](store-fault-analysis/event_state.json)、[`head_audit.json`](store-fault-analysis/head_audit.json)、[`rows_redirect.json`](store-fault-analysis/rows_redirect.json)
- 提取脚本：[`verify.py`](store-fault-analysis/verify.py)、[`head_audit.py`](store-fault-analysis/head_audit.py)；采样定义、复验命令和证据边界见 [evidence.md](evidence.md)
- 流程图解：[bug-explanation.md](bug-explanation.md)

当前波形覆盖的是有限仿真窗口；它证明了窗口内的队列停滞和指针状态，不单独证明“无限时间”性质。`xs-env` 的 XiangShan gitlink 原值与本次 replay 使用的 XiangShan commit 不同，报告以实际 checkout 的 commit 为准。Issue 讨论中维护者将问题归因于仍在重构的 vector memory 功能；该说明与本报告的波形根因一致，但不等于已有修复。
