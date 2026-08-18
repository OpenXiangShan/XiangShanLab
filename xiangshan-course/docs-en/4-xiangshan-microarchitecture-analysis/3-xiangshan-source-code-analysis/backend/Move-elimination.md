<!--
# 3.Move 指令消除

# 3. Move 指令消除

> 如果你已经学完了寄存器重命名，那你一定已经知道：当一条指令写目的寄存器时，重命名模块会从 Freelist 中分配一个全新的物理寄存器（PR），然后把架构寄存器映射到这个新 PR 上。但有没有一种情况——我们其实**根本不需要分配新 PR**？答案是：有，而且非常常见。这就是 **Move 指令消除（Move Elimination）**。别担心，它比重命名还简单——本质上就是"省了一步"。

:::info
通过本节学习，你将能够：

* 🧭 理解 Move 指令消除的**核心动机**：为什么 move 指令不需要真的"执行"
* 📋 掌握香山 Move Elimination 的**完整机制**：从 isMove 标记到映射表直通
* 🔍 吃透 **MEFreeList** 的引用计数设计与 **StdFreeList** 的关键区别
* ⚡ 理解 Move 消除在**提交、回退、快照恢复**中的特殊处理逻辑
* 🗺️ 获得**从源码到实践的完整学习路径**

:::

***

## 3.1 为什么需要 Move 指令消除？

### 3.1.1 Move 指令：看似简单，实则浪费

在 RISC-V 指令集中，并没有一条叫 `mv` 的独立指令。`mv x10, x5` 实际上是 `addi x10, x5, 0` 的伪指令——把 x5 的值加上零，存入 x10。在普通重命名流程中，它的执行路径是这样的：

```plain
mv x10, x5  (实际是 addi x10, x5, 0)

普通重命名流程：
1. 从 Freelist 分配新 PR（比如 PR50）
2. 更新映射表：x10 → PR50
3. 将 PR50 的编号发往保留站、Issue Queue
4. 等待 Issue → 派发到 ALU 执行
5. ALU 执行加零操作：PR50 = PR_x5 + 0
6. 写回 PR50
7. 提交后回收旧的 x10 映射

总共消耗：1个物理寄存器 + 1个ROB条目 + 1个Issue Queue条目 + ALU执行周期
```

但你有没有发现——**这个结果和 PR\_x5 的值完全一样**？PR50 里存的，就是 PR\_x5 的值加了个零。那我们为什么不直接让 x10 指向 PR\_x5，省掉所有这些中间步骤呢？

> 类比：想象你要把一份文件从 A 柜复制到 B 柜，但文件内容一模一样——你完全可以只在 B 柜贴个标签说"内容同 A 柜第X格"，而不需要真的复印一份放进去。Move 消除就是干这件事的。

### 3.1.2 Move 指令有多常见？

Move 指令在程序中出现频率极高：

| **场景** | **典型 Move 指令** | **出现频率** |
| --- | --- | --- |
| 函数调用参数传递 | `mv a0, x5` | 极高 |
| 寄存器值保存/恢复 | `mv s0, x10` | 高 |
| 编译器寄存器分配 | `mv x10, x5` | 高 |
| 条件移动伪指令 | `seqz x10, x5`（= `sltiu x10, x5, 1`） | 中 |

据统计，Move 类指令在典型工作负载中占比可达 **5%~10%**。如果能全部消除，相当于省下了 5%~10% 的 ALU 执行带宽和物理寄存器资源——这对高性能处理器来说是不可忽视的性能收益。

:::color4
**❤**\*\* 核心思想：\*\* Move 指令消除的本质就是——**既然结果和源操作数一模一样，就不分配新物理寄存器，直接让目的架构寄存器映射到源的物理寄存器**。省PR、省执行单元、省延迟，一举三得。

:::

***

## 3.2 Move 指令消除的全景架构

### 3.2.1 消除前 vs 消除后对比

```plain
╔══════════════════════════════════════════════════════════════════╗
║              mv x10, x5  的两种处理路径                          ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  ❌ 普通重命名（不消除）：                                        ║
║                                                                  ║
║  x5 ──→ PR20       x10 ──→ PR30（旧映射）                       ║
║       │                   │                                      ║
║       │    Freelist ──→ 分配PR50                                 ║
║       │                   │                                      ║
║       │         x10 ──→ PR50（新映射）                            ║
║       │                   │                                      ║
║       └──→ ALU: PR50 = PR20 + 0 ──→ 写回PR50                    ║
║                                                                  ║
║  消耗：1个PR + ALU执行 + Issue/ROB资源                           ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  ✅ Move消除：                                                    ║
║                                                                  ║
║  x5 ──→ PR20       x10 ──→ PR30（旧映射）                       ║
║       │                                                          ║
║       └───── 直接映射 ────→ x10 ──→ PR20（共享源PR）             ║
║                                                                  ║
║  不分配新PR，不经过ALU，零延迟                                    ║
║                                                                  ║
║  消耗：0个PR + 0执行周期 + 节省Issue/ROB压力                     ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝
```

> **图表解读**：Move 消除的关键在于"不分配新PR，直接复用源PR"。这意味着同一个物理寄存器 PR20 同时被 x5 和 x10 两个架构寄存器指向——这就是为什么需要**引用计数**。

### 3.2.2 香山 Move 消除的全景流程

```plain
┌────────────────── Move 消除全景流程 ──────────────────┐
│                                                       │
│  ① 译码阶段                                           │
│     Decode 识别 Move 指令                              │
│     设置 isMove = true 标记                            │
│              │                                        │
│              ↓                                        │
│  ② 重命名阶段（核心！）                                │
│     ┌──────────────────────────────┐                  │
│     │ if (isMove):                 │                  │
│     │   pdest = psrc（源操作数PR）  │  ← 不分配新PR    │
│     │   不请求 Freelist 分配        │                  │
│     │ else:                        │                  │
│     │   pdest = Freelist.allocate()│  ← 正常分配      │
│     └──────────────────────────────┘                  │
│              │                                        │
│              ↓                                        │
│  ③ 映射表更新                                         │
│     spec_table[ldest] = pdest                         │
│     （Move 消除时，pdest 等于源 PR，两个 AR 指向同 PR） │
│              │                                        │
│              ↓                                        │
│  ④ 提交阶段                                           │
│     Move 指令提交时，不更新 archHeadPtr                │
│     （因为没有分配新 PR，无需移动 Freelist 头指针）      │
│              │                                        │
│              ↓                                        │
│  ⑤ 回收阶段                                           │
│     当 x10 的旧映射 PR30 不再被任何架构寄存器引用时     │
│     引用计数降为0 → 释放 PR30 回 Freelist              │
│                                                       │
└───────────────────────────────────────────────────────┘
```

:::color4
**❤**\*\* 新手建议：\*\* 现阶段你只需记住 Move 消除的核心操作：**isMove 指令的 pdest 直接等于 psrc，不分配新 PR**。由此带来的一切特殊处理（引用计数、提交跳过 archAlloc、回收判断），都是这个核心操作的必然推论。

:::

***

## 3.3 isMove 标记的识别与传播

### 3.3.1 译码阶段如何识别 Move 指令？

Move 指令的识别在**译码阶段**完成。香山在指令的微操作中设置了 `isMove` 标记位，用于在重命名阶段区分普通指令和可消除的 Move 指令。

在 RISC-V 中，以下指令可以被视为 Move 指令进行消除：

| **伪指令** | **实际编码** | **Move 消除条件** |
| --- | --- | --- |
| `mv rd, rs` | `addi rd, rs, 0` | 立即数为0的 ADDI |
| `fmv.s rd, rs` | `fsgnj.s rd, rs, rs` | 浮点搬移（源1=源2的 FSGNJ） |
| - | `add rd, rs, x0` | 源2为x0的ADD（x0恒零） |
| - | `or rd, rs, x0` | 源2为x0的OR |
| - | `xor rd, rs, x0` | 源2为x0的XOR |

> 类比：译码阶段就像医院的分诊台——看到"只是搬个数据"的指令，就给它贴上"绿色通道"标签（isMove=true），后面各个环节就知道该怎么特殊处理了。

### 3.3.2 isMove 在流水线中的传播

`isMove` 标记从译码阶段产生后，会随微操作一起在流水线中传播。在 `Rename.scala` 的提交接口中，我们可以清晰地看到 `isMove` 标记被传递到 ROB/RAB 提交逻辑：

```scala
// 来自 Rename.scala L182-L188
intFreeList.io.commit match {
  case commit =>
    commit.doCommit := io.rabCommits.isCommit
    commit.archAlloc := io.rabCommits.commitValid zip io.rabCommits.info map {
      case (valid, info) => valid && info.rfWen && !info.isMove
      //                                  ^^^^^^^^^^^^^ 关键：isMove指令不触发archAlloc
    }
}
```

**这段代码的含义**：在整数 Freelist 的提交逻辑中，只有满足以下**三个条件**的指令才会触发 `archAlloc`（架构级分配）：

1. `valid`：指令有效
2. `rfWen`：指令写了整数寄存器
3. `!info.isMove`：**不是 Move 消除指令**

为什么 Move 消除指令不需要触发 `archAlloc`？因为 Move 消除没有从 Freelist 中分配新的 PR，自然提交时也不需要移动 Freelist 的架构头指针。

***

## 3.4 重命名阶段的 Move 消除核心逻辑

### 3.4.1 源码中的 Move 消除判断

Move 消除的核心发生在重命名阶段。当一条指令被标记为 `isMove` 时，重命名模块会**跳过 Freelist 分配**，直接将目的寄存器的物理编号设为源操作数的物理编号。

在 `Rename.scala` 中，我们可以通过追踪重命名的输出逻辑来理解 Move 消除的完整实现：

```scala
// 来自 Rename.scala L83-L84
val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
```

重命名输出的 `RenameOutUop` 中包含了 `pdest`（目的物理寄存器号）和 `psrc`（源物理寄存器号）。对于 Move 消除指令：

| **字段** | **普通指令** | **Move 消除指令** |
| --- | --- | --- |
| `pdest` | Freelist 分配的新 PR | 等于 `psrc(0)`（源操作数PR） |
| `psrc` | 查映射表获得的源 PR | 查映射表获得的源 PR |
| `isMove` | false | true |
| `rfWen` | true | true（仍然写寄存器，只是不经过ALU） |
| Freelist 请求 | `allocateReq = true` | `allocateReq = false`（不分配） |

### 3.4.2 Move 消除后的"共享物理寄存器"

Move 消除后，最关键的架构变化是：**一个物理寄存器可以被多个架构寄存器同时指向**。

```plain
// 初始状态：x5 → PR20, x10 → PR30
mv x10, x5

// Move 消除后：x5 → PR20, x10 → PR20
// PR20 同时被 x5 和 x10 两个架构寄存器引用！
```

这就是为什么整数寄存器使用 **MEFreeList（带引用计数的 Freelist）** 而非 StdFreeList——当一个 PR 被多个 AR 共享时，只有当**所有引用都消失**后，才能安全回收这个 PR。

:::warning
**关键认知**：Move 消除只适用于**整数寄存器**的 Freelist（`intFreeList = MEFreeList`）。浮点和向量寄存器的 Freelist（`fpFreeList`、`vecFreeList`）使用的是 `StdFreeList`，**不支持 Move 消除**。这是因为浮点/向量寄存器的 Move 指令频率较低，且引用计数的硬件开销不值得。

:::

***

## 3.5 MEFreeList：带引用计数的空闲链表

### 3.5.1 为什么整数寄存器需要 MEFreeList？

在前一章（寄存器重命名）中，我们介绍了 StdFreeList 的分配与回收机制：指令提交后，旧 PR 直接放回 Freelist 尾部。这个逻辑在**没有 Move 消除**时是正确的——因为每个架构寄存器在任意时刻只指向一个物理寄存器，旧 PR 一定不再被需要。

但 Move 消除打破了这个假设。考虑以下场景：

```plain
// 初始状态：x5 → PR20, x10 → PR30
mv x10, x5        // Move消除：x10 → PR20，PR20被x5和x10共享
add x5, x1, x2    // x5重命名 → PR40，x5不再指向PR20
// 此时 PR20 仍被 x10 引用，不能回收！

// 只有当 x10 也被重命名到其他 PR 后，PR20 才能安全回收
add x10, x3, x4   // x10重命名 → PR50，x10不再指向PR20
// 此时 PR20 才可以回收
```

**核心问题**：StdFreeList 的回收逻辑只看"旧PR是否还在arch\_table中"，无法处理"多个AR同时指向同一个PR"的场景。MEFreeList 通过**引用计数**解决了这个问题。

### 3.5.2 MEFreeList 源码解析

```scala
// 来自 MEFreeList.scala L27-L30
class MEFreeList(size: Int, commitWidth: Int)(implicit p: Parameters) extends BaseFreeList(size, commitWidth) with HasPerfEvents {
  val freeList = RegInit(VecInit(
    // originally {1, 2, ..., size - 1} are free. Register 0-31 are mapped to x0.
    Seq.tabulate(size - 1)(i => (i + 1).U(PhyRegIdxWidth.W)) :+ 0.U(PhyRegIdxWidth.W)))
```

**初始化**：MEFreeList 的空闲队列初始包含 PR1~PR(size-1) 和 PR0。注意 PR0 被特殊处理——因为 x0 恒为零，所有整数寄存器初始映射到 PR0，但 PR0 仍然在 Freelist 末尾等待分配。

### 3.5.3 MEFreeList 的分配逻辑

```scala
// 来自 MEFreeList.scala L53-L57
val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
for (i <- 0 until RenameWidth) {
  // enqueue instr, is move elimination
  io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}
```

**分配逻辑的关键**：

1. `phyRegCandidates` 根据 headPtr 的 One-Hot 编码，从 freeListVec 中选出当前可分配的 PR 列表
2. 对于第 i 条指令，它跳过前面已被分配的 PR（`PopCount(io.allocateReq.take(i))`），取出下一个空闲 PR
3. 注意注释 **"is move elimination"**：如果第 i 条指令是 Move 消除（`allocateReq(i) = false`），PopCount 自动跳过它——它根本不参与分配

> 类比：想象排队领号——Move 消除指令就是那个"我不用领号"的人，他直接插到别人的号码后面，不消耗号码资源。

### 3.5.4 MEFreeList 的回收逻辑——引用计数的核心

MEFreeList 的回收机制与 StdFreeList 有本质区别。它不是简单地"指令提交就把旧 PR 放回 Freelist"，而是通过 `freeReq` 和 `freePhyReg` 信号，配合**引用计数**来判断 PR 是否可以安全释放：

```scala
// 来自 MEFreeList.scala L78-L91
val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
for (i <- 0 until size) {
  val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
    w && freePtr(idx).value === i.U
  })
  val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
  when(freeReqOH.asUInt.orR) {
    freeList(i) := freePhyReg
  }
}
// update tail pointer
val tailPtrNext = tailPtr + PopCount(io.freeReq)
tailPtr := tailPtrNext
```

**回收逻辑的关键步骤**：

1. `freeReq` 信号表示第 i 条提交指令是否需要释放一个旧 PR
2. `freePhyReg` 表示要释放的旧 PR 编号
3. 释放的 PR 被写入 Freelist 尾部（`freeList(i) := freePhyReg`）
4. tail 指针前移

**那引用计数在哪里？** 引用计数的核心逻辑实际上在 `RenameTable.scala` 的 `need_free` 判断中：

```scala
// 来自 RenameTable.scala L170-L174
for (((old, free), i) <- (old_pdest zip need_free).zipWithIndex) {
  val hasDuplicate = old_pdest.take(i).map(_ === old)
  val blockedByDup = if (i == 0) false.B else VecInit(hasDuplicate).asUInt.orR
  free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
}
```

**这段代码的引用计数语义**：

* `VecInit(arch_table.map(_ =/= old)).asUInt.andR`：检查 arch\_table 中**是否还有任何映射指向旧 PR**
* 如果还有映射指向旧 PR（引用计数 > 0），则 `need_free = false`，不释放
* 如果没有任何映射指向旧 PR（引用计数 = 0），则 `need_free = true`，可以安全释放
* `blockedByDup` 防止同一批提交中重复释放同一个 PR

> 类比：这就像共享文档的权限管理——只要还有人引用这个文档（arch\_table中还有映射），就不能删除。只有所有人都关掉了（没有映射指向了），才能安全删除。

### 3.5.5 MEFreeList vs StdFreeList 对比

| **维度** | **MEFreeList（整数）** | **StdFreeList（浮点/向量）** |
| --- | --- | --- |
| **Move 消除** | ✅ 支持 | ❌ 不支持 |
| **回收判断** | 引用计数：arch\_table 中无映射指向时才释放 | 直接释放：提交后旧 PR 直接纳回 |
| **archAlloc** | 排除 isMove 指令（不移动架构头指针） | 包含所有写寄存器指令 |
| **PR 共享** | 允许一个 PR 被多个 AR 指向 | 一个 PR 只被一个 AR 指向 |
| **硬件开销** | 较高（需遍历 arch\_table 判断引用） | 较低（直接指针操作） |
| **适用场景** | 高频 Move 指令的整数寄存器 | Move 频率低的浮点/向量寄存器 |

:::warning
**新手建议**：MEFreeList 和 StdFreeList 的核心区别就一句话：**MEFreeList 支持 Move 消除，允许 PR 共享，因此回收时需要检查引用；StdFreeList 不支持 Move 消除，PR 不共享，回收时直接放回**。理解了这一点，其他细节都是推论。

:::

***

## 3.6 Move 消除在提交流程中的特殊处理

### 3.6.1 提交时 archAlloc 的跳过

我们在 3.3.2 节已经看到了关键代码：

```scala
// 来自 Rename.scala L185-L187
commit.archAlloc := io.rabCommits.commitValid zip io.rabCommits.info map {
  case (valid, info) => valid && info.rfWen && !info.isMove
}
```

**archAlloc 的作用**：控制 Freelist 的**架构头指针**（archHeadPtr）前移。架构头指针标记了"哪些 PR 已经被正式提交、不再是 Freelist 中的空闲资源"。

对于 Move 消除指令：

* 没有从 Freelist 中分配新 PR
* 不需要移动架构头指针
* `archAlloc = false`

```scala
// 来自 MEFreeList.scala L59-L64
val archAlloc = io.commit.archAlloc
val numArchAllocate = PopCount(archAlloc)
val archHeadPtrNew  = archHeadPtr + numArchAllocate
val archHeadPtrNext = Mux(doCommit, archHeadPtrNew, archHeadPtr)
archHeadPtr := archHeadPtrNext
```

**如果错误地把 Move 消除指令计入 archAlloc**，archHeadPtr 会多前移一位，导致 Freelist 认为"多分配了一个 PR"，长期运行后 Freelist 中的可用 PR 会越来越少，最终耗尽——这是一个致命的 bug。

### 3.6.2 提交时旧 PR 的回收

Move 消除指令提交时，仍然需要处理**旧映射的回收**。虽然 Move 消除没有分配新 PR，但它修改了目的寄存器的映射——x10 原来指向 PR30，现在指向 PR20。PR30 是否可以回收？

答案取决于 PR30 是否还有其他架构寄存器在引用它。这个判断在 `RenameTable.scala` 的 `need_free` 逻辑中完成（见 3.5.4 节）。

**示例**：

```plain
// 初始：x5 → PR20, x10 → PR30, x11 → PR30（假设x11也指向PR30）
mv x10, x5       // Move消除：x10 → PR20
// 提交时：PR30 仍被 x11 引用 → need_free = false → 不回收

// 后续：add x11, x1, x2  → x11 → PR40
// 提交时：PR30 不再被任何AR引用 → need_free = true → 回收PR30
```

***

## 3.7 Move 消除在回退/恢复流程中的特殊处理

### 3.7.1 Redirect 冲刷时的处理

当流水线发生 Redirect（分支预测错误、异常等）时，需要回退投机状态。Move 消除指令在回退时的处理与普通指令一致——因为快照和 arch\_table 的恢复逻辑不关心 PR 是新分配的还是共享的，只需要恢复正确的映射关系即可。

**关键点**：Move 消除指令在 `spec_table` 中写入的映射（x10 → PR20）会被快照保存。Redirect 时，根据快照恢复映射表即可，无需特殊处理。

### 3.7.2 Walk 回退时的处理

当 ROB/RAB 需要逐条回退（Walk）时，Move 消除指令也需要特殊处理。在 MEFreeList 中，Walk 模式下头指针的回退逻辑如下：

```scala
// 来自 MEFreeList.scala L34-L36
val doWalkRename = io.walk && io.doAllocate && !io.redirect
val doNormalRename = io.canAllocate && io.doAllocate && !io.redirect
val doRename = doWalkRename || doNormalRename
```

```scala
// 来自 MEFreeList.scala L67-L73
val numAllocate = Mux(io.walk, PopCount(io.walkReq), PopCount(io.allocateReq))
val headPtrNew   = Mux(lastCycleRedirect, redirectedHeadPtr, headPtr + numAllocate)
val headPtrOHNew = Mux(lastCycleRedirect, redirectedHeadPtrOH, headPtrOHVec(numAllocate))
val headPtrNext   = Mux(doRename, headPtrNew, headPtr)
val headPtrOHNext = Mux(doRename, headPtrOHNew, headPtrOH)
```

**Walk 模式**：当 `io.walk` 为真时，MEFreeList 使用 `walkReq` 而非 `allocateReq` 来计算头指针移动量。Move 消除指令在 Walk 时同样不需要移动头指针（因为分配时就没移动过），所以 `walkReq` 中 Move 消除指令对应的位也应该为 false。

***

## 3.8 Move 消除的正确性验证

### 3.8.1 源码中的 Debug 校验

香山在 MEFreeList 中内置了一个精巧的**不变量校验**，用于在仿真时验证物理寄存器的"守恒律"：

```scala
// 来自 MEFreeList.scala L99-L107
if(backendParams.debugEn){
  val debugArchHeadPtr = RegNext(RegNext(archHeadPtr, FreeListPtr(false, 0)), FreeListPtr(false, 0))
  val debugArchRAT = RegNext(RegNext(io.debug_rat.get, VecInit(Seq.fill(32)(0.U(PhyRegIdxWidth.W)))), VecInit(Seq.fill(32)(0.U(PhyRegIdxWidth.W))))
  val debugUniqPR = Seq.tabulate(32)(i => i match {
    case 0 => true.B
    case _ => !debugArchRAT.take(i).map(_ === debugArchRAT(i)).reduce(_ || _)
  })
  XSError(distanceBetween(tailPtr, debugArchHeadPtr) +& PopCount(debugUniqPR) =/= size.U,
    "Integer physical register should be in either arch RAT or arch free list\n")
}
```

**这个校验的逻辑**：

1. `debugArchRAT`：延迟两拍后的架构映射表（确保时序稳定）
2. `debugUniqPR`：统计 arch\_table 中**不重复的** PR 数量（处理 Move 消除导致的多对一映射）
3. **不变量**：`Freelist中的PR数量 + arch_table中不重复的PR数量 = 总PR数量`

> 类比：就像银行的对账——所有钱要么在金库里（Freelist），要么在客户账户里（arch\_table）。Move 消除让多个客户可以共享一个账户，所以统计"客户账户"时要**去重**。

如果这个不变量被打破，说明物理寄存器管理出了 bug（比如 Move 消除指令错误地触发了 archAlloc，或者引用计数判断有误），仿真器会立即报错。

### 3.8.2 debugUniqPR 的去重逻辑

```scala
// 来自 MEFreeList.scala L102-L105
val debugUniqPR = Seq.tabulate(32)(i => i match {
  case 0 => true.B   // x0 对应的 PR0 始终计入
  case _ => !debugArchRAT.take(i).map(_ === debugArchRAT(i)).reduce(_ || _)
})
```

**去重算法**：对于 arch\_table 中的第 i 个架构寄存器，检查它指向的 PR 是否与前面的架构寄存器指向的 PR 重复。如果重复，则不计入 `debugUniqPR`（因为已经在前面的架构寄存器中统计过了）。

这正是 Move 消除带来的"多对一映射"的直接体现——x5 和 x10 可能指向同一个 PR20，但 PR20 只应该被计数一次。

***

## 3.9 Move 消除的性能收益分析

### 3.9.1 直接收益

| **收益维度** | **具体效果** | **量化估算** |
| --- | --- | --- |
| **物理寄存器节省** | 每个 Move 消除少占用 1 个 PR | 5%~10% 的 PR 资源节省 |
| **ALU 带宽释放** | Move 指令不经过 ALU 执行 | 空出 5%~10% 的 ALU 周期 |
| **延迟降低** | Move 消除零延迟（不经过 Issue→Execute→Writeback） | 关键路径上节省 3~5 个时钟周期 |
| **ROB 周转加速** | Move 指令无需等待执行完成即可提交 | 减少ROB占用时间，提升吞吐 |

### 3.9.2 间接收益

Move 消除还有更深远的影响——**减少 Freelist 压力**。在不消除的场景下，每条 Move 指令消耗一个 PR，直到提交后旧 PR 才能回收。消除后，这些 PR 从未被占用，Freelist 中始终有更多可用资源，降低了流水线因 Freelist 为空而停顿的概率。

```scala
// 来自 MEFreeList.scala L93-L97
val freeRegCnt = Mux(doWalkRename && !lastCycleRedirect, distanceBetween(tailPtrNext, headPtr) - PopCount(io.walkReq),
                     Mux(doNormalRename,                     distanceBetween(tailPtrNext, headPtr) - PopCount(io.allocateReq),
                                                             distanceBetween(tailPtrNext, headPtr)))
io.canAllocate := freeRegCntReg >= RenameWidth.U
```

`canAllocate` 信号判断 Freelist 是否有足够的空闲 PR 供当前周期所有指令分配。Move 消除指令不计入 `allocateReq`，因此 `PopCount(io.allocateReq)` 更小，Freelist 更不容易触发"不够分配"的停顿。

***

## 3.10 Move 消除的局限性与注意事项

### 3.10.1 只适用于整数寄存器

香山的 Move 消除**仅限于整数寄存器**。浮点和向量寄存器的 Freelist 使用 StdFreeList，不支持 Move 消除。原因有三：

1. **频率差异**：浮点/向量 Move 指令出现频率远低于整数
2. **硬件开销**：引用计数需要遍历 arch\_table，对 32 项的浮点/向量映射表来说额外开销不小
3. **精度问题**：浮点 Move（如 `fmv.s`）在某些情况下涉及 NaN 传播等特殊语义，直接映射可能改变可见行为

### 3.10.2 不能消除的"类 Move"指令

并非所有看起来像 Move 的指令都能消除：

| **指令** | **能否消除** | **原因** |
| --- | --- | --- |
| `addi rd, rs, 0` | ✅ | 结果确实等于 rs |
| `add rd, rs, x0` | ✅ | x0 恒为零，结果等于 rs |
| `or rd, rs, x0` | ✅ | rs OR 0 = rs |
| `fmv.s rd, rs` | ❌（香山） | 浮点寄存器不支持 Move 消除 |
| `addi rd, rs, 1` | ❌ | 立即数非零，结果不等于 rs |
| `add rd, x0, rs` | ✅ | x0 + rs = rs |

### 3.10.3 引用计数的时序挑战

MEFreeList 的 `need_free` 判断需要遍历整个 arch\_table（32项），这是一个**组合逻辑较长的操作**。在高频率设计中，这可能成为时序瓶颈。香山通过以下方式缓解：

* `need_free` 判断在 RenameTable 中完成，与 Freelist 分离，降低单模块复杂度
* 使用 `old_pdest` 寄存器打一拍，避免组合逻辑路径过长

***

## 3.11 新手实战：追踪一条 Move 指令的消除过程

### 3.11.1 实战任务：追踪 `mv x10, x5` 的消除流程

**步骤1：译码阶段——识别 Move**

译码器识别出 `addi x10, x5, 0` 是一条 Move 指令，在微操作中设置 `isMove = true`。

**步骤2：重命名阶段——读源映射**

```scala
// 重命名模块读取 x5 的物理映射
// 假设 x5 → PR20，则 psrc(0) = PR20
```

**步骤3：重命名阶段——跳过 Freelist 分配**

由于 `isMove = true`，重命名模块**不向 intFreeList 发起 allocateReq**，直接设置：

```plain
pdest = psrc(0) = PR20
```

**步骤4：更新映射表**

```scala
// spec_table 更新：x10 → PR20（原来 x10 → PR30）
// 此时 PR20 同时被 x5 和 x10 引用
// PR30 成为 old_pdest，等待回收判断
```

**步骤5：输出重命名结果**

微操作通过 `out` 端口输出，携带 `pdest=PR20, isMove=true`。后续的 Issue/Dispatch 模块看到 `isMove=true`，知道这条指令不需要派发到 ALU 执行。

**步骤6：提交——跳过 archAlloc**

```scala
// archAlloc = valid && rfWen && !isMove = true && true && false = false
// archHeadPtr 不前移
```

**步骤7：回收旧映射**

检查 PR30 是否还有其他架构寄存器引用。如果没有，`need_free = true`，PR30 被放回 Freelist。

### 3.11.2 对比实验：不消除时的执行路径

同样的 `mv x10, x5`，如果不做 Move 消除：

```plain
1. Freelist 分配 PR50
2. x10 → PR50（新映射）
3. Issue Queue 等待 Issue
4. ALU 执行：PR50 = PR20 + 0
5. 写回 PR50
6. 提交：archHeadPtr 前移，PR30 回收
```

**消除节省了**：1个PR的占用 + 整个 Issue→Execute→Writeback 的延迟 + ALU 的一个执行周期。

***

## 3.12 常见问题与排错指南

### Q1：Move 消除后，修改 x5 会不会影响 x10？

**不会**。Move 消除发生在重命名阶段，之后如果有任何指令写 x5，重命名模块会为 x5 分配一个新的 PR（比如 PR40），映射变为 x5→PR40, x10→PR20。此时 x10 和 x5 不再共享 PR20，修改 x5（即 PR40）不会影响 x10（即 PR20）。

### Q2：如果 Freelist 为空，Move 消除指令还会停顿吗？

**不会**。Move 消除指令不向 Freelist 请求分配，因此 Freelist 是否为空对它没有影响。这也是 Move 消除的一个额外好处——在 Freelist 压力大的场景下，Move 指令仍能顺利通过重命名阶段。

### Q3：如何验证 Move 消除是否正确工作？

香山的 MEFreeList 内置了守恒律校验（见 3.8 节）。此外，可以通过 Difftest 框架对比 RTL 仿真结果与参考模型的架构状态——Move 消除不改变架构可见状态，只改变微架构内部的映射关系。

***

## 3.13 分级学习路径指引

🟢 **入门必掌握**：Move 消除的核心概念（isMove → 不分配新PR → 直接映射源PR）、为什么需要引用计数

🔵 **进阶需理解**：MEFreeList 的分配/回收源码实现、archAlloc 跳过逻辑、`need_free` 的引用判断

🟣 **精通深挖**：MEFreeList 与 StdFreeList 的架构差异、引用计数的时序优化、Walk 回退中 Move 消除的处理、守恒律校验的完整逻辑

***

## 3.14 本章总结

✅ **核心知识点回顾**：

* Move 指令消除的核心：**isMove 指令不分配新 PR，直接让目的 AR 映射到源 PR**，省资源、省延迟
* Move 消除导致**一个 PR 可被多个 AR 共享**，因此需要**引用计数**来判断 PR 何时可以安全回收
* 香山为整数寄存器使用 **MEFreeList**（支持 Move 消除），浮点/向量使用 **StdFreeList**（不支持）
* MEFreeList 的 `archAlloc` 信号**排除 isMove 指令**，避免 Freelist 架构头指针错误前移
* 回收判断的关键：<code>need_free = arch_table 中无映射指向旧PR && 无同批重复释放</code>
* 香山内置**守恒律校验**：Freelist 中的 PR + arch\_table 中去重后的 PR = 总 PR 数量
* Move 消除仅适用于整数寄存器，浮点/向量因频率和语义原因不支持

🎉 **恭喜你完成了 Move 指令消除的学习！** 这是寄存器重命名之后最精妙的微架构优化之一——看似简单的"省一步"，背后却牵动了 Freelist 架构、引用计数、提交逻辑等多个模块的协同设计。掌握了 Move 消除，你就理解了高性能处理器中"零开销数据搬运"的实现奥秘。接下来，让我们继续探索香山流水线中更多精彩的微架构优化。


> 更新: 2026-06-02 10:56:38  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/beryss0adppkq5nr>
-->

# 3. Move Elimination

# 3. Move Elimination

> If you have completed the register-renaming chapter, you already know that when an instruction writes a destination register, the rename stage normally allocates a new physical register (PR) from the Freelist and maps the architectural register to it. But is there a case where **we do not need to allocate a new PR at all**? Yes, and it is very common. This is **Move Elimination**. Do not worry; it is even simpler than rename: it is essentially "skipping one step."

:::info
By the end of this section, you will be able to:

* 🧭 Understand the **motivation for Move Elimination**: why a move instruction does not need to actually execute
* 📋 Understand XiangShan's **complete Move Elimination mechanism**, from the `isMove` bit to the direct mapping-table update
* 🔍 Understand the reference-counting design of **MEFreeList** and its key differences from **StdFreeList**
* ⚡ Understand the special handling of Move Elimination during **commit, rollback, and snapshot recovery**
* 🗺️ Follow a complete learning path from source code to practice

:::

***

## 3.1 Why is Move Elimination needed?

### 3.1.1 A move instruction looks simple but wastes resources

The RISC-V ISA has no standalone instruction named `mv`. `mv x10, x5` is a pseudoinstruction for `addi x10, x5, 0`: add zero to x5 and write the result to x10. In the normal rename flow, it takes this path:

```plain
mv x10, x5  (actually addi x10, x5, 0)

Normal rename flow:
1. Allocate a new PR from the Freelist (for example, PR50)
2. Update the mapping table: x10 → PR50
3. Send PR50's number to the reservation station and Issue Queue
4. Wait for issue → dispatch to the ALU
5. The ALU adds zero: PR50 = PR_x5 + 0
6. Write back PR50
7. Reclaim the old x10 mapping after commit

Total cost: 1 physical register + 1 ROB entry + 1 Issue Queue entry + one ALU execution cycle
```

But notice that **the result is exactly the value of PR_x5**. PR50 stores PR_x5 plus zero. Why not map x10 directly to PR_x5 and remove all those intermediate steps?

> The analogy is copying a file from cabinet A to cabinet B when the contents are identical. You can put a label on cabinet B saying "same contents as slot X in cabinet A" instead of making another copy. Move Elimination does exactly this.

### 3.1.2 How common are move instructions?

Move instructions appear very frequently in programs:

| **Scenario** | **Typical move** | **Frequency** |
| --- | --- | --- |
| Passing function-call arguments | `mv a0, x5` | Very high |
| Saving/restoring register values | `mv s0, x10` | High |
| Compiler register allocation | `mv x10, x5` | High |
| Conditional-move pseudoinstruction | `seqz x10, x5` (= `sltiu x10, x5, 1`) | Medium |

Move-like instructions can account for **5%–10%** of a typical workload. Eliminating all of them saves 5%–10% of ALU execution bandwidth and physical-register resources, a meaningful gain in a high-performance processor.

:::color4
**❤** **Core idea:** Move Elimination means that **when the result is identical to the source operand, do not allocate a new physical register; map the destination architectural register directly to the source physical register**. It saves a PR, an execution-unit slot, and latency at once.

:::

***

## 3.2 The Move Elimination architecture at a glance

### 3.2.1 Before versus after elimination

```plain
╔══════════════════════════════════════════════════════════════════╗
║             Two paths for mv x10, x5                             ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  ❌ Normal rename (no elimination):                              ║
║                                                                  ║
║  x5 ──→ PR20       x10 ──→ PR30 (old mapping)                   ║
║       │                   │                                      ║
║       │    Freelist ──→ allocate PR50                            ║
║       │                   │                                      ║
║       │         x10 ──→ PR50 (new mapping)                       ║
║       │                   │                                      ║
║       └──→ ALU: PR50 = PR20 + 0 ──→ write back PR50              ║
║                                                                  ║
║  Cost: 1 PR + ALU execution + Issue/ROB resources                ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  ✅ Move Elimination:                                             ║
║                                                                  ║
║  x5 ──→ PR20       x10 ──→ PR30 (old mapping)                   ║
║       │                                                          ║
║       └───── direct mapping ────→ x10 ──→ PR20 (shared source PR)║
║                                                                  ║
║  No new PR, no ALU, zero execution latency                       ║
║                                                                  ║
║  Cost: 0 PR + 0 execution cycles + less Issue/ROB pressure        ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝
```

> **Reading the diagram**: the key is "do not allocate a new PR; reuse the source PR directly." The same physical register PR20 is now pointed to by both x5 and x10. This is why **reference counting** is required.

### 3.2.2 XiangShan's complete Move Elimination flow

```plain
┌────────────────── Move Elimination flow ──────────────────┐
│                                                          │
│  ① Decode stage                                           │
│     Decode recognizes the move instruction                 │
│     Set the isMove = true flag                             │
│              │                                           │
│              ↓                                           │
│  ② Rename stage (the core!)                               │
│     ┌──────────────────────────────┐                     │
│     │ if (isMove):                 │                     │
│     │   pdest = psrc (source PR)   │  ← no new PR         │
│     │   do not request Freelist    │                     │
│     │ else:                        │                     │
│     │   pdest = Freelist.allocate()│  ← normal allocate  │
│     └──────────────────────────────┘                     │
│              │                                           │
│              ↓                                           │
│  ③ Update the mapping table                              │
│     spec_table[ldest] = pdest                             │
│     (for elimination, pdest is the source PR; two ARs share it)
│              │                                           │
│              ↓                                           │
│  ④ Commit stage                                           │
│     A move does not update archHeadPtr at commit            │
│     (no new PR was allocated, so the Freelist head need not move)
│              │                                           │
│              ↓                                           │
│  ⑤ Reclamation stage                                      │
│     When x10's old PR30 is no longer referenced by any AR  │
│     reference count reaches zero → return PR30 to Freelist │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

:::color4
**❤** **Beginner tip:** remember the core operation: **for an `isMove` instruction, `pdest` directly equals `psrc`; no new PR is allocated**. Reference counting, skipping `archAlloc` at commit, and the reclamation check all follow from this operation.

:::

***

## 3.3 Identifying and propagating the `isMove` flag

### 3.3.1 How does decode recognize a move?

Move recognition happens in the **decode stage**. XiangShan sets an `isMove` bit in the micro-operation so that rename can distinguish an ordinary instruction from an eliminable move.

In RISC-V, the following forms can be treated as moves:

| **Pseudoinstruction** | **Actual encoding** | **Move Elimination condition** |
| --- | --- | --- |
| `mv rd, rs` | `addi rd, rs, 0` | ADDI immediate is zero |
| `fmv.s rd, rs` | `fsgnj.s rd, rs, rs` | Floating-point move (FSGNJ source 1 equals source 2) |
| - | `add rd, rs, x0` | ADD source 2 is x0 (x0 is always zero) |
| - | `or rd, rs, x0` | OR source 2 is x0 |
| - | `xor rd, rs, x0` | XOR source 2 is x0 |

> Decode is like a hospital triage desk: when it sees an instruction that only moves data, it attaches a "green channel" label (`isMove=true`) so later stages know to apply the special handling.

### 3.3.2 Propagation of `isMove` through the pipeline

Once generated by decode, `isMove` travels through the pipeline with the micro-operation. The commit interface in `Rename.scala` shows the flag being passed to ROB/RAB commit logic:

```scala
// From Rename.scala L182-L188
intFreeList.io.commit match {
  case commit =>
    commit.doCommit := io.rabCommits.isCommit
    commit.archAlloc := io.rabCommits.commitValid zip io.rabCommits.info map {
      case (valid, info) => valid && info.rfWen && !info.isMove
      //                                  ^^^^^^^^^^^^^ important: a move does not trigger archAlloc
    }
}
```

**Meaning of this code:** an instruction triggers `archAlloc` (architectural allocation) in the integer Freelist only when all three conditions hold:

1. `valid`: the instruction is valid.
2. `rfWen`: the instruction writes an integer register.
3. `!info.isMove`: it is **not** a move-eliminated instruction.

A move does not trigger `archAlloc` because it did not allocate a new PR from the Freelist, so commit does not need to advance the Freelist's architectural head pointer.

***

## 3.4 Core Move Elimination logic in rename

### 3.4.1 The elimination decision in the source

The core of Move Elimination is in rename. When an instruction is marked `isMove`, rename **skips Freelist allocation** and sets the destination physical-register number directly to the source operand's physical-register number.

In `Rename.scala`, the output path makes the complete behavior clear:

```scala
// From Rename.scala L83-L84
val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
```

`RenameOutUop` contains `pdest` (destination PR number) and `psrc` (source PR number). For a move-eliminated instruction:

| **Field** | **Ordinary instruction** | **Move-eliminated instruction** |
| --- | --- | --- |
| `pdest` | New PR allocated by the Freelist | Equals `psrc(0)` (source operand PR) |
| `psrc` | Source PR looked up in the mapping table | Source PR looked up in the mapping table |
| `isMove` | false | true |
| `rfWen` | true | true (it still writes a register, but does not use the ALU) |
| Freelist request | `allocateReq = true` | `allocateReq = false` (no allocation) |

### 3.4.2 A shared physical register after elimination

The most important architectural change after Move Elimination is that **one physical register can be pointed to by several architectural registers at the same time**.

```plain
// Initial state: x5 → PR20, x10 → PR30
mv x10, x5

// After elimination: x5 → PR20, x10 → PR20
// PR20 is referenced by both architectural registers x5 and x10!
```

This is why integer registers use **MEFreeList (a reference-counted Freelist)** instead of `StdFreeList`: when multiple ARs share a PR, it is safe to reclaim that PR only after **all references disappear**.

:::warning
**Key point:** Move Elimination applies only to the **integer-register** Freelist (`intFreeList = MEFreeList`). The floating-point and vector Freelist instances (`fpFreeList` and `vecFreeList`) use `StdFreeList` and **do not support Move Elimination**. Their move frequency is lower, so the hardware cost of reference counting is not worthwhile.

:::

***

## 3.5 MEFreeList: a reference-counted free list

### 3.5.1 Why do integer registers need MEFreeList?

The previous register-renaming chapter introduced `StdFreeList` allocation and reclamation: after an instruction commits, its old PR is returned directly to the Freelist tail. This is correct **without Move Elimination**, because each architectural register points to only one physical register at any instant, so an old PR is no longer needed.

Move Elimination breaks that assumption. Consider this sequence:

```plain
// Initial state: x5 → PR20, x10 → PR30
mv x10, x5        // elimination: x10 → PR20; PR20 is shared by x5 and x10
add x5, x1, x2    // rename x5 → PR40; x5 no longer points to PR20
// PR20 is still referenced by x10 and cannot be reclaimed!

// PR20 becomes reclaimable only after x10 is renamed to another PR
add x10, x3, x4   // rename x10 → PR50; x10 no longer points to PR20
// PR20 can now be reclaimed
```

**The core problem:** `StdFreeList` only checks whether an old PR remains in `arch_table`; it cannot handle several ARs pointing to one PR. MEFreeList solves this with **reference counting**.

### 3.5.2 MEFreeList source walkthrough

```scala
// From MEFreeList.scala L27-L30
class MEFreeList(size: Int, commitWidth: Int)(implicit p: Parameters) extends BaseFreeList(size, commitWidth) with HasPerfEvents {
  val freeList = RegInit(VecInit(
    // originally {1, 2, ..., size - 1} are free. Register 0-31 are mapped to x0.
    Seq.tabulate(size - 1)(i => (i + 1).U(PhyRegIdxWidth.W)) :+ 0.U(PhyRegIdxWidth.W)))
```

**Initialization:** the initial free queue contains PR1 through PR(size-1) and PR0. PR0 is special: x0 is always zero, so all integer registers initially map to PR0, but PR0 still waits at the end of the Freelist for allocation.

### 3.5.3 MEFreeList allocation

```scala
// From MEFreeList.scala L53-L57
val phyRegCandidates = Mux1H(headPtrOHVec(0), freeListVec)
for (i <- 0 until RenameWidth) {
  // enqueue instr, is move elimination
  io.allocatePhyReg(i) := phyRegCandidates(PopCount(io.allocateReq.take(i)))
}
```

**Important allocation details:**

1. `phyRegCandidates` uses the one-hot `headPtr` encoding to select the currently allocatable PR list from `freeListVec`.
2. For instruction *i*, skip PRs already allocated to earlier instructions (`PopCount(io.allocateReq.take(i))`) and select the next free PR.
3. Note the comment **"is move elimination"**: if instruction *i* is eliminated (`allocateReq(i) = false`), `PopCount` automatically skips it; it does not participate in allocation.

> Think of people lining up to receive ticket numbers: a move-eliminated instruction says "I do not need a ticket," so it does not consume a number.

### 3.5.4 MEFreeList reclamation — the reference-counting core

MEFreeList reclamation differs fundamentally from `StdFreeList`. It does not simply return an old PR when an instruction commits. Instead, `freeReq` and `freePhyReg`, together with the reference check, determine whether a PR can be released safely:

```scala
// From MEFreeList.scala L78-L91
val freePtr = VecInit(Seq.tabulate(commitWidth)(i => tailPtr + PopCount(io.freeReq.take(i))))
for (i <- 0 until size) {
  val freeReqOH = VecInit(io.freeReq.zipWithIndex.map { case (w, idx) =>
    w && freePtr(idx).value === i.U
  })
  val freePhyReg = Mux1H(freeReqOH, io.freePhyReg)
  when(freeReqOH.asUInt.orR) {
    freeList(i) := freePhyReg
  }
}
// update tail pointer
val tailPtrNext = tailPtr + PopCount(io.freeReq)
tailPtr := tailPtrNext
```

**Key reclamation steps:**

1. `freeReq` says whether the *i*th committing instruction needs to release an old PR.
2. `freePhyReg` is the number of the old PR to release.
3. The released PR is written at the Freelist tail (`freeList(i) := freePhyReg`).
4. The tail pointer advances.

**Where is the reference count?** Its core check is in `RenameTable.scala`, in the `need_free` decision:

```scala
// From RenameTable.scala L170-L174
for (((old, free), i) <- (old_pdest zip need_free).zipWithIndex) {
  val hasDuplicate = old_pdest.take(i).map(_ === old)
  val blockedByDup = if (i == 0) false.B else VecInit(hasDuplicate).asUInt.orR
  free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
}
```

**Reference-counting semantics:**

* `VecInit(arch_table.map(_ =/= old)).asUInt.andR` checks whether **any mapping in `arch_table` still points to the old PR**.
* If a mapping remains (reference count > 0), `need_free = false` and the PR is retained.
* If no mapping remains (reference count = 0), `need_free = true` and the PR can be released.
* `blockedByDup` prevents the same PR from being released twice in one commit batch.

> This is like access control for a shared document: as long as someone still references it, it cannot be deleted. It is safe only after everyone has closed it.

### 3.5.5 MEFreeList versus StdFreeList

| **Dimension** | **MEFreeList (integer)** | **StdFreeList (floating point/vector)** |
| --- | --- | --- |
| **Move Elimination** | ✅ Supported | ❌ Not supported |
| **Reclamation test** | Reference count: release only when no `arch_table` mapping points to the PR | Direct release: return the old PR after commit |
| **archAlloc** | Excludes `isMove` instructions (does not advance the architectural head) | Includes every register-writing instruction |
| **PR sharing** | One PR may be pointed to by several ARs | Each PR is pointed to by one AR |
| **Hardware cost** | Higher (must scan `arch_table` for references) | Lower (direct pointer operations) |
| **Use case** | Integer registers with frequent moves | Floating-point/vector registers with fewer moves |

:::warning
**Beginner tip:** the difference can be stated in one sentence: **MEFreeList supports Move Elimination and permits PR sharing, so reclamation checks references; StdFreeList does not support Move Elimination, PRs are not shared, and reclamation returns them directly**. The remaining details follow from this distinction.

:::

***

## 3.6 Special handling of Move Elimination at commit

### 3.6.1 Skipping `archAlloc` at commit

We saw the key code in Section 3.3.2:

```scala
// From Rename.scala L185-L187
commit.archAlloc := io.rabCommits.commitValid zip io.rabCommits.info map {
  case (valid, info) => valid && info.rfWen && !info.isMove
}
```

**Role of `archAlloc`:** it advances the Freelist's **architectural head pointer** (`archHeadPtr`). The architectural head marks PRs that have been formally committed and are no longer free resources in the Freelist.

For a move-eliminated instruction:

* No new PR was allocated from the Freelist.
* The architectural head pointer must not move.
* `archAlloc = false`.

```scala
// From MEFreeList.scala L59-L64
val archAlloc = io.commit.archAlloc
val numArchAllocate = PopCount(archAlloc)
val archHeadPtrNew  = archHeadPtr + numArchAllocate
val archHeadPtrNext = Mux(doCommit, archHeadPtrNew, archHeadPtr)
archHeadPtr := archHeadPtrNext
```

**If a move is incorrectly counted in `archAlloc`**, `archHeadPtr` advances one extra position. The Freelist then believes that one extra PR was allocated; over a long run, available PRs shrink until the Freelist is exhausted. This is a fatal bug.

### 3.6.2 Reclaiming the old PR at commit

A move still needs **old-mapping reclamation** at commit. Although it allocated no new PR, it changed the destination mapping: x10 used to point to PR30 and now points to PR20. Can PR30 be reclaimed?

The answer depends on whether another architectural register still references PR30. `RenameTable.scala` performs this check through `need_free` (see Section 3.5.4).

**Example:**

```plain
// Initial: x5 → PR20, x10 → PR30, x11 → PR30 (assume x11 also points to PR30)
mv x10, x5       // elimination: x10 → PR20
// At commit: PR30 is still referenced by x11 → need_free = false → keep it

// Later: add x11, x1, x2 → x11 → PR40
// At commit: no AR references PR30 → need_free = true → reclaim PR30
```

***

## 3.7 Special handling during rollback and recovery

### 3.7.1 Handling a Redirect flush

When a Redirect occurs (branch misprediction, exception, and so on), speculative state must be rolled back. Move-eliminated instructions are handled like ordinary instructions during this rollback: snapshot and `arch_table` recovery do not care whether a PR was newly allocated or shared; they only need to restore the correct mapping.

**Key point:** the mapping written by a move in `spec_table` (x10 → PR20) is included in the snapshot. On Redirect, restore the mapping table from the snapshot; no extra handling is required.

### 3.7.2 Handling a Walk rollback

When the ROB/RAB rolls back one instruction at a time (Walk), move elimination also needs to be accounted for. In MEFreeList, the head-pointer logic in Walk mode is:

```scala
// From MEFreeList.scala L34-L36
val doWalkRename = io.walk && io.doAllocate && !io.redirect
val doNormalRename = io.canAllocate && io.doAllocate && !io.redirect
val doRename = doWalkRename || doNormalRename
```

```scala
// From MEFreeList.scala L67-L73
val numAllocate = Mux(io.walk, PopCount(io.walkReq), PopCount(io.allocateReq))
val headPtrNew   = Mux(lastCycleRedirect, redirectedHeadPtr, headPtr + numAllocate)
val headPtrOHNew = Mux(lastCycleRedirect, redirectedHeadPtrOH, headPtrOHVec(numAllocate))
val headPtrNext   = Mux(doRename, headPtrNew, headPtr)
val headPtrOHNext = Mux(doRename, headPtrOHNew, headPtrOH)
```

**Walk mode:** when `io.walk` is true, MEFreeList uses `walkReq`, not `allocateReq`, to compute head-pointer movement. A move also does not move the head during Walk (it never moved it during allocation), so the corresponding bit in `walkReq` must be false.

***

## 3.8 Verifying Move Elimination correctness

### 3.8.1 The source-level debug check

MEFreeList contains a precise **invariant check** that verifies the conservation of physical registers during simulation:

```scala
// From MEFreeList.scala L99-L107
if(backendParams.debugEn){
  val debugArchHeadPtr = RegNext(RegNext(archHeadPtr, FreeListPtr(false, 0)), FreeListPtr(false, 0))
  val debugArchRAT = RegNext(RegNext(io.debug_rat.get, VecInit(Seq.fill(32)(0.U(PhyRegIdxWidth.W)))), VecInit(Seq.fill(32)(0.U(PhyRegIdxWidth.W))))
  val debugUniqPR = Seq.tabulate(32)(i => i match {
    case 0 => true.B
    case _ => !debugArchRAT.take(i).map(_ === debugArchRAT(i)).reduce(_ || _)
  })
  XSError(distanceBetween(tailPtr, debugArchHeadPtr) +& PopCount(debugUniqPR) =/= size.U,
    "Integer physical register should be in either arch RAT or arch free list\n")
}
```

**How the check works:**

1. `debugArchRAT` is the architectural mapping table delayed by two cycles, providing stable timing.
2. `debugUniqPR` counts **unique** PRs in `arch_table`, handling the many-to-one mappings introduced by Move Elimination.
3. **Invariant:** `number of PRs in the Freelist + number of unique PRs in arch_table = total number of PRs`.

> It is like reconciling a bank account: every unit of money is either in the vault (Freelist) or in a customer account (`arch_table`). Because Move Elimination lets several customers share one account, customer accounts must be counted uniquely.

If this invariant is violated, physical-register management is incorrect (for example, a move incorrectly triggered `archAlloc`, or reference counting is wrong), and the simulator reports an error immediately.

### 3.8.2 The deduplication logic in `debugUniqPR`

```scala
// From MEFreeList.scala L102-L105
val debugUniqPR = Seq.tabulate(32)(i => i match {
  case 0 => true.B   // PR0 for x0 is always counted
  case _ => !debugArchRAT.take(i).map(_ === debugArchRAT(i)).reduce(_ || _)
})
```

**Deduplication algorithm:** for architectural register *i*, check whether its PR is equal to a PR pointed to by an earlier architectural register. If it is a duplicate, do not count it in `debugUniqPR`; it was already counted earlier.

This is the direct result of Move Elimination's many-to-one mapping: x5 and x10 may both point to PR20, but PR20 must be counted only once.

***

## 3.9 Performance benefits of Move Elimination

### 3.9.1 Direct benefits

| **Benefit** | **Effect** | **Estimate** |
| --- | --- | --- |
| **Physical-register savings** | Each eliminated move uses one fewer PR | 5%–10% PR-resource savings |
| **ALU bandwidth** | A move does not execute in the ALU | 5%–10% of ALU cycles freed |
| **Lower latency** | Elimination has zero execution latency (no Issue→Execute→Writeback) | 3–5 clock cycles saved on a critical path |
| **Faster ROB turnover** | A move need not wait for execution before commit | Less ROB occupancy and higher throughput |

### 3.9.2 Indirect benefits

Move Elimination also **reduces Freelist pressure**. Without elimination, each move consumes a PR until its old PR is reclaimed at commit. With elimination, those PRs are never occupied, so more resources remain available and a Freelist-empty stall is less likely.

```scala
// From MEFreeList.scala L93-L97
val freeRegCnt = Mux(doWalkRename && !lastCycleRedirect, distanceBetween(tailPtrNext, headPtr) - PopCount(io.walkReq),
                     Mux(doNormalRename,                     distanceBetween(tailPtrNext, headPtr) - PopCount(io.allocateReq),
                                                             distanceBetween(tailPtrNext, headPtr)))
io.canAllocate := freeRegCntReg >= RenameWidth.U
```

The `canAllocate` signal checks whether the Freelist has enough free PRs for all instructions in the current cycle. A move does not count in `allocateReq`, so `PopCount(io.allocateReq)` is smaller and the Freelist is less likely to stall for insufficient allocation capacity.

***

## 3.10 Limitations and cautions

### 3.10.1 Integer registers only

XiangShan's Move Elimination **applies only to integer registers**. Floating-point and vector register Freelist instances use `StdFreeList` and do not support it. There are three reasons:

1. **Frequency:** floating-point/vector moves occur much less often than integer moves.
2. **Hardware cost:** reference counting scans `arch_table`; doing this for 32-entry floating-point/vector maps adds nontrivial overhead.
3. **Precision semantics:** floating-point moves such as `fmv.s` can involve special NaN-propagation semantics, so direct mapping could change observable behavior in some cases.

### 3.10.2 Move-like instructions that cannot be eliminated

Not every instruction that looks like a move can be eliminated:

| **Instruction** | **Eliminable?** | **Reason** |
| --- | --- | --- |
| `addi rd, rs, 0` | ✅ | The result is exactly `rs` |
| `add rd, rs, x0` | ✅ | x0 is always zero, so the result is `rs` |
| `or rd, rs, x0` | ✅ | `rs OR 0 = rs` |
| `fmv.s rd, rs` | ❌ (XiangShan) | Floating-point registers do not support Move Elimination |
| `addi rd, rs, 1` | ❌ | The immediate is nonzero, so the result differs from `rs` |
| `add rd, x0, rs` | ✅ | `x0 + rs = rs` |

### 3.10.3 Timing challenges of reference counting

The MEFreeList `need_free` check scans the entire 32-entry `arch_table`, making it a **long combinational operation**. In a high-frequency design it may become a timing bottleneck. XiangShan mitigates this in two ways:

* Perform `need_free` in `RenameTable`, separate from the Freelist, to reduce per-module complexity.
* Register `old_pdest` for one cycle to shorten the combinational path.

***

## 3.11 Hands-on exercise: follow one move through elimination

### 3.11.1 Exercise: follow `mv x10, x5`

**Step 1: Decode — recognize the move**

The decoder recognizes `addi x10, x5, 0` as a move and sets `isMove = true` in the micro-operation.

**Step 2: Rename — read the source mapping**

```scala
// The rename module reads x5's physical mapping
// Assume x5 → PR20, so psrc(0) = PR20
```

**Step 3: Rename — skip Freelist allocation**

Because `isMove = true`, rename **does not issue `allocateReq` to `intFreeList`** and directly sets:

```plain
pdest = psrc(0) = PR20
```

**Step 4: Update the mapping table**

```scala
// Update spec_table: x10 → PR20 (x10 previously → PR30)
// PR20 is now referenced by both x5 and x10
// PR30 becomes old_pdest and awaits the reclamation check
```

**Step 5: Output the renamed result**

The micro-operation leaves through the `out` port with `pdest=PR20, isMove=true`. The later Issue/Dispatch modules see `isMove=true` and know that the instruction does not need to execute in the ALU.

**Step 6: Commit — skip `archAlloc`**

```scala
// archAlloc = valid && rfWen && !isMove = true && true && false = false
// archHeadPtr does not advance
```

**Step 7: Reclaim the old mapping**

Check whether any other architectural register references PR30. If none does, `need_free = true` and PR30 returns to the Freelist.

### 3.11.2 Comparison experiment: the path without elimination

For the same `mv x10, x5`, if Move Elimination is disabled:

```plain
1. Freelist allocates PR50
2. x10 → PR50 (new mapping)
3. Wait for issue in the Issue Queue
4. ALU executes: PR50 = PR20 + 0
5. Write back PR50
6. Commit: advance archHeadPtr and reclaim PR30
```

**Elimination saves:** one PR, the entire Issue→Execute→Writeback latency, and one ALU execution cycle.

***

## 3.12 Frequently asked questions and troubleshooting

### Q1: After Move Elimination, can changing x5 affect x10?

**No.** Elimination occurs during rename. If a later instruction writes x5, rename allocates a new PR for x5 (for example PR40), producing x5→PR40 and x10→PR20. x5 and x10 no longer share PR20, so changing x5 (PR40) does not affect x10 (PR20).

### Q2: If the Freelist is empty, will a move-eliminated instruction stall?

**No.** A move does not request a Freelist allocation, so it is unaffected by whether the Freelist is empty. This is an additional benefit under high Freelist pressure: moves can still pass through rename.

### Q3: How can I verify that Move Elimination works correctly?

MEFreeList includes the conservation-law check described in Section 3.8. You can also use Difftest to compare the RTL simulation with the reference model's architectural state. Move Elimination does not change architectural state; it changes only the microarchitectural mappings.

***

## 3.13 Tiered learning path

🟢 **Beginner essentials:** the core concept (`isMove` → no new PR → direct source-PR mapping) and why reference counting is needed

🔵 **Intermediate:** MEFreeList allocation/reclamation source, the `archAlloc` skip logic, and the reference test in `need_free`

🟣 **Advanced:** architectural differences between MEFreeList and StdFreeList, timing optimization for reference counting, Move Elimination during Walk rollback, and the complete conservation-law check

***

## 3.14 Summary

✅ **Key points:**

* The core of Move Elimination: an **`isMove` instruction does not allocate a new PR; it maps the destination AR directly to the source PR**, saving resources and latency.
* Because elimination lets **multiple ARs share one PR**, reference counting is required to decide when a PR can be safely reclaimed.
* XiangShan uses **MEFreeList** (Move Elimination supported) for integer registers and **StdFreeList** (not supported) for floating-point/vector registers.
* MEFreeList's `archAlloc` signal **excludes `isMove` instructions**, preventing an incorrect advance of the Freelist architectural head.
* The key reclamation condition is `<code>need_free = no mapping in arch_table points to old PR && no duplicate release in the same batch</code>`.
* XiangShan includes a **conservation-law check**: PRs in the Freelist plus deduplicated PRs in `arch_table` equal the total PR count.
* Move Elimination applies only to integer registers; floating-point/vector registers are excluded because of frequency and semantic considerations.

🎉 **Congratulations on completing Move Elimination!** It is one of the most elegant microarchitectural optimizations after register rename. The seemingly simple act of "skipping one step" coordinates Freelist architecture, reference counting, commit logic, and several other modules. Once you understand it, you understand how high-performance processors implement zero-cost data movement. Continue exploring the other microarchitectural optimizations in the XiangShan pipeline.


> Updated: 2026-06-02 10:56:38
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/beryss0adppkq5nr>
