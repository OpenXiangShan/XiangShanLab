<!--
# 2.寄存器重命名

> 如果你是第一次接触寄存器重命名，看到"映射表"、"Freelist"、"Bypass转发"、"快照恢复"这些术语可能会感到有些复杂。别担心——寄存器重命名本质上就是一件事：给指令换一个"真名"，让它们不再因为"撞名"而互相等待。让我们一步步来，你会发现它比想象中直观得多。

:::info
通过本节学习，你将能够：

* 🧭 彻底理解寄存器重命名的**核心目的**：消除伪数据相关（WAW/WAR）
* 📋 掌握三种主流重命名架构：**ROB重命名、ARF拓展重命名、统一PRF重命名**
* 🔍 吃透重命名映射表的读写、Bypass转发、Freelist空闲链表的完整工作机制，并**读懂香山源码实现**
* 🗺️ 掌握Redirect、Snapshot快照、ROB压缩三大流水线恢复机制
* ⚡ 理解多指令并行重命名、带宽瓶颈、跨宽度缓冲队列的工程设计
* 📦 了解特殊寄存器 **VCSR.vl** 的专属重命名逻辑

:::

***

## 2.1 寄存器重命名核心原理：解决伪数据相关

### 2.1.1 为什么需要重命名？——"房间号"的故事

在学习乱序执行时，最大的性能阻碍并非真实数据依赖，而是**架构寄存器数量稀缺导致的伪相关**。很多指令之间没有真实数据交互，仅仅因为复用了同一个架构寄存器，就被流水线强行串行执行，严重压制了乱序的并发能力。

> 通俗比喻：架构寄存器如同有限的"房间号"（只有32个），物理寄存器如同海量的"真实房间"（128个）。程序只能识别有限房间号，硬件通过重命名，把同一个房间号映射到不同真实房间，避免新老指令互相挤占、强行等待。

**寄存器重命名，就是乱序处理器的"去伪依赖"核心机制**：将程序可见的少量架构寄存器（ARF），映射为硬件大量的物理寄存器（PRF），让指令真正做到"无相关则并行、乱序执行"。

### 2.1.2 三种数据相关与重命名解决方案

数据相关分为**真相关（RAW）**和**伪相关（WAW/WAR）**，寄存器重命名可彻底消除两类伪相关，优化真相关调度逻辑。

| **相关类型** | **全称** | **产生原因** | **是否为真依赖** | **重命名解决方式** |
| --- | --- | --- | --- | --- |
| RAW | 写后读 | 前一条指令写寄存器，后一条指令读该寄存器，需等待真实结果 | ✅ 真相关 | **无法消除**，通过Bypass转发缩短等待延迟 |
| WAW | 写后写 | 两条指令连续写同一个架构寄存器，无数据传递，仅寄存器复用冲突 | ❌ 伪相关 | **完全消除**：映射到不同物理寄存器，互不干扰、可乱序 |
| WAR | 读后写 | 前指令读寄存器，后指令写同寄存器，无数据冲突，仅资源复用冲突 | ❌ 伪相关 | **完全消除**：读写绑定不同物理寄存器，并行执行 |

```plain
// 1. RAW 真相关（写后读，必须等待，无法消除）
add  x10, x1, x2  // I1：写x10
add  x11, x10, x3 // I2：读x10，必须等待I1写完，真实数据依赖

// 2. WAW 伪相关（写后写，无数据传递，重命名可消除）
add  x10, x1, x2  // I1：写x10 → 重命名为 PR20
add  x10, x3, x4  // I2：覆盖写x10 → 重命名为 PR21，两条无数据交互，可乱序

// 3. WAR 伪相关（读后写，无数据冲突，重命名可消除）
add  x11, x10, x2 // I1：读x10 → 读PR20
add  x10, x1, x3  // I2：写x10 → 写PR22，读写不同物理寄存器，可并行
```

:::warning
**关键认知**：很多新手误以为重命名能解决所有数据相关，实际上**仅消除 WAW、WAR 伪相关**。RAW真相关无法消除，只能通过Bypass转发缩短延迟。这就像两个人确实需要传递同一个包裹，你必须等对方递过来——这不是"撞名"问题，而是真实需求。

:::

## 2.2 香山重命名模块整体架构

### 2.2.1 重命名模块全景图

香山的寄存器重命名并不是一个简单的"查表+分配"模块，而是一个**多组件协同**的复杂子系统。你可以把它想象成一座**高效的物流中心**：

```plain
┌──────────────── Rename（重命名物流中心）────────────────────────────────────────┐
│                                                                                │
│  输入：DecodeOutUop（来自译码的指令微操作）                                       │
│         │                                                                      │
│         ↓                                                                      │
│  ┌─────────────── 核心组件群 ───────────────┐                                  │
│  │                                          │                                  │
│  │  ┌──────────┐  ┌──────────┐  ┌────────┐ │                                  │
│  │  │ RenameTable│  │ FreeList │  │BusyTable│ │                                  │
│  │  │  映射表    │  │ 空闲链表  │  │ 忙闲表  │ │                                  │
│  │  │(AR→PR映射) │  │(PR资源池) │  │(PR状态) │ │                                  │
│  │  └─────┬────┘  └─────┬────┘  └───┬────┘ │                                  │
│  │        │             │            │       │                                  │
│  │        └──────┬──────┘──────┬─────┘       │                                  │
│  │               ↓             ↓             │                                  │
│  │        ┌──────────┐  ┌───────────┐        │                                  │
│  │        │ Snapshot │  │CompressUnit│        │                                  │
│  │        │  快照管理 │  │  压缩单元  │        │                                  │
│  │        └──────────┘  └───────────┘        │                                  │
│  └──────────────────────────────────────────┘                                  │
│         │                                                                      │
│         ↓                                                                      │
│  输出：RenameOutUop（带有物理寄存器号的微操作 → 发往Dispatch/ROB）                │
│                                                                                │
│  关键外部交互：                                                                  │
│  · redirect（重定向信号，分支预测错误/异常时冲刷）                                  │
│  · rabCommits（ROB提交信号，触发PR回收）                                         │
│  · snpt（快照端口，创建/删除/恢复快照）                                           │
└────────────────────────────────────────────────────────────────────────────────┘
```

> **图表解读**：重命名模块的核心是"三表一快照"——映射表管"谁映射到谁"、Freelist管"谁空闲可用"、BusyTable管"谁还在忙"、Snapshot管"出了事怎么回退"。CompressUnit则是性能优化利器，负责ROB压缩。

:::color4
**❤**\*\* 新手建议：\*\*

现阶段你只需记住：**重命名 = 查映射表（读源操作数） + 分配空闲PR（写目的寄存器） + Bypass转发（同周期依赖） + 快照/恢复（出错回退）**。不必一开始就纠结时序优化的细节，先建立整体观，后面我们逐个击破。

:::

### 2.2.2 五类寄存器的独立重命名通道

在香山源码中，重命名模块为**五类寄存器**分别建立了独立的映射表和空闲链表，这是一个非常关键的架构设计决策：

```scala
// 来自 Rename.scala L154-L158
val intFreeList  = Module(new MEFreeList(IntPhyRegs, RabCommitWidth))     // 整数寄存器
val fpFreeList   = Module(new StdFreeList(FpPhyRegs - FpLogicRegs, ...))  // 浮点寄存器
val vecFreeList  = Module(new StdFreeList(VfPhyRegs - VecLogicRegs, ...)) // 向量寄存器
val v0FreeList   = Module(new StdFreeList(V0PhyRegs - V0LogicRegs, ...))  // v0掩码寄存器
val vlFreeList   = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, ...))  // vl长度寄存器
```

| **寄存器类型** | **Freelist类型** | **架构寄存器数** | **物理寄存器数** | **说明** |
| --- | --- | --- | --- | --- |
| 整数 | MEFreeList | 32 | IntPhyRegs | 通用整数寄存器x0~x31 |
| 浮点 | StdFreeList | 32 | FpPhyRegs | 浮点寄存器f0~f31 |
| 向量 | StdFreeList | 31 | VfPhyRegs | 向量数据寄存器v1~v31（v0独立） |
| v0掩码 | StdFreeList | 1 | V0PhyRegs | 向量掩码寄存器v0 |
| vl长度 | StdFreeList | 1 | VlPhyRegs | 向量长度寄存器vl |

> **为什么要分开？** 因为不同类型寄存器的使用模式差异很大——整数寄存器最频繁、向量寄存器有特殊状态、v0/vl几乎是全局唯一的。分开管理既减少端口冲突，又简化了恢复逻辑。

***

## 2.3 重命名映射表（RenameTable）工作机制

重命名映射表是重命名模块的**核心数据库**，负责记录"架构寄存器 → 物理寄存器"的实时映射关系。在香山源码中，它由 `RenameTable` 类实现。

### 2.3.1 映射表的数据结构——"双表并行"

香山的映射表采用了**投机表+ 架构表**的双表设计，这是一个精妙的工程选择：

```scala
// 来自 RenameTable.scala L105-L117
// speculative rename table（投机映射表：记录当前流水线的实时映射）
val spec_table = RegInit(rename_table_init)
val spec_table_next = WireInit(spec_table)

// arch state rename table（架构映射表：仅记录已提交的确定映射）
val arch_table = RegInit(rename_table_init)
val arch_table_next = WireDefault(arch_table)
```

> 类比：你可以把 `spec_table` 想象成"工作草稿"——随时涂改，记录当前流水线最新的映射状态；`arch_table` 则是"正式存档"——只有指令提交后才会更新，永远保存确定无误的映射。当流水线出错需要回退时，我们就用正式存档覆盖工作草稿。

### 2.3.2 映射表的初始化——不同寄存器，不同策略

注意源码中 `rename_table_init` 的实现，不同类型寄存器的初始化方式截然不同：

```scala
// 来自 RenameTable.scala L106-L112
val rename_table_init = reg_t match {
  case Reg_I => VecInit.fill(IntLogicRegs)(0.U)           // 整数：全部映射到PR0（x0恒零）
  case Reg_F => VecInit.tabulate(FpLogicRegs)(_.U)         // 浮点：初始时ARi→PRi（1:1映射）
  case Reg_V => VecInit.tabulate(VecLogicRegs)(_.U)        // 向量：初始时ARi→PRi
  case Reg_V0 => VecInit.tabulate(V0LogicRegs)(_.U)        // v0：初始1:1映射
  case Reg_Vl => VecInit.tabulate(VlLogicRegs)(_.U)        // vl：初始1:1映射
}
```

**为什么整数寄存器初始化为0？** 因为RISC-V架构中 x0 恒为零，所有整数寄存器初始都映射到物理寄存器0，而物理寄存器0硬连线为零值。浮点和向量寄存器则采用"ARi→PRi"的1:1初始映射。

### 2.3.3 Read 映射（读阶段）——查表获取物理寄存器号

指令读源寄存器时，重命名模块根据指令携带的**架构寄存器号（AR）**，查询 `spec_table`，取出对应的**物理寄存器号（PR）**，发送给保留站与功能单元。

```scala
// 来自 RenameTable.scala L151-L157（简化注释版）
for ((r, i) <- io.readPorts.zipWithIndex) {
  // T0周期：检测同周期写端口的Bypass匹配
  val t0_bypass = io.specWritePorts.map(w => w.wen && w.addr === r.addr)
  // T1周期：Bypass信号打一拍
  val t1_bypass = RegNext(Mux(io.redirect, 0.U, VecInit(t0_bypass)))
  // Bypass数据
  val bypass_data = ParallelPriorityMux(t1_bypass.reverse, t1_wSpec.map(_.data).reverse)
  // 最终读结果：有Bypass命中则转发，否则查表
  r.data := Mux(t1_bypass.asUInt.orR, bypass_data, t1_rdata_use_t1_raddr(i))
}
```

**读映射的关键逻辑**：

1. 首先检查是否有同周期的写操作覆盖了要读的架构寄存器（Bypass）
2. 如果有Bypass命中，直接转发写数据（零延迟）
3. 如果没有Bypass命中，正常查 `spec_table` 获取映射

**读映射示例**：执行 `lw x12, 0(x10)` 时，硬件查询映射表，获取x10对应的物理寄存器PR30，指令直接向PR30发起读请求，全程不修改任何映射状态。

### 2.3.4 Write 映射（写阶段）——分配新PR并更新映射

指令写目的寄存器时，重命名模块会**分配新的空闲物理寄存器**，将当前架构寄存器的映射关系更新为新PR号，同时将旧PR号标记为待回收状态。

```scala
// 来自 RenameTable.scala L137-L149（简化注释版）
// 写端口的地址转One-Hot编码，用于快速匹配
val t1_wSpec_addr = t1_wSpec.map(w => Mux(w.wen, UIntToOH(w.addr), 0.U))

// 更新spec_table：逐项检查是否有写命中
for ((next, i) <- spec_table_next.zipWithIndex) {
  val matchVec = t1_wSpec_addr.map(w => w(i))  // 检查哪个写端口命中了第i项
  val wMatch = ParallelPriorityMux(matchVec.reverse, t1_wSpec.map(_.data).reverse)
  next := Mux(
    RegNext(t1_redirect),   // 如果有重定向
    Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),  // 快照恢复或arch表覆盖
    Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))  // 写命中则更新，否则保持
  )
}
spec_table := spec_table_next
```

**写映射的完整流程**：

1. 将写端口的地址转为One-Hot编码，便于并行匹配
2. 对spec\_table的每一项，检查是否有写操作命中
3. 如果有重定向（redirect），优先使用快照或arch\_table恢复
4. 如果有写命中，更新为新的PR号；否则保持不变

**写映射示例**：执行 `add x10, x5, x6`，原x10映射为PR30，重命名模块新分配PR35，将映射表更新为 x10→PR35，同时标记旧PR30为待回收状态。

### 2.3.5 Rename Bypass（重命名转发）——零延迟解决同周期RAW依赖

流水线相邻周期的多条指令存在寄存器依赖时，重命名模块无需等待指令写回PRF，直接通过**Bypass通路转发物理寄存器映射关系**，解决RAW相关的延迟问题。

```plain
// 同周期两条指令，存在RAW依赖
add  x10, x1, x2  // I1：重命名 x10->PR30
and  x11, x10, x3 // I2：读x10

// Bypass逻辑：
// I1先完成重命名，产出最新映射 x10=PR30
// Bypass直接将PR30转发给I2，I2直接读取PR30数据
// 无需等待I1写回PRF，无查表延迟，解决RAW等待开销
```

在源码中，Bypass是通过读端口逻辑中的 `t0_bypass` 检测和 `bypass_data` 转发实现的（见4.4.3节代码）。核心思想是：**同周期有写操作命中了要读的地址，就不去查旧表，直接拿写的数据用**。

:::color4
**❤**\*\* 新手建议：\*\*

映射表的读写逻辑是重命名模块最复杂的部分，特别是时序优化（T0/T1打拍）。初读源码时，建议先忽略时序优化（GatedValidRegNext、RegNext等），只关注核心逻辑：**读=查表+Bypass，写=分配新PR+更新映射**。时序优化的细节可以在理解核心逻辑后再回来品味。

:::

***

## 2.4 Freelist 空闲链表机制

Freelist是物理寄存器的**资源管理器**，负责统一管理所有PRF的分配、空闲、回收。你可以把它想象成一个**号码牌发放机**：新指令需要物理寄存器时来取号，指令提交后旧号码归还。

### 2.4.1 两种Freelist实现

香山源码中提供了两种Freelist实现，适用于不同的寄存器类型：

| **Freelist类型** | **源文件** | **适用场景** | **核心特点** |
| --- | --- | --- | --- |
| **MEFreeList** | MEFreeList.scala | 整数寄存器 | 支持快速恢复（Move Elimination优化） |
| **StdFreeList** | StdFreeList.scala | 浮点/向量/v0/vl | 标准实现，结构简洁 |

### 2.4.2 Freelist Update（分配）

重命名阶段分配新物理寄存器时，Freelist实时更新链表状态：从空闲链表头部取出可用PR号，分配给当前写指令，同时将该PR号从空闲列表中移除。

```scala
// 来自 Rename.scala L182-L188（整数Freelist的提交接口连接）
intFreeList.io.commit match {
  case commit =>
    commit.doCommit := io.rabCommits.isCommit
    commit.archAlloc := io.rabCommits.commitValid zip io.rabCommits.info map {
      case (valid, info) => valid && info.rfWen && !info.isMove
    }
}
```

**Freelist Update 示例**：

初始Freelist空闲队列：\[PR50, PR51, PR52, PR53...]

执行写指令 `sub x11, x2, x3`，Update操作取出队首PR50分配给x11，更新后Freelist：\[PR51, PR52, PR53...]

### 2.4.3 Freelist Release（释放回收）

当指令成功提交后，其占用的旧物理寄存器不再是架构寄存器的最新映射，此时触发Release回收逻辑：将旧PR号重新放回Freelist空闲链表尾部。

在源码中，回收逻辑需要**精确判断旧PR是否真的可以释放**——只有当arch\_table中没有任何映射指向该旧PR时，才能安全回收：

```scala
// 来自 RenameTable.scala L170-L174
for (((old, free), i) <- (old_pdest zip need_free).zipWithIndex) {
  val hasDuplicate = old_pdest.take(i).map(_ === old)
  val blockedByDup = if (i == 0) false.B else VecInit(hasDuplicate).asUInt.orR
  free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
}
```

**这段代码的逻辑**：

1. 检查旧PR号是否在arch\_table中还有其他映射指向它——如果还有，说明还有架构状态依赖它，不能回收
2. 检查同一批次提交中是否有重复的旧PR号——避免重复释放

**Freelist Release 示例**：x11绑定的PR50指令提交后，触发Release，PR50被放回Freelist队尾，空闲队列恢复：\[PR51, PR52, PR53..., PR50]，实现资源循环利用。

:::warning
**关键易错点**：物理寄存器**绝对不会在指令执行完成后立即回收**，必须等到**该指令提交且旧PR确实没有其他映射引用时**才释放。这是因为后续的指令可能还在读取旧PR的值，提前回收会导致数据错误。

:::

***

## 2.5 快照恢复机制（Snapshot）

Snapshot快照是香山重命名模块的**精准回退机制**。相比全局Redirect冲刷，快照可以实现"指定分支、指定区间"的映射表精准恢复，大幅减少无效重命名回退开销。

### 2.5.1 快照的源码实现

香山使用 `SnapshotGenerator` 在映射表之上实现快照功能：

```scala
// 来自 RenameTable.scala L135
val snapshots = SnapshotGenerator(spec_table, t1_snpt.snptEnq, t1_snpt.snptDeq, t1_redirect, t1_snpt.flushVec)
```

`SnapshotGenerator` 接收以下关键信号：

* `spec_table`：当前投机映射表（被快照的数据源）
* `snptEnq`：创建快照请求
* `snptDeq`：删除快照请求
* `t1_redirect`：重定向信号（触发快照恢复）
* `flushVec`：冲刷向量（指定恢复哪个快照）

### 2.5.2 快照恢复在映射表更新中的体现

在4.4.4节的写映射逻辑中，我们看到了快照恢复的核心代码：

```scala
// 来自 RenameTable.scala L143-L147
next := Mux(
  RegNext(t1_redirect),   // 如果有重定向
  Mux(t2_snpt.useSnpt,
    snapshots(t2_snpt.snptSelect)(i),  // 使用快照恢复
    arch_table(i)                       // 使用arch表恢复
  ),
  Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))  // 正常写映射
)
```

**恢复优先级**：

1. 检测到重定向信号 → 进入恢复模式
2. 优先尝试快照恢复（`useSnpt`为真时，选择对应快照数据）
3. 没有可用快照时，回退到arch\_table全局恢复

### 2.5.3 三大快照操作

#### Snapshot Create（创建快照）

当流水线检测到**分支指令、投机执行入口指令**时，自动创建当前周期的重命名映射表快照、Freelist状态快照、VCSR寄存器快照，保存当前合法的映射全局状态。每个分支对应独立快照，快照与ROB条目绑定，跟随指令生命周期流转。

#### Snapshot Delete（删除快照）

当分支指令**预测正确、正常提交**后，对应快照完成历史使命，硬件自动删除快照，释放快照存储资源。

#### Snapshot Restore（恢复快照）

当分支预测错误、投机路径作废时，触发快照恢复：丢弃当前所有新的重命名映射、作废未提交指令的PR占用、回退Freelist状态、恢复VCSR/Vtype寄存器状态至快照保存时刻。

### 2.5.4 快照机制运行示例

```plain
// 1. 分支指令B1，创建快照S1，保存：x10->PR21、Freelist状态、VCSR.vl值
beq  x1, x2, loop

// 2. 投机执行分支路径指令，持续更新映射
add  x10, x5, x6  // x10映射更新为PR25
sub  x11, x7, x8

// 3. 分支结果校验：预测错误，分支不跳转
// 4. 触发Snapshot Restore：
//    映射表回退至S1：x10->PR21
//    作废PR25占用，归还Freelist
//    恢复vl、vtype快照状态
// 5. 删除S1，从正确地址重新取指执行
```

***

## 2.6 Redirect 重定向机制

Redirect是流水线异常冲刷、分支预测错误时的**映射表快速修复机制**。当重定向信号到来时，映射表会立即停止正常写操作，切换到恢复模式。

### 2.6.1 Redirect 触发条件

满足以下任意场景，触发重命名Redirect重定向：

* 分支预测错误，流水线全局冲刷
* 指令执行触发异常、中断，流水线回退
* 投机执行路径作废，需要恢复正确架构状态

### 2.6.2 Redirect 在映射表中的实现

在源码中，Redirect信号对映射表的影响体现在两个层面：

**1. 写操作被屏蔽**：

```scala
// 来自 RenameTable.scala L130
val t1_wSpec = RegNext(Mux(io.redirect, 0.U.asTypeOf(io.specWritePorts), io.specWritePorts))
```

当redirect有效时，写端口被清零——所有未提交的投机映射写入全部作废。

**2. 读端口Bypass被清零**：

```scala
// 来自 RenameTable.scala L154
val t1_bypass = RegNext(Mux(io.redirect, 0.U.asTypeOf(VecInit(t0_bypass)), VecInit(t0_bypass)))
```

redirect有效时，Bypass信号也被清零，确保不会读到错误的转发数据。

### 2.6.3 Redirect Vtype 恢复机制

向量指令Vtype寄存器属于特殊状态寄存器，重命名逻辑与通用寄存器不同。发生Redirect冲刷时，硬件不会直接丢弃Vtype状态，而是通过**快照备份+定向恢复**机制：读取冲刷前保存的合法Vtype快照值，覆盖当前错乱的映射状态。

**Vtype Redirect恢复示例**：

1. 执行向量指令，当前Vtype=0b1010、vl=8，硬件备份该快照
2. 投机执行后续向量指令，临时修改Vtype=0b1100
3. 触发分支错误流水线冲刷
4. Redirect机制读取备份快照，将Vtype恢复为0b1010

***

## 2.7 Rename 和 ROB Compressed 工作原理

### 2.7.1 为什么需要ROB压缩？

ROB Compressed（ROB压缩）是香山架构的**性能优化机制**。想象一下：如果连续3条指令都在写x10寄存器，普通模式下需要3个ROB条目、3次映射更新、3次提交——但最终只有最后一次写的结果是有意义的。ROB压缩就是把这些"重复劳动"合并。

### 2.7.2 源码中的CompressUnit

香山在Rename模块中实例化了专门的 `CompressUnit`：

```scala
// 来自 Rename.scala L152
val compressUnit = Module(new CompressUnit())
```

CompressUnit负责检测连续无冲突的写指令，合并它们的ROB条目与重命名映射记录。

### 2.7.3 压缩机制运行示例

```plain
// 连续无冲突写同架构寄存器，满足压缩条件
add  x10, x1, x2
add  x10, x3, x4
add  x10, x5, x6

// 普通模式：3条指令分配3个ROB条目、3次映射更新、3次提交
// 压缩模式：合并为1组压缩ROB条目，仅保留最终x10最新PR映射
// 提交：批量一次提交，跳过中间两次无效映射
// 冲刷：一次性回退整条指令组状态，无需逐条恢复
```

***

## 2.8 多指令并行重命名实现原理

### 2.8.1 为什么需要并行重命名？

香山处理器支持**每周期多条指令同时重命名**，适配超发射流水线设计。如果每周期只能重命名一条指令，那么即使前端译码再快、后端执行单元再多，重命名模块也会成为瓶颈——就像一条单车道的高速公路入口，再宽的公路也得排队。

### 2.8.2 源码中的并行设计

在 `Rename.scala` 的IO定义中，我们可以看到多组并行端口的设计：

```scala
// 来自 Rename.scala L77-L84
val intReadPorts = Vec(RenameWidth, Vec(numIntRatPorts, new RatReadPort(...)))
val fpReadPorts  = Vec(RenameWidth, Vec(numFpRatPorts,  new RatReadPort(...)))
val vecReadPorts = Vec(RenameWidth, Vec(numVecRatPorts, new RatReadPort(...)))
val v0ReadPorts  = Vec(RenameWidth, new RatReadPort(...))
val vlReadPorts  = Vec(RenameWidth, new RatReadPort(...))
// RenameWidth组输出端口
val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
```

**关键设计**：

* `RenameWidth` 组读端口，每周期可以同时读取 `RenameWidth` 条指令的源操作数映射
* 每组读端口内部又按寄存器类型分多个子端口（整数2个、浮点3个、向量3个等）
* 写端口和Freelist分配也是 `RenameWidth` 宽度，支持并行分配

### 2.8.3 并行Bypass转发

多指令并行重命名时，同周期内的指令之间可能存在RAW依赖。香山通过**级联Bypass**解决这个问题：前序指令完成重命名后，其最新的PR映射直接通过Bypass网络转发给后续指令，无需等到下一周期。

***

## 2.9 重命名带宽瓶颈分析

重命名模块的核心带宽瓶颈集中在三大场景：

| **瓶颈类型** | **产生原因** | **影响** | **香山的优化手段** |
| --- | --- | --- | --- |
| **PR分配带宽瓶颈** | 多指令同时写寄存器时，Freelist分配端口不足 | 停顿，吞吐下降 | 多端口Freelist、MEFreeList优化 |
| **映射表读写瓶颈** | 高频读写映射表，端口冲突引发时序问题 | 时序不收敛，频率受限 | T0/T1打拍优化、GatedValidRegNext |
| **快照保存瓶颈** | 密集分支场景下，快照创建/存储占用大量带宽 | 快照资源耗尽，无法创建新快照 | 快照复用机制、有限快照深度 |

***

## 2.10 Decode与Rename宽度不一致的缓冲队列设计

前端Decode译码宽度与后端Rename重命名宽度**无法完全对齐**是工程常态（如译码4发射、重命名3发射），若直接直连会导致流水线频繁停顿、指令丢失。

> 类比：想象一条4车道的公路汇入一条3车道的隧道——如果直接对接，必然发生拥堵和碰撞。需要一个缓冲区（匝道）来平滑车流。

香山采用**异步缓冲队列**解决该问题：

* 前端译码完成的指令先写入缓冲队列，不直接推送重命名模块
* 重命名模块根据自身带宽，匀速从队列取指令执行重命名
* 队列满则暂停前端译码，队列空则前端全速发射，实现上下游带宽解耦

```scala
// 来自 Rename.scala L67
val in = Vec(RenameWidth, Flipped(DecoupledIO(new DecodeOutUop)))
// DecoupledIO自带ready/valid握手，天然支持背压（back-pressure）
// 当Rename来不及处理时，ready拉低，自动阻塞Decode侧
```

***

## 2.11 VCSR.vl 向量长度寄存器重命名机制

VCSR.vl是RISC-V向量架构的**特殊状态寄存器**，用于记录当前向量指令的有效长度。它的重命名逻辑与通用寄存器完全不同——属于"状态类特殊重命名"。

**核心重命名规则**：

* vl寄存器属于**全局状态寄存器**，不参与通用PRF分配，采用独立状态快照管理
* 向量指令修改vl时，硬件单独保存vl的快照，绑定当前ROB条目
* 正常提交：更新全局vl状态；冲刷回退：通过快照恢复旧vl值
* 重命名阶段不分配物理寄存器，仅做**状态记录与快照备份**

```scala
// 来自 Rename.scala L158
val vlFreeList = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, RabCommitWidth, 1))
// vl只有1个架构寄存器，物理寄存器数量也很少
// 独立的Freelist和映射表通道，与通用寄存器解耦
```

```plain
// 1. 配置向量长度vl=16，硬件保存vl快照S_vl1
vsetvli t0, 16, e8, m1

// 2. 执行多组向量指令，复用vl=16
vadd.vv v1, v2, v3
vsub.vv v4, v5, v6

// 3. 修改向量长度vl=8，更新快照S_vl2
vsetvli t0, 8, e8, m1

// 4. 触发分支冲刷，投机路径作废
// 5. 硬件读取旧快照S_vl1，恢复vl=16，保证后续向量指令正常执行
```

:::warning
**新手建议**：初学者重点掌握通用寄存器重命名、三种架构差异、Freelist与快照核心逻辑。VCSR.vl特殊重命名、带宽瓶颈、缓冲队列设计属于工程进阶内容，了解概念即可，待基础扎实后再深入源码。

:::

***

## 2.12 新手实战：从源码追踪一条指令的重命名过程

让我们以一条具体的指令为例，在源码中追踪它的完整重命名过程。

### 2.12.1 实战任务：追踪 `add x10, x1, x2` 的重命名

**步骤1：指令进入Rename模块**

```scala
// Rename.scala L67
val in = Vec(RenameWidth, Flipped(DecoupledIO(new DecodeOutUop)))
```

译码后的 `add x10, x1, x2` 通过 `in` 端口进入Rename模块。此时指令携带的信息包括：源操作数x1、x2（架构寄存器号），目的寄存器x10，以及各种控制信号。

**步骤2：读源操作数映射**

```scala
// Rename.scala L77
val intReadPorts = Vec(RenameWidth, Vec(numIntRatPorts, new RatReadPort(log2Ceil(IntLogicRegs))))
```

重命名模块通过 `intReadPorts` 向整数映射表发起读请求：查询x1和x2当前映射到哪个物理寄存器。映射表返回 PR\_x1 和 PR\_x2。

**步骤3：Bypass检查**

如果同周期有前序指令也在写x1或x2，Bypass逻辑会检测到并直接转发最新的PR号，无需查表。

**步骤4：为目的寄存器分配新PR**

```scala
// Rename.scala L154
val intFreeList = Module(new MEFreeList(IntPhyRegs, RabCommitWidth))
```

从整数Freelist中取出一个空闲PR（例如PR50），分配给x10。Freelist头部指针前移。

**步骤5：更新映射表**

将 x10→PR50 的新映射写入 `spec_table`。旧的 x10 映射（例如PR30）被记录为 `old_pdest`，等待提交后回收。

**步骤6：输出重命名结果**

```scala
// Rename.scala L83
val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
```

重命名完成的微操作通过 `out` 端口输出，此时指令已携带物理寄存器号：psrc(0)=PR\_x1, psrc(1)=PR\_x2, pdest=PR50。

***

## 2.13 常见问题与排错指南

### Q1：为什么物理寄存器数量远大于架构寄存器？

物理寄存器需要容纳：当前正在执行的指令的临时结果 + 已提交但尚未回收的旧值 + 投机执行路径上的中间结果。如果物理寄存器不够，Freelist为空，重命名模块就会停顿，流水线吞吐骤降。香山通过配置参数（如 `IntPhyRegs`、`FpPhyRegs`）控制各类物理寄存器数量。

### Q2：快照用完了怎么办？

快照资源是有限的（硬件面积限制）。当快照全部被占用时，新分支指令无法创建快照，此时需要等待旧快照释放（分支提交后删除），或者采用全局冲刷回退到arch\_table。这是性能与面积的工程权衡。

### Q3：如何验证重命名逻辑的正确性？

香山提供了Difftest协同仿真框架，可以对比RTL仿真结果与参考模型（如Spike/NEMU）的架构状态。映射表还提供了 `debug_rdata` 端口，可以实时读取arch\_table内容用于对比验证。

***

## 2.14 分级学习路径指引

🟢 **入门必掌握**：伪相关概念（WAW/WAR消除）、统一PRF重命名流程、Freelist基础分配回收逻辑

🔵 **进阶需理解**：映射表读写与Bypass（`RenameTable.scala`）、Redirect恢复、Snapshot快照机制、ROB压缩原理（`CompressUnit.scala`）

🟣 **精通深挖**：多指令并行重命名的时序优化（T0/T1打拍）、带宽瓶颈优化、宽窄模块缓冲队列设计、VCSR.vl特殊重命名、Freelist的ME/Std两种实现差异

***

## 2.15 本章总结

✅ **核心知识点回顾**：

* 寄存器重命名核心价值：**消除WAW、WAR伪数据相关**，释放乱序执行能力，RAW真相关无法消除
* 三种重命名架构中，香山采用**统一PRF重命名**，适配高性能超乱序流水线
* 香山为五类寄存器（整数/浮点/向量/v0/vl）建立了**独立的映射表和Freelist**
* 映射表采用**spec\_table + arch\_table双表设计**，投机与架构状态分离，支持快速恢复
* Freelist是物理寄存器的**资源管理器**，分配与回收需严格保证正确性（不能提前回收）
* Redirect、Snapshot、ROB压缩三大机制，分别解决快速冲刷、精准回退、性能优化问题
* 并行重命名通过多端口映射表、级联Bypass、宽Freelist实现，缓冲队列解决上下游带宽不匹配
* VCSR.vl为特殊状态寄存器，采用**快照式状态管理**，无物理寄存器分配逻辑

🎉 **恭喜你完成了寄存器重命名的学习！** 这是理解乱序处理器最核心的机制之一。掌握了重命名，你就理解了乱序处理器"为什么能乱序"的根本答案。接下来，让我们继续探索香山流水线中指令是如何被调度和发射执行的。


> 更新: 2026-06-01 18:20:04  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/xva8bkbdnhaa3x5g>
-->

# 2. Register Renaming


> If register renaming is new to you, terms such as "rename table," "Freelist," "Bypass forwarding," and "snapshot recovery" may seem complicated. Do not worry: register renaming is fundamentally about giving each instruction a "true name" so that instructions no longer wait for one another because of name collisions. We will build the idea step by step; it is more intuitive than it first appears.

:::info

By completing this section, you will be able to:


* 🧭 Fully understand the **core purpose** of register renaming: eliminating false data dependencies (WAW/WAR)

* 📋 Master three mainstream renaming architectures: **ROB renaming, ARF-extension renaming, and unified-PRF renaming**

* 🔍 Understand the complete read/write, bypass-forwarding, and free-list mechanisms of the rename table, and **read the XiangShan source implementation**

* 🗺️ Master the three pipeline-recovery mechanisms: Redirect, Snapshot, and ROB compression

* ⚡ Understand the engineering of parallel multi-instruction renaming, bandwidth bottlenecks, and width-mismatch buffering queues

* 📦 Learn the dedicated renaming logic for the special register **VCSR.vl**

:::

***


## 2.1 Core Principle of Register Renaming: Eliminating False Data Dependencies


### 2.1.1 Why Is Renaming Needed? The "Room Number" Story


When learning out-of-order execution, the largest performance obstacle is often not a true data dependency but a **false dependency caused by the limited number of architectural registers**. Many instructions exchange no real data, yet the pipeline serializes them simply because they reuse the same architectural register, severely limiting out-of-order parallelism.


> A simple analogy: architectural registers are a limited set of "room numbers" (only 32), while physical registers are a large set of "actual rooms" (128). The program sees only the limited room numbers; hardware renaming maps one room number to different actual rooms so that old and new instructions do not crowd each other out or wait unnecessarily.


**Register renaming is the core false-dependency-removal mechanism in an out-of-order processor**: it maps the small set of program-visible architectural registers (ARF) to the hardware's much larger physical register file (PRF), allowing instructions with no dependency to execute in parallel and out of order.


### 2.1.2 Three Data Dependencies and Renaming Solutions


Data dependencies are divided into **true dependencies (RAW)** and **false dependencies (WAW/WAR)**. Register renaming can eliminate both false dependencies completely while simplifying scheduling of true dependencies.


| **Dependency** | **Full name** | **Cause** | **True dependency?** | **Renaming solution** |
| --- | --- | --- | --- | --- |

| RAW | Read after write | The earlier instruction writes a register and the later instruction reads it, so the true result is required | ✅ True | **Cannot be eliminated**; bypass forwarding shortens the wait |

| WAW | Write after write | Two instructions write the same architectural register; there is no data transfer, only a register-reuse conflict | ❌ False | **Eliminated completely**: map them to different physical registers so they can execute out of order |

| WAR | Write after read | The earlier instruction reads a register and the later instruction writes it; there is no data conflict, only resource reuse | ❌ False | **Eliminated completely**: bind the read and write to different physical registers so they can execute in parallel |


```plain
// 1. RAW true dependency (read after write; must wait and cannot be eliminated)
add  x10, x1, x2  // I1: writes x10
add  x11, x10, x3 // I2: reads x10; must wait for I1, a true data dependency

// 2. WAW false dependency (write after write; no data transfer, removable by renaming)
add  x10, x1, x2  // I1: writes x10 -> renamed to PR20
add  x10, x3, x4  // I2: overwrites x10 -> renamed to PR21; no data interaction, can execute out of order

// 3. WAR false dependency (write after read; no data conflict, removable by renaming)
add  x11, x10, x2 // I1: reads x10 -> reads PR20
add  x10, x1, x3  // I2: writes x10 -> writes PR22; different physical registers allow parallel execution
```

:::warning

**Key point**: Many beginners assume renaming solves every data dependency, but it **only removes WAW and WAR false dependencies**. A true RAW dependency cannot be eliminated; bypass forwarding can only shorten its latency. It is like two people who genuinely need to pass the same package: one must wait for the other to hand it over. This is a real requirement, not a name collision.

:::


## 2.2 XiangShan Register-Renaming Architecture


### 2.2.1 Rename Module Overview


XiangShan register renaming is not a simple "lookup + allocation" block but a complex subsystem in which **multiple components cooperate**. You can think of it as an **efficient logistics center**:


```plain
┌──────────────── Rename (renaming logistics center) ────────────────────────────┐
│                                                                                │
│  Input: DecodeOutUop (decoded instruction micro-operation)                    │
│         │                                                                      │
│         ↓                                                                      │
│  ┌─────────────── Core component group ───────────────┐                       │
│  │                                                     │                       │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐          │                       │
│  │  │RenameTable│  │ FreeList │  │BusyTable │          │                       │
│  │  │ rename map │  │ free list │  │ busy table│         │                       │
│  │  │(AR→PR map) │  │(PR pool)  │  │(PR status)│         │                       │
│  │  └─────┬────┘  └─────┬────┘  └─────┬────┘          │                       │
│  │        │             │              │              │                       │
│  │        └──────┬──────┘──────┬───────┘              │                       │
│  │               ↓             ↓                     │                       │
│  │        ┌──────────┐  ┌───────────┐                │                       │
│  │        │ Snapshot │  │CompressUnit│                │                       │
│  │        │ snapshot mgr│ │ compression unit│          │                       │
│  │        └──────────┘  └───────────┘                │                       │
│  └───────────────────────────────────────────────────┘                       │
│         │                                                                      │
│         ↓                                                                      │
│  Output: RenameOutUop (uop with physical register numbers -> Dispatch/ROB)    │
│                                                                                │
│  Key external interactions:                                                    │
│  · redirect (flush on branch misprediction/exception)                         │
│  · rabCommits (ROB commit signals that trigger PR reclamation)                 │
│  · snpt (snapshot ports for create/delete/restore)                             │
└────────────────────────────────────────────────────────────────────────────────┘
```


> **How to read the diagram**: The rename module is centered on "three tables and one snapshot": the rename table tracks "who maps to whom," the Freelist tracks "who is free," the BusyTable tracks "who is still busy," and Snapshot tracks "how to roll back after an error." CompressUnit is a performance optimization that compresses ROB entries.

:::color4
**❤**\*\* Beginner tip:\*\*


For now, remember only: **renaming = look up the rename table (read source operands) + allocate a free PR (write the destination register) + bypass forwarding (same-cycle dependencies) + snapshot/recovery (rollback on errors)**. Do not get stuck on timing optimizations at first; establish the overall picture and return to details later.

:::


### 2.2.2 Independent Rename Channels for Five Register Classes


In the XiangShan source, the rename module builds an independent rename table and free list for **five register classes**. This is a key architectural decision:


```scala
// From Rename.scala L154-L158
val intFreeList  = Module(new MEFreeList(IntPhyRegs, RabCommitWidth))     // integer registers
val fpFreeList   = Module(new StdFreeList(FpPhyRegs - FpLogicRegs, ...))  // floating-point registers
val vecFreeList  = Module(new StdFreeList(VfPhyRegs - VecLogicRegs, ...)) // vector registers
val v0FreeList   = Module(new StdFreeList(V0PhyRegs - V0LogicRegs, ...))  // v0 mask register
val vlFreeList   = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, ...))  // vl length register
```


| **Register class** | **Freelist type** | **Architectural registers** | **Physical registers** | **Description** |
| --- | --- | --- | --- | --- |

| Integer | MEFreeList | 32 | IntPhyRegs | General-purpose integer registers x0-x31 |

| Floating point | StdFreeList | 32 | FpPhyRegs | Floating-point registers f0-f31 |

| Vector | StdFreeList | 31 | VfPhyRegs | Vector data registers v1-v31 (v0 is separate) |

| v0 mask | StdFreeList | 1 | V0PhyRegs | Vector mask register v0 |

| vl length | StdFreeList | 1 | VlPhyRegs | Vector length register vl |


> **Why separate them?** Register classes have very different usage patterns: integer registers are used most frequently, vector registers carry special state, and v0/vl are nearly global singletons. Separate management reduces port conflicts and simplifies recovery.

***


## 2.3 Rename Table (`RenameTable`) Operation


The rename table is the rename module's **central database**. It records the live "architectural register -> physical register" mapping and is implemented by the `RenameTable` class in XiangShan.


### 2.3.1 Rename-Table Data Structure: Two Tables in Parallel


XiangShan uses a two-table design with a **speculative table plus an architectural table**, a careful engineering choice:


```scala
// From RenameTable.scala L105-L117
// speculative rename table (records the pipeline's current live mapping)
val spec_table = RegInit(rename_table_init)
val spec_table_next = WireInit(spec_table)

// arch state rename table (records only committed, definitive mappings)
val arch_table = RegInit(rename_table_init)
val arch_table_next = WireDefault(arch_table)
```


> As an analogy, think of `spec_table` as a "working draft" that is edited continuously and records the pipeline's newest mapping, while `arch_table` is the "official archive" updated only when instructions commit and therefore always definitive. When the pipeline must roll back, the archive overwrites the draft.


### 2.3.2 Rename-Table Initialization: Different Strategies by Register Class


Observe that `rename_table_init` is initialized differently for each register class:


```scala
// From RenameTable.scala L106-L112
val rename_table_init = reg_t match {
  case Reg_I => VecInit.fill(IntLogicRegs)(0.U)           // integer: all map to PR0 (x0 is always zero)
  case Reg_F => VecInit.tabulate(FpLogicRegs)(_.U)         // floating point: initial ARi -> PRi (1:1)
  case Reg_V => VecInit.tabulate(VecLogicRegs)(_.U)        // vector: initial ARi -> PRi
  case Reg_V0 => VecInit.tabulate(V0LogicRegs)(_.U)        // v0: initial 1:1 mapping
  case Reg_Vl => VecInit.tabulate(VlLogicRegs)(_.U)        // vl: initial 1:1 mapping
}
```


**Why are integer registers initialized to zero?** In RISC-V, x0 is always zero, so all integer registers initially map to physical register 0, which is hard-wired to zero. Floating-point and vector registers instead use an initial one-to-one `ARi -> PRi` mapping.


### 2.3.3 Read Mapping: Look Up the Physical Register Number


When an instruction reads a source register, the rename module uses its **architectural register number (AR)** to query `spec_table`, obtains the corresponding **physical register number (PR)**, and sends it to the reservation station and execution units.


```scala
// From RenameTable.scala L151-L157 (simplified comments)
for ((r, i) <- io.readPorts.zipWithIndex) {
  // T0: detect a same-cycle bypass match from a write port
  val t0_bypass = io.specWritePorts.map(w => w.wen && w.addr === r.addr)
  // T1: register the bypass signal
  val t1_bypass = RegNext(Mux(io.redirect, 0.U, VecInit(t0_bypass)))
  // Bypass data
  val bypass_data = ParallelPriorityMux(t1_bypass.reverse, t1_wSpec.map(_.data).reverse)
  // Final read result: forward on a bypass hit; otherwise use the table
  r.data := Mux(t1_bypass.asUInt.orR, bypass_data, t1_rdata_use_t1_raddr(i))
}
```


**Key read-mapping logic**:


1. First check whether a same-cycle write covers the architectural register being read (Bypass).

2. On a bypass hit, forward the write data directly (zero additional latency).

3. Without a bypass hit, query `spec_table` normally to obtain the mapping.


**Read-mapping example**: For `lw x12, 0(x10)`, hardware queries the rename table, finds that x10 maps to PR30, and issues the read directly to PR30 without changing any mapping state.


### 2.3.4 Write Mapping: Allocate a New PR and Update the Mapping


When an instruction writes a destination register, the rename module **allocates a new free physical register**, updates the architectural register's mapping to the new PR number, and marks the old PR for later reclamation.


```scala
// From RenameTable.scala L137-L149 (simplified comments)
// Convert write-port addresses to one-hot form for fast matching
val t1_wSpec_addr = t1_wSpec.map(w => Mux(w.wen, UIntToOH(w.addr), 0.U))

// Update spec_table: check each entry for a write hit
for ((next, i) <- spec_table_next.zipWithIndex) {
  val matchVec = t1_wSpec_addr.map(w => w(i))  // which write port hits entry i
  val wMatch = ParallelPriorityMux(matchVec.reverse, t1_wSpec.map(_.data).reverse)
  next := Mux(
    RegNext(t1_redirect),   // redirect takes the recovery path
    Mux(t2_snpt.useSnpt, snapshots(t2_snpt.snptSelect)(i), arch_table(i)),  // snapshot or arch-table restore
    Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))  // update on a write hit; otherwise hold
  )
}
spec_table := spec_table_next
```


**Complete write-mapping flow**:


1. Convert write-port addresses to one-hot form for parallel matching.

2. For each `spec_table` entry, check whether any write operation hits it.

3. If a redirect occurs, prioritize recovery from a snapshot or `arch_table`.

4. On a write hit, update to the new PR number; otherwise keep the old value.


**Write-mapping example**: For `add x10, x5, x6`, x10 initially maps to PR30. The rename module allocates PR35, updates the table to x10 -> PR35, and marks old PR30 for reclamation.


### 2.3.5 Rename Bypass: Zero-Latency Resolution of Same-Cycle RAW Dependencies


When instructions in adjacent pipeline positions have register dependencies, the rename module need not wait for a writeback to the PRF. It **forwards the physical-register mapping through the bypass path** to remove the RAW-related delay.


```plain
// Two same-cycle instructions with a RAW dependency
add  x10, x1, x2  // I1: rename x10 -> PR30
and  x11, x10, x3 // I2: read x10

// Bypass logic:
// I1 renames first and produces the newest mapping x10 = PR30
// Bypass forwards PR30 directly to I2, which reads data from PR30
// No wait for I1's PRF writeback and no table-lookup latency
```


In the source, Bypass is implemented by the read-port `t0_bypass` detection and `bypass_data` forwarding (see the code in Section 4.4.3). The core idea is: **if a same-cycle write hits the address being read, use the write data directly instead of looking up the old table**.

:::color4

**❤**\*\* Beginner tip:\*\*


Rename-table read/write logic is the most complex part of the module, especially the timing optimization that registers T0/T1 signals. On a first pass, ignore optimizations such as `GatedValidRegNext` and `RegNext` and focus on the core: **read = table lookup + Bypass; write = allocate a new PR + update the mapping**. Return to timing details after the core behavior is clear.

:::

***


## 2.4 Freelist Operation


The Freelist is the physical-register **resource manager**. It manages allocation, availability, and reclamation for the PRF. Think of it as a **ticket dispenser**: a new instruction takes a number when it needs a physical register, and the old number is returned after commit.


### 2.4.1 Two Freelist Implementations


The XiangShan source provides two Freelist implementations for different register classes:


| **Freelist type** | **Source file** | **Use case** | **Key feature** |
| --- | --- | --- | --- |

| **MEFreeList** | MEFreeList.scala | Integer registers | Supports fast recovery (Move Elimination optimization) |

| **StdFreeList** | StdFreeList.scala | Floating-point/vector/v0/vl | Standard implementation with a simple structure |


### 2.4.2 Freelist Update (Allocation)


When the rename stage allocates a new physical register, the Freelist updates its state immediately: it takes an available PR number from the head of the free list, assigns it to the current writing instruction, and removes that number from the list.


```scala
// From Rename.scala L182-L188 (integer Freelist commit-interface connection)
intFreeList.io.commit match {
  case commit =>
    commit.doCommit := io.rabCommits.isCommit
    commit.archAlloc := io.rabCommits.commitValid zip io.rabCommits.info map {
      case (valid, info) => valid && info.rfWen && !info.isMove
    }
}
```


**Freelist Update example**:


Initial Freelist queue: `[PR50, PR51, PR52, PR53...]`


For `sub x11, x2, x3`, Update takes PR50 from the head and assigns it to x11; the Freelist becomes `[PR51, PR52, PR53...]`.


### 2.4.3 Freelist Release (Reclamation)


After an instruction commits successfully, its old physical register is no longer the architectural register's newest mapping. Release then returns the old PR number to the tail of the Freelist.


In the source, reclamation must **precisely determine whether the old PR is safe to release**. It is safe only when no mapping in `arch_table` still points to that old PR:

```scala
// From RenameTable.scala L170-L174
for (((old, free), i) <- (old_pdest zip need_free).zipWithIndex) {
  val hasDuplicate = old_pdest.take(i).map(_ === old)
  val blockedByDup = if (i == 0) false.B else VecInit(hasDuplicate).asUInt.orR
  free := VecInit(arch_table.map(_ =/= old)).asUInt.andR && !blockedByDup
}
```


**Logic of this code**:


1. Check whether another mapping in `arch_table` still points to the old PR. If so, architectural state still depends on it, so it cannot be reclaimed.

2. Check for duplicate old PR numbers in the same commit batch to avoid releasing one PR twice.


**Freelist Release example**: After the instruction bound to PR50 for x11 commits, Release returns PR50 to the tail, restoring `[PR51, PR52, PR53..., PR50]` and reusing the resource cyclically.

:::warning

**Important pitfall**: A physical register is **never reclaimed immediately when an instruction finishes execution**. It is released only after **the instruction commits and no other mapping references the old PR**. Later instructions may still read that PR; early reclamation would corrupt their data.

:::

***


## 2.5 Snapshot Recovery


A Snapshot is the XiangShan rename module's **precise rollback mechanism**. Unlike a global Redirect flush, it can precisely restore the rename table for a "specified branch and interval," greatly reducing useless rename rollback work.


### 2.5.1 Snapshot Implementation in the Source


XiangShan implements snapshots on top of the rename table with `SnapshotGenerator`:

```scala
// From RenameTable.scala L135
val snapshots = SnapshotGenerator(spec_table, t1_snpt.snptEnq, t1_snpt.snptDeq, t1_redirect, t1_snpt.flushVec)
```

`SnapshotGenerator` receives these key signals:


* `spec_table`: current speculative rename table (snapshot source)

* `snptEnq`: snapshot-creation request

* `snptDeq`: snapshot-deletion request

* `t1_redirect`: redirect signal (triggers snapshot recovery)

* `flushVec`: flush vector (selects which snapshot to restore)


### 2.5.2 Snapshot Recovery in Rename-Table Updates


The write-mapping logic in Section 4.4.4 contains the core snapshot-recovery code:

```scala
// From RenameTable.scala L143-L147
next := Mux(
  RegNext(t1_redirect),   // redirect takes the recovery path
  Mux(t2_snpt.useSnpt,
    snapshots(t2_snpt.snptSelect)(i),  // recover from the snapshot
    arch_table(i)                       // recover from the arch table
  ),
  Mux(VecInit(matchVec).asUInt.orR, wMatch, spec_table(i))  // normal write mapping
)
```


**Recovery priority**:


1. A redirect signal is detected -> enter recovery mode.

2. Prefer snapshot recovery; when `useSnpt` is true, select the corresponding snapshot data.

3. If no snapshot is available, fall back to global recovery from `arch_table`.


### 2.5.3 Three Snapshot Operations

#### Snapshot Create (Create a Snapshot)


When the pipeline detects a **branch or speculative-entry instruction**, it automatically creates snapshots of the current rename table, Freelist state, and VCSR registers. Each branch gets an independent snapshot; the snapshot is bound to its ROB entry and follows the instruction lifetime.

#### Snapshot Delete (Delete a Snapshot)


After a branch is **predicted correctly and commits normally**, its snapshot has served its purpose. Hardware deletes it automatically and frees the snapshot storage.

#### Snapshot Restore (Restore a Snapshot)


When a branch is mispredicted and the speculative path is discarded, snapshot recovery drops all new rename mappings, invalidates PR allocations of uncommitted instructions, rolls back Freelist state, and restores VCSR/Vtype state to the snapshot point.


### 2.5.4 Snapshot Operation Example


```plain
// 1. Branch B1 creates snapshot S1, saving x10 -> PR21, Freelist state, and VCSR.vl
beq  x1, x2, loop

// 2. Speculatively execute the branch-path instructions and update mappings
add  x10, x5, x6  // x10 mapping becomes PR25
sub  x11, x7, x8

// 3. Branch resolution: misprediction; the branch is not taken
// 4. Trigger Snapshot Restore:
//    restore the table to S1: x10 -> PR21
//    invalidate PR25's allocation and return it to the Freelist
//    restore the vl and vtype snapshot state
// 5. Delete S1 and fetch again from the correct address
```

***


## 2.6 Redirect Mechanism


Redirect is the **fast rename-table repair mechanism** used for pipeline exception flushes and branch mispredictions. When a redirect arrives, the table immediately stops normal writes and switches to recovery mode.


### 2.6.1 Redirect Trigger Conditions


Rename Redirect is triggered by any of the following:


* A branch misprediction causes a global pipeline flush.

* Instruction execution raises an exception or interrupt, requiring pipeline rollback.

* A speculative path is discarded and the correct architectural state must be restored.


### 2.6.2 Redirect in the Rename Table


In the source, Redirect affects the rename table in two ways:

**1. Writes are masked**:

```scala
// From RenameTable.scala L130
val t1_wSpec = RegNext(Mux(io.redirect, 0.U.asTypeOf(io.specWritePorts), io.specWritePorts))
```


When redirect is asserted, the write ports are cleared, invalidating all uncommitted speculative mapping writes.

**2. Read-port Bypass is cleared**:

```scala
// From RenameTable.scala L154
val t1_bypass = RegNext(Mux(io.redirect, 0.U.asTypeOf(VecInit(t0_bypass)), VecInit(t0_bypass)))
```


When redirect is asserted, Bypass signals are also cleared so that no incorrect forwarded data can be read.


### 2.6.3 Redirect Vtype Recovery


The vector-instruction Vtype register is a special state register, so its renaming logic differs from general-purpose registers. During a Redirect flush, hardware does not simply discard Vtype state. Instead, it uses **snapshot backup plus targeted recovery**: the valid Vtype snapshot saved before the flush overwrites the corrupted current mapping state.

**Vtype Redirect recovery example**:


1. Execute a vector instruction with Vtype=0b1010 and vl=8; hardware saves this snapshot.

2. Speculatively execute later vector instructions and temporarily change Vtype to 0b1100.

3. Trigger a pipeline flush for a branch misprediction.

4. Redirect reads the backup snapshot and restores Vtype to 0b1010.

***


## 2.7 Rename and ROB Compression


### 2.7.1 Why Is ROB Compression Needed?


ROB Compression is a **performance optimization** in XiangShan. If three consecutive instructions all write x10, normal operation needs three ROB entries, three mapping updates, and three commits, although only the final write matters. ROB compression combines this "repeated work."


### 2.7.2 `CompressUnit` in the Source


The Rename module instantiates a dedicated `CompressUnit`:

```scala
// From Rename.scala L152
val compressUnit = Module(new CompressUnit())
```


`CompressUnit` detects consecutive conflict-free writes and merges their ROB entries and rename-mapping records.


### 2.7.3 Compression Example


```plain
// Consecutive conflict-free writes to one architectural register satisfy compression
add  x10, x1, x2
add  x10, x3, x4
add  x10, x5, x6

// Normal mode: three instructions allocate three ROB entries, update mappings three times, and commit three times
// Compression mode: merge them into one compressed ROB group and keep only x10's newest PR mapping
// Commit: commit the group once and skip the two intermediate obsolete mappings
// Flush: roll back the whole instruction group in one operation; no per-instruction recovery is needed
```

***


## 2.8 Parallel Multi-Instruction Renaming


### 2.8.1 Why Is Parallel Renaming Needed?


XiangShan supports **renaming multiple instructions per cycle** for its superscalar pipeline. If only one instruction could be renamed each cycle, the rename module would bottleneck the design regardless of front-end decode speed or the number of back-end execution units, like a one-lane highway entrance feeding a much wider road.


### 2.8.2 Parallel Design in the Source


The `Rename.scala` I/O definition exposes several groups of parallel ports:

```scala
// From Rename.scala L77-L84
val intReadPorts = Vec(RenameWidth, Vec(numIntRatPorts, new RatReadPort(...)))
val fpReadPorts  = Vec(RenameWidth, Vec(numFpRatPorts,  new RatReadPort(...)))
val vecReadPorts = Vec(RenameWidth, Vec(numVecRatPorts, new RatReadPort(...)))
val v0ReadPorts  = Vec(RenameWidth, new RatReadPort(...))
val vlReadPorts  = Vec(RenameWidth, new RatReadPort(...))
// RenameWidth groups of output ports
val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
```

**Key design points**:


* `RenameWidth` groups of read ports can read source mappings for `RenameWidth` instructions in the same cycle.

* Each group is split into multiple sub-ports by register class (for example, two integer, three floating-point, and three vector ports).

* Write ports and Freelist allocation are also `RenameWidth` wide, enabling parallel allocation.


### 2.8.3 Parallel Bypass Forwarding


Parallel renaming can create same-cycle RAW dependencies between instructions. XiangShan resolves them with **cascaded Bypass**: once an earlier instruction is renamed, its newest PR mapping is forwarded through the Bypass network to later instructions without waiting for the next cycle.

***


## 2.9 Rename Bandwidth Bottlenecks


The rename module's main bandwidth bottlenecks fall into three scenarios:


| **Bottleneck** | **Cause** | **Impact** | **XiangShan optimization** |
| --- | --- | --- | --- |

| **PR-allocation bandwidth** | Too few Freelist allocation ports when multiple instructions write registers | Stalls and lower throughput | Multi-port Freelist and MEFreeList optimization |

| **Rename-table read/write bandwidth** | Frequent table accesses create port conflicts and timing problems | Timing fails to converge and frequency is limited | T0/T1 pipelining and `GatedValidRegNext` |

| **Snapshot-storage bandwidth** | Snapshot creation/storage consumes substantial bandwidth in branch-dense code | Snapshot resources run out and new snapshots cannot be created | Snapshot reuse and bounded snapshot depth |

***


## 2.10 Buffering for Decode/Rename Width Mismatch


It is normal for front-end Decode width and back-end Rename width to be **not perfectly aligned** (for example, Decode can issue four while Rename handles three). Directly connecting them would cause frequent stalls and lost instructions.


> Analogy: imagine a four-lane road merging directly into a three-lane tunnel; congestion and collisions are inevitable. A buffer (an on-ramp) is needed to smooth the traffic.


XiangShan uses an **asynchronous buffer queue**:


* Decoded instructions are first written to the buffer rather than pushed directly to Rename.

* Rename consumes instructions from the queue at its own bandwidth and renames them steadily.

* A full queue pauses front-end Decode; an empty queue lets the front end issue at full speed, decoupling upstream and downstream bandwidth.

```scala
// From Rename.scala L67
val in = Vec(RenameWidth, Flipped(DecoupledIO(new DecodeOutUop)))
// DecoupledIO provides a ready/valid handshake and naturally supports back-pressure
// When Rename cannot keep up, ready is deasserted and the Decode side is blocked automatically
```

***


## 2.11 Renaming the VCSR.vl Vector-Length Register


VCSR.vl is a **special state register** in the RISC-V vector architecture that records the active length of the current vector instruction. Its renaming logic differs completely from general-purpose registers; it is a form of "state-special renaming."


**Core renaming rules**:


* The vl register is **global state**; it does not use general PRF allocation and is managed with independent state snapshots.

* When a vector instruction modifies vl, hardware saves a dedicated vl snapshot bound to the current ROB entry.

* Normal commit updates global vl state; a flush rollback restores the old vl value from a snapshot.

* The rename stage allocates no physical register; it performs only **state recording and snapshot backup**.

```scala
// From Rename.scala L158
val vlFreeList = Module(new StdFreeList(VlPhyRegs - VlLogicRegs, VlLogicRegs, Reg_Vl, RabCommitWidth, 1))
// vl has only one architectural register, so the physical-register count is also small
// An independent Freelist and rename-table channel decouples vl from general-purpose registers
```


```plain
// 1. Configure vector length vl=16; hardware saves vl snapshot S_vl1
vsetvli t0, 16, e8, m1

// 2. Execute several vector instructions reusing vl=16
vadd.vv v1, v2, v3
vsub.vv v4, v5, v6

// 3. Change vector length to vl=8 and update snapshot S_vl2
vsetvli t0, 8, e8, m1

// 4. Trigger a branch flush; the speculative path is discarded
// 5. Hardware reads old snapshot S_vl1 and restores vl=16 so later vector instructions execute correctly
```

:::warning

**Beginner advice**: First master general-purpose register renaming, the differences among the three architectures, and the core Freelist/snapshot logic. VCSR.vl renaming, bandwidth bottlenecks, and buffer-queue design are advanced engineering topics; understand the concepts first and study the source in depth once the fundamentals are solid.

:::

***


## 2.12 Beginner Exercise: Trace One Instruction Through Rename


Let us trace the complete rename process of one concrete instruction through the source.


### 2.12.1 Exercise: Trace Renaming of `add x10, x1, x2`


**Step 1: The instruction enters Rename**

```scala
// Rename.scala L67
val in = Vec(RenameWidth, Flipped(DecoupledIO(new DecodeOutUop)))
```


The decoded `add x10, x1, x2` enters Rename through the `in` port. It carries source operands x1 and x2 (architectural register numbers), destination x10, and the relevant control signals.


**Step 2: Read source-operand mappings**

```scala
// Rename.scala L77
val intReadPorts = Vec(RenameWidth, Vec(numIntRatPorts, new RatReadPort(log2Ceil(IntLogicRegs))))
```


The rename module uses `intReadPorts` to query the integer rename table for the physical registers currently mapped from x1 and x2. The table returns PR\_x1 and PR\_x2.


**Step 3: Bypass check**


If an earlier instruction in the same cycle also writes x1 or x2, the Bypass logic detects it and forwards the newest PR number directly without a table lookup.


**Step 4: Allocate a new PR for the destination**

```scala
// Rename.scala L154
val intFreeList = Module(new MEFreeList(IntPhyRegs, RabCommitWidth))
```


Take a free PR from the integer Freelist (for example PR50) and assign it to x10. Advance the Freelist head pointer.


**Step 5: Update the rename table**


Write the new x10 -> PR50 mapping into `spec_table`. Record the old x10 mapping (for example PR30) as `old_pdest` for reclamation after commit.


**Step 6: Output the renamed result**

```scala
// Rename.scala L83
val out = Vec(RenameWidth, DecoupledIO(new RenameOutUop))
```


The renamed micro-operation leaves through the `out` port carrying physical register numbers: `psrc(0)=PR_x1`, `psrc(1)=PR_x2`, and `pdest=PR50`.

***


## 2.13 Frequently Asked Questions and Troubleshooting


### Q1: Why are there far more physical registers than architectural registers?


Physical registers must hold temporary results of in-flight instructions, committed but not-yet-reclaimed old values, and intermediate values on speculative paths. If there are too few physical registers, the Freelist becomes empty, Rename stalls, and pipeline throughput drops sharply. XiangShan controls the count of each class with parameters such as `IntPhyRegs` and `FpPhyRegs`.


### Q2: What happens when all snapshots are in use?


Snapshot resources are finite because of hardware-area limits. When all snapshots are occupied, a new branch cannot create one; it must wait for an old snapshot to be freed after branch commit, or use a global flush to roll back to `arch_table`. This is an engineering trade-off between performance and area.


### Q3: How can rename logic be verified?


XiangShan provides a Difftest co-simulation framework that compares RTL architectural state with a reference model such as Spike or NEMU. The rename table also exposes a `debug_rdata` port so `arch_table` can be read in real time for comparison.

***


## 2.14 Tiered Learning Path


🟢 **Must know first**: false dependencies (WAW/WAR elimination), the unified-PRF rename flow, and basic Freelist allocation/reclamation


🔵 **Understand next**: rename-table read/write and Bypass (`RenameTable.scala`), Redirect recovery, Snapshot operation, and ROB compression (`CompressUnit.scala`)


🟣 **Deep dive**: timing optimization for parallel renaming (T0/T1 pipelining), bandwidth optimization, buffering between wide and narrow modules, VCSR.vl renaming, and the differences between the ME and Std Freelist implementations

***


## 2.15 Chapter Summary


✅ **Key points**:


* The core value of register renaming is **eliminating WAW and WAR false dependencies**, unlocking out-of-order execution; true RAW dependencies cannot be eliminated.

* Among the three renaming architectures, XiangShan uses **unified-PRF renaming** for a high-performance out-of-order pipeline.

* XiangShan provides **independent rename tables and Freelists** for five classes: integer, floating-point, vector, v0, and vl.

* The rename table uses a **`spec_table` + `arch_table` two-table design**, separating speculative and architectural state for fast recovery.

* The Freelist is the physical-register **resource manager**; allocation and reclamation must be correct and reclamation cannot happen early.

* Redirect, Snapshot, and ROB compression address fast flushing, precise rollback, and performance optimization, respectively.

* Parallel renaming uses multi-port tables, cascaded Bypass, and a wide Freelist; buffer queues handle upstream/downstream bandwidth mismatch.

* VCSR.vl is a special state register managed with **snapshot-based state**, without physical-register allocation logic.


🎉 **You have completed the register-renaming lesson!** This is one of the key mechanisms for understanding out-of-order processors. Once renaming is clear, you understand the fundamental answer to "why can an out-of-order processor execute out of order?" Next, we will explore how XiangShan schedules, issues, and executes instructions in its pipeline.



> Updated: 2026-06-01 18:20:04

> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/xva8bkbdnhaa3x5g>
