# BEQ 指令后端执行生命周期

## 📋 基本信息

| 字段 | 内容 |
|---|---|
| **指令名称** | `beq rs1, rs2, offset` |
| **编码格式** | B-type；`funct3=000`，`opcode=1100011` |
| **RISC-V 扩展** | RV64I 条件分支 |
| **指令分类** | 整数条件分支 |
| **FuType** | `FuType.brh` |
| **FuOpType** | `BRUOpType.beq` |
| **立即数选择** | `SelImm.IMM_SB` |
| **目标 FU** | `BranchUnit` → `BranchModule` |

本文从 Decode 已经产生 `DecodedInst` 开始，只分析 BEQ 在香山后端中的数据通路和预测错误恢复。

## 1. 后端路径

```text
DecodedInst
    ↓
Rename
    ↓
NewDispatch ───────→ ROB
    ↓
Issue Queue
    ↓
Scheduler / ExuBlock / ExeUnit
    ↓
BranchUnit
    ├── taken / target
    └── mispredict → RedirectGenerator → CtrlBlock
                                      ↓
                         清除年轻指令并恢复推测状态
```

BEQ 不产生整数目的寄存器结果，但仍然进入 ROB，用于按程序顺序跟踪完成和提交。

## 2. 译码结果

译码表位于 `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`：

```scala
BEQ -> XSDecode(
  SrcType.reg, SrcType.reg, SrcType.X,
  FuType.brh, BRUOpType.beq, SelImm.IMM_SB
)
```

| 字段 | BEQ 的值 | 含义 |
|---|---|---|
| `srcType(0)` | `SrcType.reg` | `rs1` 是整数寄存器 |
| `srcType(1)` | `SrcType.reg` | `rs2` 是整数寄存器 |
| `srcType(2)` | `SrcType.X` | 不使用第三源 |
| `fuType` | `FuType.brh` | 进入分支功能单元 |
| `fuOpType` | `BRUOpType.beq` | 选择相等比较 |
| `selImm` | `SelImm.IMM_SB` | 选择 B 型立即数 |
| `rfWen` | `false` | 不写整数目的寄存器 |

`XSDecode.generate()` 将 `xWen` 放入译码表；`DecodedInst.decode()` 使用 `ListLookup` 选中表项，再按 `allSignals` 的字段顺序写入 `DecodedInst`。BEQ 没有显式设置 `xWen`，因此使用默认值 `false`。

Decode 同时填写：

```text
lsrc(0) = rs1
lsrc(1) = rs2
```

此时仍是逻辑寄存器编号。

## 3. 重命名

| 项目 | 内容 |
|---|---|
| 输入 | `DecodedInst`，包含 `lsrc(0/1)`、`rfWen`、`fuType`、`fuOpType` |
| 输出 | `DynInst`，包含 `psrc(0/1)` 和 `robIdx` |
| 源寄存器 | 通过整数 RAT 将 `rs1`、`rs2` 映射为物理寄存器 |
| 目的寄存器 | BEQ 的 `rfWen=false`，不从整数 Free List 分配 `pdest` |
| 动态信息 | 保留 `fuType`、`fuOpType`、PC、立即数和预测信息 |
| BEQ 示例 | `rs1 → p42`、`rs2 → p17`，输出 `psrc(0)=p42`、`psrc(1)=p17` |
| 代码位置 | `backend/rename/Rename.scala`、`RenameTable.scala` |

Rename 的核心连接为：

```text
rs1 ──► intReadPorts(0) ──► psrc(0)
rs2 ──► intReadPorts(1) ──► psrc(1)
```

## 4. 分发

| 项目 | 内容 |
|---|---|
| 输入 | Rename 输出的 `DynInst` |
| ROB 输出 | `enqRob.req`，保存 BEQ 的顺序、完成状态和 `robIdx` |
| IQ 输出 | `toIssueQueues`，进入支持 `FuType.brh` 的 Issue Queue |
| 队列选择 | 根据 `fuType` 和后端 `FuConfig` 查找候选 IQ |
| 多队列选择 | 使用 `IQValidNumVec` 比较队列占用，选择负载较小的队列 |
| 源状态 | `BusyTable` 读取 `psrc(0/1)`，生成 `srcState(0/1)` |
| 入队条件 | ROB 可接收、IQ 入队端口可用、没有更老指令阻塞 |
| 握手信号 | `fromRename.fire = valid && ready` |
| 代码位置 | `backend/dispatch/NewDispatch.scala` |

BEQ 同时进入 ROB 和 Issue Queue：

```text
Rename.fromRename
        │
        ├──► NewDispatch.enqRob.req ──► ROB
        └──► NewDispatch.toIssueQueues ──► Issue Queue
```

## 5. 发射

| 项目 | 内容 |
|---|---|
| 输入 | Issue Queue 中的 BEQ uop |
| 保存字段 | `psrc(0/1)`、`fuType`、`fuOpType`、`srcState(0/1)`、`robIdx` |
| 唤醒条件 | 两个整数源操作数已经就绪 |
| 资源条件 | 分支执行端口和目标 ExuBlock 可用 |
| 输出 | Scheduler 将 BEQ 发往整数 `ExuBlock` |
| Flush 条件 | BEQ 在发射前被 redirect 按年龄取消时，不再发射 |
| 代码位置 | `Scheduler.scala`、`IssueQueue.scala`、`ExuBlock.scala` |

Issue Queue 负责保存和选择 uop，BranchUnit 负责计算分支条件。

## 6. 执行

| 项目 | 内容 |
|---|---|
| 输入操作数 | `src(0)`、`src(1)` |
| 控制字段 | `fuOpType = BRUOpType.beq`、`predictInfo.taken` |
| 实际条件 | `taken = !((src1 ^ src2).orR)`，即 `src1 == src2` |
| 跳转目标 | taken 时为分支 PC 加 B 型立即数 |
| 顺序目标 | not-taken 时为顺序下一条 PC |
| 预测判断 | `mispredict = pred_taken ^ taken` |
| 输出 | `taken`、目标地址、`mispredict` |
| 代码位置 | `fu/wrapper/BranchUnit.scala`、`fu/Branch.scala` |

| 预测结果 | 实际结果 | `mispredict` | 处理 |
|---|---|---:|---|
| not-taken | not-taken | 0 | 正常完成 |
| taken | taken | 0 | 正常完成 |
| not-taken | taken | 1 | 按分支目标恢复 |
| taken | not-taken | 1 | 按顺序 PC 恢复 |

## 7. Redirect 与 Flush

| 项目 | 内容 |
|---|---|
| 触发条件 | `mispredict=1` |
| 产生位置 | `BranchUnit` 的 redirect 输出 |
| 中间路径 | `ExuBlock → RedirectGenerator → CtrlBlock` |
| 恢复边界 | `RedirectLevel.flushAfter` |
| redirect 信息 | `robIdx`、`fullTarget`、`cfiUpdate` |
| ROB | 清除 BEQ 之后的年轻 entry，保留 BEQ |
| Issue Queue | 删除错误路径上尚未发射的 uop |
| ExeUnit | 清除流水线中更年轻的 uop |
| Rename / Free List | 恢复重命名状态，回收错误路径资源 |
| Dispatch | 阻止错误路径继续入队 |
| 代码位置 | `ctrlblock/RedirectGenerator.scala`、`CtrlBlock.scala`，以及各模块的 `needFlush` 逻辑 |

```text
BranchUnit
    ↓
ExuBlock
    ↓
RedirectGenerator
    ↓
CtrlBlock
    ├── ROB
    ├── Rename / Free List
    ├── Dispatch
    ├── Issue Queue
    ├── ExeUnit
    └── DataPath
```

`flushAfter` 保留产生 redirect 的 BEQ 及其之前的较老指令，只清除 BEQ 之后的年轻状态。

## 8. 完成与提交

BEQ 不写整数寄存器，因此没有整数写回数据。它仍需向 ROB 报告执行完成，等待所有更老指令完成后，在 ROB 头部按程序顺序提交。

预测正确时，BEQ 完成后正常提交。预测错误时，BEQ 作为恢复边界保留；错误路径上的年轻 uop 被清除，正确路径上的指令重新进入后端。

## 9. 关键代码索引

| 后端阶段 | 文件 | 重点内容 |
|---|---|---|
| 译码结果 | `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala` | BEQ 译码表项、`XSDecode` 参数、`lsrc` 填写和 `rfWen` 输出 |
| uop 字段 | `src/main/scala/xiangshan/backend/Bundles.scala` | `DecodedInst`、`DynInst`、`allSignals` 和译码字段连接 |
| 重命名 | `src/main/scala/xiangshan/backend/rename/Rename.scala` | RAT 读端口、`psrc` 生成、`robIdx` 分配和目的物理寄存器申请 |
| 重命名表 | `src/main/scala/xiangshan/backend/rename/RenameTable.scala` | 逻辑寄存器到物理寄存器的映射读取与更新 |
| 分发 | `src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala` | Issue Queue 选择、`BusyTable` 查询、ROB/IQ 入队和背压 |
| 调度 | `src/main/scala/xiangshan/backend/schedule/Scheduler.scala` | 就绪 uop 选择和执行端口分配 |
| Issue Queue | `src/main/scala/xiangshan/backend/issue/IssueQueue.scala` | uop 保存、源操作数唤醒和发射请求 |
| 执行单元 | `src/main/scala/xiangshan/backend/exu/ExuBlock.scala`、`ExeUnit.scala` | Issue Queue 到功能单元的连接和执行结果返回 |
| 分支执行 | `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala` | 实际分支方向、目标地址和 redirect 生成 |
| 分支判断 | `src/main/scala/xiangshan/backend/fu/Branch.scala` | `taken` 和 `mispredict` 的计算 |
| Redirect 选择 | `src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala` | 多个 redirect 请求的汇总和按 ROB 年龄选择 |
| Flush 控制 | `src/main/scala/xiangshan/backend/ctrlblock/CtrlBlock.scala` | redirect 向 ROB、Rename、Dispatch、Issue Queue 和执行单元的传播 |
| ROB | `src/main/scala/xiangshan/backend/rob/Rob.scala` | BEQ 的完成、按序提交和年轻指令清除 |

阅读顺序可以概括为：

```text
DecodeUnit.scala / Bundles.scala
  → Rename.scala / RenameTable.scala
  → NewDispatch.scala
  → Scheduler.scala / IssueQueue.scala
  → ExuBlock.scala / ExeUnit.scala
  → BranchUnit.scala / Branch.scala
  → RedirectGenerator.scala / CtrlBlock.scala
  → Rob.scala
```

每读完一个模块，记录 BEQ uop 的输入字段、输出字段，以及 redirect 或 `robIdx` 在该模块中的作用。
