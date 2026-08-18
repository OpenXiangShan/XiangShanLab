<!--
# 4.分发阶段

# 4. Dispatch Queue——从重命名到发射的"分拣中心"

> 如果你是第一次接触处理器分发阶段，看到"Issue Queue路由"、"BusyTable忙闲表"、"LFST负载前馈存储表"这些术语可能会觉得有点头大。别担心——分发本质上就是一件事：把重命名完的指令，按照它的"工种"分拣到对应的车间（Issue Queue），同时给它办好工牌（ROB条目）和登记好原材料状态（源操作数就绪情况）。让我们一步步来，你会发现它就像一个高效的物流分拣中心。

:::info
通过本节学习，你将能够：

* 🧭 理解分发阶段在香山处理器中的**位置与角色**——重命名与发射之间的桥梁
* 📋 掌握 **ROB 分配**——每条指令如何获得顺序提交追踪编号
* 🔍 搞懂 **Issue Queue 路由**——指令如何根据 fuType 被分拣到正确的发射队列
* ⚡ 吃透 **BusyTable 忙闲表**——源操作数就绪状态的初始化与唤醒机制
* 🗺️ 理解 **LFST 负载前馈**——访存指令的依赖信息如何提前传递
* 📦 了解 **LSQ 入队**——Load/Store 指令的专用队列路由
* ✅ 获得**从源码到实践的完整学习路径**

:::

***

## 4.1 分发阶段的全景认知

### 4.1.1 分发阶段在流水线中的位置

你可以把处理器流水线想象成一座**现代化工厂**：

:::info

* **前端（IFU→Decode）** 是**原料采购与翻译部门**——负责取回指令并翻译成微操作
* **重命名** 是**身份证办理处**——给每条指令换上物理寄存器的"真名"
* **分发** 是**分拣中心**——拿着翻译好的工单，给指令分配工位（ROB）、分拣到正确的车间（Issue Queue）、登记原材料到货状态（BusyTable）
* **发射与执行** 是**生产线**——按照工单执行实际运算

:::

没有分发这个"分拣中心"，重命名后的指令就像一群拿到了身份证但不知道该去哪上班的工人——谁该去 ALU 车间？谁该去访存车间？原材料到没到齐？全都不知道。分发就是解决这些问题的枢纽。

### 4.1.2 分发阶段全景图

香山的分发并不是一个简单的"转发模块"，而是一个**多目标路由+状态初始化**的复合子系统：

```plain
┌──────────────── Dispatch（分拣中心）───────────────────────────────────────────┐
│                                                                                │
│  输入：fromRename（来自重命名的 RenameOutUop，RenameWidth 条/周期）              │
│         │                                                                      │
│         ├───────────────┬─────────────────┬──────────────────┐                 │
│         ↓               ↓                 ↓                  ↓                 │
│  ┌────────────┐  ┌────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ ROB 分配    │  │ IQ 路由     │  │ BusyTable    │  │ LSQ 入队     │          │
│  │ (enqRob)   │  │ (fuMapIQIdx)│  │ 源操作数就绪  │  │ (lsqEnqIO)  │          │
│  │            │  │             │  │ 状态初始化    │  │              │          │
│  │ 每条uop获得 │  │ 根据fuType  │  │              │  │ Load/Store   │          │
│  │ ROB追踪编号 │  │ 分拣到对应   │  │ 查询5类      │  │ 专用队列     │          │
│  │            │  │ Issue Queue │  │ BusyTable    │  │              │          │
│  └────────────┘  └────────────┘  └──────────────┘  └──────────────┘          │
│         │               │                 │                  │                 │
│         ↓               ↓                 ↓                  ↓                 │
│  ┌──────────┐  ┌──────────────────────────────────┐  ┌──────────┐           │
│  │  ROB     │  │      各类 Issue Queue              │  │   LSQ    │           │
│  │ (顺序提交)│  │  ALU IQ | FEX IQ | VFEX IQ | ...  │  │ (访存队列)│           │
│  └──────────┘  └──────────────────────────────────┘  └──────────┘           │
│                                                                                │
│  辅助模块：LFST（负载前馈存储表）、RegCacheTagTable、VlBusyTable               │
│                                                                                │
│  关键约束：ROB满 或 任何目标IQ满 → 分发停顿（Stall）                            │
└────────────────────────────────────────────────────────────────────────────────┘
```

> **图表解读**：分发的核心是"三路分发+一表初始化"——ROB分配保证顺序提交、IQ路由保证指令去对车间、BusyTable初始化告诉IQ"原材料到没到"、LSQ入队处理访存指令的专用路径。任何一路不通，整条流水线都得等着。

:::color4
**❤**\*\* 新手建议：\*\*

现阶段你只需记住：**分发 = 分配ROB + 分拣到IQ + 初始化源操作数就绪状态 + 访存指令入LSQ**。不必一开始就纠结 LFST 和 RegCacheTagTable 的细节，先建立整体观，后面逐个击破。

:::

***

## 4.2 ROB 分配——给指令发"工牌"

### 4.2.1 为什么需要 ROB？

乱序处理器允许指令乱序执行，但**必须按程序顺序提交**。ROB（Reorder Buffer）就是保证这一语义的核心数据结构——你可以把它想象成工厂的**工牌管理系统**：每个工人（指令）按入场顺序领一个工牌号，不管他在车间里干得多快，最后都得按工牌号顺序出厂（提交）。

### 4.2.2 源码中的 ROB 分配接口

在 `Dispatch.scala` 中，ROB 分配通过 `enqRob` 端口完成：

```scala
// 来自 Dispatch.scala L109
val enqRob = Flipped(new RobEnqIO)
```

当分发模块接收到重命名后的微操作时，会通过 `enqRob.req` 向 ROB 申请入队。每个请求携带指令的完整信息：

```scala
// 来自 Dispatch.scala L167-L200（调试输出中的字段）
// enqRob.req(i).bits 包含：
// - instr, pc        ：指令编码与PC
// - commitType       ：提交类型
// - fuType, fuOpType ：功能单元与操作码
// - psrc(0), psrc(1) ：源物理寄存器
// - pdest            ：目的物理寄存器
// - robIdx           ：ROB编号（由ROB分配后返回）
// - numUops, numWB   ：微操作数与写回数
```

### 4.2.3 ROB 分配的关键约束

**ROB 满时分发必须停顿**。在源码中，ROB 的 ready 信号与所有 Issue Queue 的 ready 信号共同决定分发能否继续：

```scala
// 来自 Dispatch.scala L324
io.toRenameAllFire := io.fromRename.map(x => !x.valid || x.fire).reduce(_ && _)
```

`toRenameAllFire` 表示所有 Rename→Dispatch 的握手都成功。只有当 ROB 有空位、目标 IQ 有空位时，这个信号才为真，重命名模块才不会停顿。

> 类比：就像工厂的入场闸机——工牌发完了（ROB满）或者某个车间满员了（IQ满），闸机就得关上，新工人进不来。

***

## 4.3 Issue Queue 路由——指令该去哪个车间？

### 4.3.1 为什么需要路由？

重命名后的微操作携带了 `fuType`（功能单元类型）信息，但不同的功能单元挂接在不同的 Issue Queue 上。分发模块的核心职责之一就是**根据 fuType 把指令路由到正确的 Issue Queue**。

这就像快递分拣中心——标着"电子产品"的包裹走 A 通道，标着"食品"的走 B 通道。分错了，包裹就永远送不到。

### 4.3.2 fuMapIQIdx——功能单元到 Issue Queue 的映射表

香山在 `Dispatch.scala` 中构建了一张关键的映射表 `fuMapIQIdx`，记录每种 FuConfig 应该路由到哪些 Issue Queue：

```scala
// 来自 Dispatch.scala L64-L68
val fuMapIQIdx = sortedFuConfigs.map( fu => {
  val fuInIQIdx = fuConfigsInIssueParams.zipWithIndex.filter { case (f, i) => f.contains(fu) }.map(_._2)
  (fu -> fuInIQIdx)
})
```

**这段代码的逻辑**：

1. 遍历所有排序后的 FuConfig
2. 对于每个 FuConfig，检查哪些 Issue Queue 包含该功能单元
3. 记录映射关系：`FuConfig → [IQ编号列表]`

### 4.3.3 needMultiIQ vs needSingleIQ——单队列与多队列路由

有些功能单元只存在于一个 Issue Queue 中（单队列路由），有些存在于多个 Issue Queue 中（多队列路由，需要负载均衡选择）：

```scala
// 来自 Dispatch.scala L75-L76
val needMultiIQ = sameIQIdxFus.sortBy(_._1.head.fuType.id).filter(_._2.size > 1)
val needSingleIQ = sameIQIdxFus.sortBy(_._1.head.fuType.id).filter(_._2.size == 1)
```

| **类别** | **含义** | **路由策略** | **典型示例** |
| --- | --- | --- | --- |
| **needSingleIQ** | 功能单元只存在于1个IQ | 直接路由，无需选择 | 某些专用的除法/加密单元 |
| **needMultiIQ** | 功能单元存在于多个IQ | 需要负载均衡选择 | ALU（存在于6个IQ中）、乘法器 |

> 类比：单队列路由就像只有一个窗口的银行——直接排队就行；多队列路由就像有6个窗口的银行——你得选一个最短的队。

### 4.3.4 多队列负载均衡——谁空选谁

对于 `needMultiIQ` 的功能单元，分发模块通过**比较各 IQ 的已占用条目数**来选择最空闲的队列：

```scala
// 来自 Dispatch.scala L590-L596
val compareMatrix = Wire(Vec(iqNum, Vec(iqNum, Bool())))
for (i <- 0 until iqNum) {
  for (j <- 0 until iqNum) {
    if (i == j) compareMatrix(i)(j) := false.B
    else if (i < j) compareMatrix(i)(j) := issueQueueCountAddEnq(exuidx(i)) < issueQueueCountAddEnq(exuidx(j))
    else compareMatrix(i)(j) := !compareMatrix(j)(i)
  }
}
```

**负载均衡算法**：

1. 构建一个 `iqNum × iqNum` 的比较矩阵，`compareMatrix(i)(j)=true` 表示第i个IQ比第j个IQ更空
2. 比较依据是 `issueQueueCountAddEnq`——IQ当前已占用数 + 即将入队数的总和
3. 选择最空的 IQ 作为路由目标

`issueQueueCountAddEnq` 的计算考虑了当前 IQ 的实际占用和本周期即将入队的指令数量：

```scala
// 来自 Dispatch.scala L582-L583
val issueQueueCount = VecInit(io.IQValidNumVec.zip(needAppendIQValidNumVec).map(x => RegNext(x._1 + x._2)))
val issueQueueCountAddEnq = VecInit(issueQueueCount.zip(needAppendIQValidNumVec).map(x => x._1 + x._2))
```

### 4.3.5 香山南湖的 Issue Queue 配置

从源码的调试输出中，我们可以看到香山南湖架构的完整 Issue Queue 配置：

```scala
// 来自 Dispatch.scala L293-L311（调试输出整理）
Queue 0  : issueQueueALU0  — Alu, Csr, Fence, Brh, Jmp
Queue 1  : issueQueueALU1  — Alu, Div, Brh, Jmp
Queue 2  : issueQueueALU2  — Alu, I2F, Vsetriwi, Vsetriwv, fl2v, Brh, Jmp
Queue 3  : issueQueueALU3  — Alu, Bku
Queue 4  : issueQueueALU4  — Alu, Mul
Queue 5  : issueQueueALU5  — Alu, Mul
Queue 6  : issueQueueLDU0  — Ldu
Queue 7  : issueQueueLDU1  — Ldu
Queue 8  : issueQueueLDU2  — Ldu
Queue 9  : issueQueueSTA0  — Sta, Mou  +  issueQueueSTD0 — Std, Moud
Queue 10 : issueQueueSTA1  — Sta, Mou  +  issueQueueSTD1 — Std, Moud
Queue 11 : issueQueueFEX0  — Falu, Fmac, Fcvt, Fcmp, F2v
Queue 12 : issueQueueFEX1  — Falu, Fmac, Fdiv
Queue 13 : issueQueueFEX2  — Falu, Fmac, Fdiv
Queue 14 : issueQueueFEX3  — Falu, Fmac
Queue 15 : issueQueueVFEX0 — VialuFix, Falu, Fvma, Vimac, Vppu, Vipu, VFcvt, Vsetrvf, FvMove
Queue 16 : issueQueueVFEX1 — VialuFix, Falu, Fvma, Vfdiv, Vvid
Queue 17 : issueQueueVLSU0 — Vldu, Vstu, Vsegldu, Vsegstu
Queue 18 : issueQueueVLSU1 — Vldu, Vstu
```

> **图表解读**：可以看到 ALU 类型出现在 Queue 0~5 共6个 IQ 中（典型的 needMultiIQ），而 LDU 只出现在 Queue 6~8。这种"多副本+负载均衡"的设计，是为了让最频繁的整数运算指令不因为 IQ 满而停顿。

***

## 4.4 BusyTable 忙闲表——源操作数到没到货？

### 4.4.1 为什么需要 BusyTable？

在重命名阶段，我们知道了每条指令的源操作数对应哪个物理寄存器。但**物理寄存器里有数据了吗？** 如果前序指令还没写回结果，源操作数就是"还没到货"的状态。Issue Queue 需要知道每个源操作数的就绪状态，才能决定什么时候发射指令。

BusyTable 就是记录**每个物理寄存器是否已有有效数据**的表格。你可以把它想象成快递追踪系统——标注每个包裹"已送达"还是"在途中"。

### 4.4.2 五类 BusyTable 的实例化

香山为五类寄存器分别实例化了独立的 BusyTable：

```scala
// 来自 Dispatch.scala L402-L407
val intBusyTable  = Module(new BusyTable(numRegSrcInt * renameWidth, backendParams.numPregWb(IntData()),  IntPhyRegs,  IntWB()))
val fpBusyTable   = Module(new BusyTable(numRegSrcFp  * renameWidth, backendParams.numPregWb(FpData()),   FpPhyRegs,   FpWB()))
val vecBusyTable  = Module(new BusyTable(numRegSrcVf  * renameWidth, backendParams.numPregWb(VecData()),  VfPhyRegs,   VfWB()))
val v0BusyTable   = Module(new BusyTable(numRegSrcV0  * renameWidth, backendParams.numPregWb(V0Data()),   V0PhyRegs,   V0WB()))
val vlBusyTable   = Module(new VlBusyTable(numRegSrcVl * renameWidth, backendParams.numPregWb(VlData()), VlPhyRegs,   VlWB()))
```

| **BusyTable** | **管理对象** | **读端口数** | **写回端口来源** |
| --- | --- | --- | --- |
| intBusyTable | 整数物理寄存器 | numRegSrcInt × RenameWidth | IntWB（整数写回） |
| fpBusyTable | 浮点物理寄存器 | numRegSrcFp × RenameWidth | FpWB（浮点写回） |
| vecBusyTable | 向量物理寄存器 | numRegSrcVf × RenameWidth | VfWB（向量写回） |
| v0BusyTable | v0掩码物理寄存器 | numRegSrcV0 × RenameWidth | V0WB |
| vlBusyTable | vl长度物理寄存器 | numRegSrcVl × RenameWidth | VlWB（特殊实现） |

> 类比：五类 BusyTable 就像五个独立的快递追踪系统——国内快递、国际快递、冷链、易碎品、特殊件，各有各的追踪逻辑，互不干扰。

### 4.4.3 BusyTable 的两个核心操作：分配与唤醒

**1. 分配（Alloc）——新指令写目的寄存器时，标记为"忙"**

当一条指令在重命名阶段分配了新的目的物理寄存器，该 PR 的 BusyTable 状态被标记为\*\*"忙"（busy=true）\*\*——因为这条指令还没执行完，数据还没写回来。

```scala
// 来自 Dispatch.scala L412-L423
val allocPregsValid = Wire(Vec(busyTables.size, Vec(RenameWidth, Bool())))
allocPregsValid(0) := VecInit(fromRename.map(x => x.valid && x.bits.rfWen && !x.bits.isMove))
//                                                ^^^^^^^^^^^^^^^^^^^^^^^^^^ 注意：Move消除不标记忙！
allocPregsValid(1) := VecInit(fromRename.map(x => x.valid && x.bits.fpWen))
allocPregsValid(2) := VecInit(fromRename.map(x => x.valid && x.bits.vecWen))
allocPregsValid(3) := VecInit(fromRename.map(x => x.valid && x.bits.v0Wen))

val allocPregs = Wire(Vec(busyTables.size, Vec(RenameWidth, ValidIO(UInt(PhyRegIdxWidth.W)))))
allocPregs.zip(allocPregsValid).map(x => {
  x._1.zip(x._2).zipWithIndex.map{case ((sink, source), i) => {
    sink.valid := source
    sink.bits := fromRename(i).bits.pdest  // 新分配的目的PR号
  }}
})
```

**关键细节**：整数 BusyTable 的 allocPregsValid 中有 `!x.bits.isMove`——Move 消除指令不分配新 PR，自然也不需要将其目的 PR 标记为忙，因为源 PR 的数据早就就绪了。

**2. 唤醒（Wake Up）——指令写回时，标记为"就绪"**

当一条指令执行完毕写回结果时，其目的 PR 的 BusyTable 状态被更新为\*\*"就绪"（busy=false）\*\*。所有等待该 PR 的 Issue Queue 表项都能收到唤醒信号。

```scala
// 来自 Dispatch.scala L424-L433
val wakeUp = io.wakeUpAll.wakeUpInt ++ io.wakeUpAll.wakeUpFp ++ io.wakeUpAll.wakeUpVec
busyTables.zip(wbPregs).zip(allocPregs).map{ case ((b, w), a) => {
  b.io.wakeUpInt := io.wakeUpAll.wakeUpInt   // 整数写回唤醒
  b.io.wakeUpFp  := io.wakeUpAll.wakeUpFp    // 浮点写回唤醒
  b.io.wakeUpVec := io.wakeUpAll.wakeUpVec   // 向量写回唤醒
  b.io.og0Cancel := io.og0Cancel             // 取消信号
  b.io.ldCancel  := io.ldCancel              // Load取消信号
  b.io.wbPregs   := w                         // 写回端口号
  b.io.allocPregs := a                        // 分配端口号
}}
```

**唤醒的三路来源**：整数写回、浮点写回、向量写回。每个 BusyTable 都接收全部三路唤醒信号，确保跨类型的依赖也能被正确唤醒（例如整数指令依赖浮点结果）。

### 4.4.4 BusyTable 读取——查询源操作数就绪状态

分发阶段通过 BusyTable 的读端口查询源操作数的就绪状态，并将结果传递给 Issue Queue：

```scala
// 来自 Dispatch.scala L461-L496（简化版）
busyTables.zip(idxRegType).zipWithIndex.map { case ((b, idxseq), i) => {
  // 构建读地址：从 fromRename 中提取属于该类寄存器的 psrc 编号
  val readAddr = VecInit(fromRename.map(x =>
    x.bits.psrc.zipWithIndex.filter(xx => idxseq.contains(xx._2)).map(_._1)
  ).flatten)
  // 构建读使能：仅当源操作数类型匹配时才读
  val readValid = VecInit(fromRename.map(x =>
    x.bits.psrc.zipWithIndex.filter(xx => idxseq.contains(xx._2))
      .map(y => x.valid && SrcType.isXp(x.bits.srcType(y._2)))
  ).flatten)
  // 连接读端口
  b.io.read.map(_.req).zip(readAddr).map(x => x._1 := x._2)
}}
```

**读取逻辑的关键**：

1. 根据寄存器类型（int/fp/vec/v0）从 `psrc` 中筛选出对应的源操作数编号
2. 只有当源操作数类型匹配（如整数源读 intBusyTable）时才发起读请求
3. 读结果（`busyTables(k).io.read(readidx).resp`）为 true 表示该源操作数**已就绪**

### 4.4.5 allSrcState——源操作数就绪状态汇总

分发模块将五类 BusyTable 的读结果汇总为统一的 `allSrcState`，传递给 Issue Queue：

```scala
// 来自 Dispatch.scala L497-L519
val allSrcState = Wire(Vec(renameWidth, Vec(numRegSrc, Vec(numRegType, Bool()))))
for (i <- 0 until renameWidth){
  for (j <- 0 until numRegSrc){
    for (k <- 0 until numRegType){
      if (!idxRegType(k).contains(j)) {
        allSrcState(i)(j)(k) := false.B
      } else {
        val readEn = k match {
          case 0 => SrcType.isXp(fromRename(i).bits.srcType(j))  // 整数源
          case 1 => SrcType.isFp(fromRename(i).bits.srcType(j))  // 浮点源
          case 2 => SrcType.isVp(fromRename(i).bits.srcType(j))  // 向量源
          case 3 => SrcType.isV0(fromRename(i).bits.srcType(j))  // v0源
        }
        // 源就绪 = (是该类型 && BusyTable返回就绪) || 是立即数
        allSrcState(i)(j)(k) := readEn && busyTables(k).io.read(readidx).resp
                              || SrcType.isImm(fromRename(i).bits.srcType(j))
      }
    }
  }
  // vl 源单独处理
  allSrcStateVl(i) := vlBusyTable.io.read(i).resp || !fromRename(i).bits.vlRen
}
```

**核心公式**：`源操作数就绪 = (类型匹配 && BusyTable返回true) || 是立即数`

立即数天然就绪（不依赖寄存器），所以 `SrcType.isImm` 直接返回 true。

:::color4
**❤**\*\* 新手建议：\*\*

BusyTable 的核心逻辑就一句话：**新分配的目的PR标记为忙，写回后标记为就绪，读查询告诉IQ源操作数到没到货**。多类型、多端口的细节都是这个核心逻辑的工程实现。

:::

***

## 4.5 LSQ 入队——访存指令的专用通道

### 4.5.1 为什么访存指令需要特殊处理？

Load/Store 指令除了进入普通的 Issue Queue 外，还需要进入**LSQ（Load-Store Queue）**——因为访存指令涉及地址依赖、内存一致性、Store-to-Load 前递等复杂逻辑，需要专门的队列来管理。

### 4.5.2 源码中的 LSQ 入队接口

```scala
// 来自 Dispatch.scala L146-L147
val toMem = new Bundle {
  val lsqEnqIO = Flipped(new LsqEnqIO)
}
```

分发模块通过 `lsqEnqIO` 向 LSQ 发送入队请求。与普通 IQ 不同的是，LSQ 入队需要额外的信息：

```scala
// 来自 Dispatch.scala L204-L241（调试输出中的LSQ字段）
// lsqEnqIO.req(i).bits 包含：
// - instr, pc       ：指令编码与PC
// - fuType, fuOpType：功能单元与操作码
// - psrc, pdest     ：物理寄存器号
// - robIdx          ：ROB编号
// - sqIdx           ：Store Queue编号（由LSQ分配后返回）
// - needAlloc       ：是否需要分配LSQ条目（0=不需要, 1=Load, 2=Store）
// - canAccept       ：LSQ是否有空位
```

### 4.5.3 LSQ 与 IQ 的双重入队

访存指令在分发阶段会**同时进入 IQ 和 LSQ**——IQ 负责调度发射，LSQ 负责管理访存依赖和一致性。只有当 IQ 和 LSQ 都有空位时，访存指令才能成功入队。

> 类比：就像去银行办贷款——你得同时在"业务窗口"（IQ）和"风控系统"（LSQ）都挂号成功，才能开始办理。

***

## 4.6 LFST——负载前馈存储表

### 4.6.1 为什么需要 LFST？

在乱序处理器中，Load 指令可能依赖于前序 Store 指令的地址计算结果。如果分发阶段能提前知道这种依赖关系，就能在 Issue Queue 中标记 Load 指令的源操作数依赖状态，避免 Load 盲目发射后再取消。

LFST（Load-Forward-Store-Table）就是香山用来在分发阶段**提前传递 Store→Load 依赖信息**的专用模块。

```scala
// 来自 Dispatch.scala L153
val lfst = new DispatchLFSTIO
```

### 4.6.2 LFST 的依赖信息传递

在源码中，LFST 的依赖信息通过 BusyTable 的 `loadDependency` 读端口传递给 Issue Queue：

```scala
// 来自 Dispatch.scala L466-L486
val srcLoadDependencyUpdate = fromRenameUpdate.map(x => x.bits.srcLoadDependency)
val srcType = fromRenameUpdate.map(x => x.bits.srcType)
srcLoadDependencyUpdate.zip(srcType).zipWithIndex.foreach {
  case ((sinks, srcTypes), uopIdx) =>
    for (srcidx <- 0 until 3) {
      val sink = sinks(srcidx)
      val srcType = srcTypes(srcidx)
      val fpRead = busyTables(1).io.read(uopIdx * 3 + srcidx).loadDependency
      if (srcidx < 2) {
        val intRead = busyTables(0).io.read(uopIdx * 2 + srcidx).loadDependency
        sink := Mux1H(Seq(
          SrcType.isFp(srcType) -> fpRead,
          SrcType.isXp(srcType) -> intRead,
        ))
      } else {
        sink := Mux(SrcType.isFp(srcType), fpRead, 0.U.asTypeOf(sink))
      }
    }
}
```

**依赖信息的选择逻辑**：根据源操作数的类型（整数/浮点），从对应的 BusyTable 读端口获取 `loadDependency`，传递给下游的 Issue Queue。这告诉 IQ："这个源操作数虽然就绪了，但它可能依赖一个未完成的 Store，发射时需要特别注意。"

***

## 4.7 RegCacheTagTable——寄存器缓存标签管理

### 4.7.1 什么是 RegCache？

香山为了优化整数寄存器的数据旁路延迟，引入了 **RegCache**——一种小型的寄存器缓存机制。RegCacheTagTable 负责管理 RegCache 的标签信息，在分发阶段为整数源操作数读取 RegCache 标签。

```scala
// 来自 Dispatch.scala L400-L401
val rcTagTable = Module(new RegCacheTagTable(numRegSrcInt * renameWidth))
```

### 4.7.2 RegCacheTagTable 的读取连接

```scala
// 来自 Dispatch.scala L453-L459
rcTagTable.io.allocPregs.zip(allocPregs(0)).map(x => x._1 := x._2)   // 分配时更新标签
rcTagTable.io.wakeupFromIQ := io.wakeUpAll.wakeUpInt                    // IQ唤醒时更新标签
rcTagTable.io.og0Cancel := io.og0Cancel                                 // 取消信号
rcTagTable.io.ldCancel := io.ldCancel                                   // Load取消信号
```

```scala
// 来自 Dispatch.scala L489-L494
// 仅为整数源操作数读取 RegCache 标签
val rcTagUpdate = fromRenameUpdate.map(x =>
  x.bits.regCacheIdx.zipWithIndex.filter(x => idxseq.contains(x._2)).map(_._1)
).flatten
rcTagUpdate.zip(rcTagTable.io.readPorts.map(_.addr)).map(x => x._1 := x._2)
```

> **RegCache 是一个进阶优化机制**，初学者了解其存在即可。核心思想是：某些频繁使用的整数寄存器值可以缓存在离执行单元更近的位置，减少从寄存器堆读取的延迟。

***

## 4.8 向量源操作数的特殊处理——忽略旧 vd

### 4.8.1 为什么向量指令需要特殊处理？

向量指令的源操作数中有一个特殊的"旧 vd"（old destination vector register）——某些向量指令需要读取目的寄存器的旧值进行合并操作。但在很多情况下（如 tail 不可见、mask 全1），旧 vd 的值实际上不会被使用，此时可以将其标记为"已就绪"，避免不必要的等待。

```scala
// 来自 Dispatch.scala L521-L542
val ignoreOldVdVec = Wire(Vec(renameWidth, Bool()))
for (i <- 0 until renameWidth){
  val isDependOldVd = fromRename(i).bits.vpu.isDependOldVd
  val isWritePartVd = fromRename(i).bits.vpu.isWritePartVd
  val vta = fromRename(i).bits.vpu.vta
  val vma = fromRename(i).bits.vpu.vma
  val vm = fromRename(i).bits.vpu.vm
  val vlIsVlmax = vlBusyTable.io_vl_read.vlReadInfo(i).is_vlmax
  val vlIsNonZero = vlBusyTable.io_vl_read.vlReadInfo(i).is_nonzero
  val ignoreTail = vlIsVlmax && (vm =/= 0.U || vma) && !isWritePartVd
  val ignoreWhole = (vm =/= 0.U || vma) && vta
  val ignoreOldVd = vlBusyTable.io.read(i).resp && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
  ignoreOldVdVec(i) := readEn && ignoreOldVd
  // 如果可以忽略旧vd，则标记为就绪
  allSrcState(i)(j)(k) := readEn && (busyTables(k).io.read(readidx).resp || ignoreOldVd)
                        || SrcType.isImm(fromRename(i).bits.srcType(j))
}
```

**忽略旧 vd 的条件**：

1. vl 已就绪且非零
2. 指令不依赖旧 vd 值（`!isDependOldVd`）
3. 满足忽略 tail 或忽略整体的条件（与 `vta`、`vma`、`vm` 相关）

> 类比：就像你做一道菜需要"上次的剩菜"作为原料——但如果确认用不上剩菜（比如配方改了），那就不用等剩菜送达，直接开始做就行。

***

## 4.9 分发停顿条件分析

### 4.9.1 三大停顿来源

分发模块是流水线中**最容易成为瓶颈的阶段之一**，因为它需要同时满足多个下游模块的接受条件：

| **停顿来源** | **触发条件** | **影响** |
| --- | --- | --- |
| **ROB 满** | ROB 无空余条目 | 所有指令无法入队，流水线完全停顿 |
| **目标 IQ 满** | 指令对应的 Issue Queue 无空位 | 该类指令停顿，其他类指令可能继续 |
| **LSQ 满** | Load/Store Queue 无空位 | 访存指令停顿，非访存指令可能继续 |

### 4.9.2 源码中的停顿传播

```scala
// 来自 Dispatch.scala L324
io.toRenameAllFire := io.fromRename.map(x => !x.valid || x.fire).reduce(_ && _)
```

`toRenameAllFire` 是分发向重命名模块反馈的信号——只有当所有有效指令都成功握手（fire）时才为真。如果任何一条指令因为下游满而无法分发，这个信号就为假，重命名模块会停顿，进而前端译码也会停顿——**背压（back-pressure）从后向前传播**。

> 类比：就像高速公路上的拥堵——最前方的收费站（IQ/ROB）满了，后面的车（指令）一辆接一辆停下来，很快整条高速（流水线）都堵了。

***

## 4.10 分发与重命名的数据更新

### 4.10.1 fromRenameUpdate——修正重命名输出

分发模块不仅接收重命名的输出，还需要**对部分字段进行修正**后再传递给下游：

```scala
// 来自 Dispatch.scala L325-L342
val fromRenameUpdate = Wire(Vec(RenameWidth, Flipped(ValidIO(new DispatchUpdateUop))))
for (i <- 0 until RenameWidth) {
  fromRenameUpdate(i).valid := fromRename(i).valid
  // v0 不需要 srcLoadDependency，srcState 通过 allSrcState 更新
  fromRenameUpdate(i).bits.srcLoadDependency(3) := 0.U.asTypeOf(...)
  fromRenameUpdate(i).bits.srcState := 0.U.asTypeOf(...)
  fromRenameUpdate(i).bits.srcStateVl := 0.U
  connectSamePort(fromRenameUpdate(i).bits, fromRename(i).bits)
  // 修正 ftqOffset：分支/Store压缩时，应使用最后一条指令的偏移
  fromRenameUpdate(i).bits.ftqOffset := fromRename(i).bits.ftqLastOffset
  // 修正 ftqPtr：跨FTQ行时需要+1
  fromRenameUpdate(i).bits.ftqPtr := fromRename(i).bits.ftqPtr + fromRename(i).bits.crossFtq
  // 修正 isRVC：使用最后一条指令的压缩标记
  fromRenameUpdate(i).bits.isRVC := fromRename(i).bits.lastIsRVC
}
```

**需要修正的关键字段**：

* `ftqOffset`：ROB 压缩时，一条 ROB 条目包含多条指令，需要使用最后一条指令的 FTQ 偏移
* `ftqPtr`：跨 FTQ 行时需要调整指针
* `srcState`：由 BusyTable 的读结果覆盖（重命名阶段无法知道就绪状态）
* `srcLoadDependency`：由 LFST 逻辑填充

***

## 4.11 SingleStep 调试模式下的特殊分发逻辑

### 4.11.1 为什么需要 SingleStep 特殊处理？

在调试模式下，处理器需要在每条机器指令提交后暂停。但由于 ROB 压缩，一条机器指令可能对应多个微操作。分发模块需要精确追踪哪条微操作属于当前"步进"的机器指令。

```scala
// 来自 Dispatch.scala L545-L568
val s_holdRobidx :: s_updateRobidx :: Nil = Enum(2)
val singleStepState = RegInit(s_updateRobidx)

when(!io.singleStep) {
  singleStepState := s_updateRobidx
}.elsewhen(io.singleStep && fromRename(0).fire && io.enqRob.req(0).valid) {
  singleStepState := s_holdRobidx
  robidxStepHold := fromRename(0).bits.robIdx
}
```

**状态机**：

* `s_updateRobidx`：正常状态，更新步进 ROB 编号
* `s_holdRobidx`：持有当前步进的 ROB 编号，等待提交后暂停

> 这是调试相关的进阶内容，初学者了解即可。

***

## 4.12 新手实战：追踪一条 ADD 指令的分发过程

### 4.12.1 实战任务：追踪 `add x10, x1, x2` 的分发流程

**步骤1：接收重命名输出**

```scala
// Dispatch.scala L106
val fromRename = Vec(RenameWidth, Flipped(DecoupledIO(new RenameOutUop)))
```

假设 `add x10, x1, x2` 重命名后：psrc(0)=PR5, psrc(1)=PR8, pdest=PR50, fuType=ALU。

**步骤2：ROB 分配**

```scala
// Dispatch.scala L109
val enqRob = Flipped(new RobEnqIO)
```

向 ROB 申请一个条目，获得 robIdx。ROB 记录这条指令的提交追踪信息。

**步骤3：BusyTable 查询源操作数就绪状态**

* 查询 intBusyTable：PR5 是否就绪？PR8 是否就绪？
* 假设 PR5 已写回（就绪），PR8 未写回（忙）
* allSrcState = \[true, false]（源1就绪，源2未就绪）

**步骤4：Issue Queue 路由**

* fuType=ALU → fuMapIQIdx 查找 → ALU 存在于 Queue 0~5
* needMultiIQ → 负载均衡选择 → 假设选择 Queue 2（最空闲）

**步骤5：写入 Issue Queue**

```scala
// Dispatch.scala L112
val toIssueQueues = Vec(IQEnqSum, DecoupledIO(new DispatchOutUop))
```

指令写入 Queue 2，携带：psrc=\[PR5, PR8], pdest=PR50, robIdx, srcState=\[ready, busy]。

**步骤6：等待唤醒**

后续某条指令写回 PR8 → intBusyTable 唤醒 → Issue Queue 中该条目的源2变为就绪 → 指令可以被发射。

### 4.12.2 对比：Load 指令的分发流程

对于 `lw x10, 0(x5)`：

1. 重命名后：psrc(0)=PR5（基址寄存器）, pdest=PR50, fuType=LDU
2. ROB 分配
3. **LSQ 入队**：Load 指令需要在 LSQ 中分配一个 LQ 条目
4. BusyTable 查询：PR5 是否就绪？
5. IQ 路由：LDU → Queue 6~8（Load 专用 IQ）
6. **双重依赖**：IQ 和 LSQ 都必须有空位，指令才能入队

***

## 4.13 常见问题与排错指南

### Q1：分发停顿时，如何判断是哪个模块造成的？

香山的停顿原因追踪系统（`stallReason`）可以记录每个周期的停顿原因：

```scala
// 来自 Dispatch.scala L157
val stallReason = Flipped(new StallReasonIO(RenameWidth))
```

通过分析 `stallReason` 的输出，可以精确定位是 ROB 满、某个 IQ 满、还是 LSQ 满导致的停顿。

### Q2：为什么 Move 消除指令在 BusyTable 中不标记忙？

```scala
// 来自 Dispatch.scala L413
allocPregsValid(0) := VecInit(fromRename.map(x => x.valid && x.bits.rfWen && !x.bits.isMove))
```

Move 消除指令的 pdest 等于源操作数的 PR，数据早已就绪。如果将其标记为忙，下游 IQ 会错误地认为该 PR 没有有效数据，导致依赖它的指令永远无法发射。

### Q3：多队列路由的负载均衡会不会增加延迟？

会。比较矩阵（`compareMatrix`）的组合逻辑深度为 O(iqNum²)，在高频率设计中可能成为时序瓶颈。香山通过打拍（`IQSort` 使用 Reg）来缓解时序压力。

***

## 4.14 分级学习路径指引

🟢 **入门必掌握**：分发的三大职责（ROB分配、IQ路由、BusyTable初始化）、停顿条件、背压传播机制

🔵 **进阶需理解**：fuMapIQIdx 路由映射、needMultiIQ 负载均衡算法、BusyTable 的分配/唤醒/读取源码实现、LSQ 双重入队

🟣 **精通深挖**：LFST 依赖前递机制、RegCacheTagTable 标签管理、向量旧vd忽略优化、SingleStep 调试分发逻辑、多IQ负载均衡的时序优化

***

## 4.15 本章总结

✅ **核心知识点回顾**：

* 分发是重命名与发射之间的**桥梁**，负责三大核心任务：**ROB分配、IQ路由、源操作数就绪状态初始化**
* **ROB 分配**保证乱序执行后的顺序提交语义；ROB 满则流水线停顿
* **IQ 路由**通过 `fuMapIQIdx` 映射表将指令分拣到正确的 Issue Queue；多队列路由采用**负载均衡算法**选择最空闲的 IQ
* **BusyTable** 记录物理寄存器的忙闲状态：新分配的PR标记为忙，写回后唤醒为就绪；五类寄存器各有独立的 BusyTable
* **Move 消除指令**在 BusyTable 中**不标记忙**，因为其 pdest 共享源 PR 的数据
* **LSQ 入队**是访存指令的专用通道，需要 IQ 和 LSQ 双重就绪
* **LFST** 提前传递 Store→Load 依赖信息，避免 Load 盲目发射
* 分发停顿来源于**ROB满、目标IQ满、LSQ满**，通过背压机制向前端传播

🎉 **恭喜你完成了分发阶段的学习！** 现在你已经理解了指令从"拿到物理身份证"到"进入车间排队"的完整过程。分发模块看似只是一个"分拣中心"，但其中的路由逻辑、状态初始化、负载均衡和停顿控制，每一项都是高性能处理器设计中不可忽视的工程挑战。接下来，让我们继续探索 Issue Queue 中指令是如何被调度和发射执行的。

***

接下来建议你继续阅读：

* [香山乱序流水线设计](16-xiangshan-ooo-pipeline-design)
* [指令动态执行分析](17-instruction-dynamic-execution-analysis)
* [Bug分类与分析](21-bug-classification-and-analysis)


> 更新: 2026-06-02 10:56:42  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/rx0ps1qslgayqqlb>
-->

# 4. Dispatch Stage

# 4. Dispatch Queue — the "sorting center" between rename and issue

> If this is your first time studying the dispatch stage of a processor, terms such as "Issue Queue routing," "BusyTable," and "LFST" (the load-forwarding store table) may look intimidating. Do not worry: dispatch has one essential job. It sorts renamed instructions into the workshop (Issue Queue) for their "trade," gives each one an employee badge (a ROB entry), and records whether its raw materials (source operands) are ready. Step by step, it looks much like an efficient logistics sorting center.

:::info
By the end of this section, you will be able to:

* 🧭 Understand the **position and role** of dispatch in the XiangShan processor — the bridge between rename and issue
* 📋 Understand **ROB allocation** — how each instruction obtains an in-order-commit tracking number
* 🔍 Understand **Issue Queue routing** — how instructions are sorted into the correct issue queue according to `fuType`
* ⚡ Understand **BusyTable** — initialization and wake-up of source-operand readiness
* 🗺️ Understand **LFST forwarding** — how dependency information for memory instructions is passed ahead of time
* 📦 Understand **LSQ enqueue** — the dedicated queue path for Load/Store instructions
* ✅ Follow a complete learning path from source code to hands-on practice

:::

***

## 4.1 Dispatch-stage overview

### 4.1.1 Where dispatch sits in the pipeline

You can picture the processor pipeline as a **modern factory**:

:::info

* The **front end (IFU→Decode)** is the **raw-material procurement and translation department** — it fetches instructions and translates them into micro-operations
* **Rename** is the **ID office** — it gives every instruction the "real name" of a physical register
* **Dispatch** is the **sorting center** — it assigns a workstation (ROB), sends the instruction to the correct workshop (Issue Queue), and records raw-material arrival status (BusyTable)
* **Issue and execute** are the **production line** — they perform the actual operation described by the work order

:::

Without this sorting center, renamed instructions would be workers who have received identity cards but do not know where to report for work. Which one belongs in the ALU workshop? Which one belongs in the memory workshop? Have all materials arrived? Dispatch is the hub that resolves these questions.

### 4.1.2 Dispatch-stage panorama

XiangShan dispatch is not a simple forwarding module. It is a compound subsystem that performs **multi-destination routing and state initialization**:

```plain
┌──────────────── Dispatch (sorting center) ─────────────────────────────────────┐
│                                                                                │
│  Input: fromRename (RenameOutUop from rename, RenameWidth entries/cycle)       │
│         │                                                                      │
│         ├───────────────┬─────────────────┬──────────────────┐                 │
│         ↓               ↓                 ↓                  ↓                 │
│  ┌────────────┐  ┌────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ ROB alloc  │  │ IQ routing │  │ BusyTable    │  │ LSQ enqueue  │          │
│  │ (enqRob)   │  │ (fuMapIQIdx)│ │ source ready │  │ (lsqEnqIO)   │          │
│  │            │  │             │ │ state init   │  │              │          │
│  │ each uop   │  │ sort by     │ │ query five   │  │ dedicated    │          │
│  │ gets a ROB │  │ fuType into │ │ BusyTables   │  │ Load/Store   │          │
│  │ tracking ID│  │ its IQ      │ │              │  │ queue        │          │
│  └────────────┘  └────────────┘  └──────────────┘  └──────────────┘          │
│         │               │                 │                  │                 │
│         ↓               ↓                 ↓                  ↓                 │
│  ┌──────────┐  ┌──────────────────────────────────┐  ┌──────────┐           │
│  │  ROB     │  │       Issue Queues                │  │   LSQ    │           │
│  │ (in-order │  │  ALU IQ | FEX IQ | VFEX IQ | ... │  │ (memory  │           │
│  │ commit)  │  │                                  │  │ queues)  │           │
│  └──────────┘  └──────────────────────────────────┘  └──────────┘           │
│                                                                                │
│  Helper modules: LFST (load-forwarding store table), RegCacheTagTable,        │
│                  VlBusyTable                                                   │
│                                                                                │
│  Key constraint: ROB full or any target IQ full → dispatch stalls              │
└────────────────────────────────────────────────────────────────────────────────┘
```

> **Reading the diagram**: dispatch has three output paths plus one state-initialization path. ROB allocation guarantees in-order commit, IQ routing sends instructions to the right workshop, BusyTable initialization tells the IQ whether the materials have arrived, and LSQ enqueue handles the dedicated memory path. If any path is blocked, the whole pipeline must wait.

:::color4
**❤** **Beginner tip:**

For now, remember only this: **dispatch = allocate a ROB entry + sort into an IQ + initialize source readiness + enqueue memory instructions into the LSQ**. You do not need to get lost in LFST and RegCacheTagTable details yet; establish the overall picture first.

:::

***

## 4.2 ROB allocation — giving each instruction an "employee badge"

### 4.2.1 Why is a ROB needed?

An out-of-order processor may execute instructions out of order, but it **must commit them in program order**. The ROB (Reorder Buffer) is the core data structure that guarantees this property. Think of it as the factory's **badge-management system**: every worker (instruction) receives a badge number in arrival order. No matter how quickly it finishes in its workshop, it must leave the factory (commit) in badge-number order.

### 4.2.2 The ROB allocation interface in the source

In `Dispatch.scala`, ROB allocation is performed through the `enqRob` port:

```scala
// From Dispatch.scala L109
val enqRob = Flipped(new RobEnqIO)
```

When dispatch receives renamed micro-operations, it requests an enqueue from the ROB through `enqRob.req`. Each request carries the complete instruction information:

```scala
// From Dispatch.scala L167-L200 (fields arranged from the debug output)
// enqRob.req(i).bits contains:
// - instr, pc        : instruction encoding and PC
// - commitType       : commit type
// - fuType, fuOpType : functional unit and operation code
// - psrc(0), psrc(1) : source physical registers
// - pdest            : destination physical register
// - robIdx           : ROB index (returned after ROB allocation)
// - numUops, numWB   : number of micro-operations and writebacks
```

### 4.2.3 Key constraints on ROB allocation

**Dispatch must stall when the ROB is full.** In the source, the ROB ready signal and the ready signals of all Issue Queues jointly determine whether dispatch can continue:

```scala
// From Dispatch.scala L324
io.toRenameAllFire := io.fromRename.map(x => !x.valid || x.fire).reduce(_ && _)
```

`toRenameAllFire` means that every Rename→Dispatch handshake succeeded. It is true only when the ROB has space and every target IQ can accept its instruction; only then will rename continue without stalling.

> The analogy is an entrance gate at a factory: when the badges are exhausted (ROB full) or a workshop is at capacity (IQ full), the gate closes and no new worker can enter.

***

## 4.3 Issue Queue routing — which workshop should receive the instruction?

### 4.3.1 Why is routing needed?

Renamed micro-operations carry a `fuType` (functional-unit type), but different functional units are connected to different Issue Queues. One of dispatch's main responsibilities is to **route an instruction to the correct Issue Queue according to `fuType`**.

This is like a parcel sorting center: a parcel labeled "electronics" uses channel A and one labeled "food" uses channel B. A sorting mistake means the parcel never reaches its destination.

### 4.3.2 `fuMapIQIdx` — mapping functional units to Issue Queues

In `Dispatch.scala`, XiangShan builds a key mapping named `fuMapIQIdx`, recording which Issue Queues should receive each `FuConfig`:

```scala
// From Dispatch.scala L64-L68
val fuMapIQIdx = sortedFuConfigs.map( fu => {
  val fuInIQIdx = fuConfigsInIssueParams.zipWithIndex.filter { case (f, i) => f.contains(fu) }.map(_._2)
  (fu -> fuInIQIdx)
})
```

**What this code does:**

1. Iterate over all sorted `FuConfig` values.
2. For each `FuConfig`, check which Issue Queues contain that functional unit.
3. Record the mapping: `FuConfig → [IQ indices]`.

### 4.3.3 `needMultiIQ` vs. `needSingleIQ` — single-queue and multi-queue routing

Some functional units exist in only one Issue Queue (single-queue routing); others exist in several queues (multi-queue routing, which needs load balancing):

```scala
// From Dispatch.scala L75-L76
val needMultiIQ = sameIQIdxFus.sortBy(_._1.head.fuType.id).filter(_._2.size > 1)
val needSingleIQ = sameIQIdxFus.sortBy(_._1.head.fuType.id).filter(_._2.size == 1)
```

| **Category** | **Meaning** | **Routing policy** | **Typical example** |
| --- | --- | --- | --- |
| **needSingleIQ** | The functional unit exists in only one IQ | Route directly; no choice is needed | Some dedicated division/encryption units |
| **needMultiIQ** | The functional unit exists in multiple IQs | Select using load balancing | ALU (present in six IQs), multiplier |

> A single-queue route is like a bank with one window: join the only line. A multi-queue route is like a bank with six windows: choose the shortest line.

### 4.3.4 Multi-queue load balancing — choose the least occupied queue

For a functional unit in `needMultiIQ`, dispatch chooses the least occupied queue by **comparing the number of occupied entries in each IQ**:

```scala
// From Dispatch.scala L590-L596
val compareMatrix = Wire(Vec(iqNum, Vec(iqNum, Bool())))
for (i <- 0 until iqNum) {
  for (j <- 0 until iqNum) {
    if (i == j) compareMatrix(i)(j) := false.B
    else if (i < j) compareMatrix(i)(j) := issueQueueCountAddEnq(exuidx(i)) < issueQueueCountAddEnq(exuidx(j))
    else compareMatrix(i)(j) := !compareMatrix(j)(i)
  }
}
```

**Load-balancing algorithm:**

1. Build an `iqNum × iqNum` comparison matrix. `compareMatrix(i)(j)=true` means IQ *i* has more free capacity than IQ *j*.
2. Compare `issueQueueCountAddEnq`, the current occupancy plus the entries that will be enqueued in the current cycle.
3. Choose the least occupied IQ as the routing destination.

The calculation of `issueQueueCountAddEnq` accounts for both current occupancy and instructions that will enter during this cycle:

```scala
// From Dispatch.scala L582-L583
val issueQueueCount = VecInit(io.IQValidNumVec.zip(needAppendIQValidNumVec).map(x => RegNext(x._1 + x._2)))
val issueQueueCountAddEnq = VecInit(issueQueueCount.zip(needAppendIQValidNumVec).map(x => x._1 + x._2))
```

### 4.3.5 The Nanhu Issue Queue configuration

The debug output in the source shows the complete Issue Queue configuration for the XiangShan Nanhu architecture:

```scala
// From Dispatch.scala L293-L311 (organized from the debug output)
Queue 0  : issueQueueALU0  — Alu, Csr, Fence, Brh, Jmp
Queue 1  : issueQueueALU1  — Alu, Div, Brh, Jmp
Queue 2  : issueQueueALU2  — Alu, I2F, Vsetriwi, Vsetriwv, fl2v, Brh, Jmp
Queue 3  : issueQueueALU3  — Alu, Bku
Queue 4  : issueQueueALU4  — Alu, Mul
Queue 5  : issueQueueALU5  — Alu, Mul
Queue 6  : issueQueueLDU0  — Ldu
Queue 7  : issueQueueLDU1  — Ldu
Queue 8  : issueQueueLDU2  — Ldu
Queue 9  : issueQueueSTA0  — Sta, Mou  +  issueQueueSTD0 — Std, Moud
Queue 10 : issueQueueSTA1  — Sta, Mou  +  issueQueueSTD1 — Std, Moud
Queue 11 : issueQueueFEX0  — Falu, Fmac, Fcvt, Fcmp, F2v
Queue 12 : issueQueueFEX1  — Falu, Fmac, Fdiv
Queue 13 : issueQueueFEX2  — Falu, Fmac, Fdiv
Queue 14 : issueQueueFEX3  — Falu, Fmac
Queue 15 : issueQueueVFEX0 — VialuFix, Falu, Fvma, Vimac, Vppu, Vipu, VFcvt, Vsetrvf, FvMove
Queue 16 : issueQueueVFEX1 — VialuFix, Falu, Fvma, Vfdiv, Vvid
Queue 17 : issueQueueVLSU0 — Vldu, Vstu, Vsegldu, Vsegstu
Queue 18 : issueQueueVLSU1 — Vldu, Vstu
```

> **Reading the diagram**: ALU operations appear in six IQs, Queue 0 through Queue 5 (a typical `needMultiIQ` case), whereas LDU appears only in Queue 6 through Queue 8. This multi-copy plus load-balancing design prevents the most frequent integer operations from stalling just because one IQ is full.

***

## 4.4 BusyTable — have the source operands arrived?

### 4.4.1 Why is a BusyTable needed?

During rename, we know which physical register corresponds to each source operand. But **does that physical register already contain data?** If an earlier instruction has not written its result back, the source operand has not arrived. The Issue Queue needs readiness for each source operand to decide when it can issue the instruction.

A BusyTable records **whether each physical register contains valid data**. Think of it as a parcel-tracking system that labels each package "delivered" or "in transit."

### 4.4.2 Instantiating the five BusyTables

XiangShan instantiates an independent BusyTable for each register class:

```scala
// From Dispatch.scala L402-L407
val intBusyTable  = Module(new BusyTable(numRegSrcInt * renameWidth, backendParams.numPregWb(IntData()),  IntPhyRegs,  IntWB()))
val fpBusyTable   = Module(new BusyTable(numRegSrcFp  * renameWidth, backendParams.numPregWb(FpData()),   FpPhyRegs,   FpWB()))
val vecBusyTable  = Module(new BusyTable(numRegSrcVf  * renameWidth, backendParams.numPregWb(VecData()),  VfPhyRegs,   VfWB()))
val v0BusyTable   = Module(new BusyTable(numRegSrcV0  * renameWidth, backendParams.numPregWb(V0Data()),   V0PhyRegs,   V0WB()))
val vlBusyTable   = Module(new VlBusyTable(numRegSrcVl * renameWidth, backendParams.numPregWb(VlData()), VlPhyRegs,   VlWB()))
```

| **BusyTable** | **Managed object** | **Number of read ports** | **Writeback source** |
| --- | --- | --- | --- |
| intBusyTable | Integer physical registers | numRegSrcInt × RenameWidth | IntWB (integer writeback) |
| fpBusyTable | Floating-point physical registers | numRegSrcFp × RenameWidth | FpWB (floating-point writeback) |
| vecBusyTable | Vector physical registers | numRegSrcVf × RenameWidth | VfWB (vector writeback) |
| v0BusyTable | v0 mask physical registers | numRegSrcV0 × RenameWidth | V0WB |
| vlBusyTable | vl-length physical registers | numRegSrcVl × RenameWidth | VlWB (special implementation) |

> The analogy is five independent parcel-tracking systems: domestic parcels, international parcels, cold-chain parcels, fragile parcels, and special parcels, each with its own tracking rules.

### 4.4.3 The two core BusyTable operations: allocation and wake-up

**1. Allocation — mark a newly written destination register as busy**

When an instruction receives a new destination physical register during rename, the BusyTable marks that PR as **busy (`busy=true`)**, because the instruction has not completed and its data has not been written back.

```scala
// From Dispatch.scala L412-L423
val allocPregsValid = Wire(Vec(busyTables.size, Vec(RenameWidth, Bool())))
allocPregsValid(0) := VecInit(fromRename.map(x => x.valid && x.bits.rfWen && !x.bits.isMove))
//                                                ^^^^^^^^^^^^^^^^^^^^^^^^^^ Note: move elimination does not mark it busy!
allocPregsValid(1) := VecInit(fromRename.map(x => x.valid && x.bits.fpWen))
allocPregsValid(2) := VecInit(fromRename.map(x => x.valid && x.bits.vecWen))
allocPregsValid(3) := VecInit(fromRename.map(x => x.valid && x.bits.v0Wen))

val allocPregs = Wire(Vec(busyTables.size, Vec(RenameWidth, ValidIO(UInt(PhyRegIdxWidth.W)))))
allocPregs.zip(allocPregsValid).map(x => {
  x._1.zip(x._2).zipWithIndex.map{case ((sink, source), i) => {
    sink.valid := source
    sink.bits := fromRename(i).bits.pdest  // newly allocated destination PR number
  }}
})
```

**Important detail:** integer `allocPregsValid` contains `!x.bits.isMove`. A move-eliminated instruction does not allocate a new PR, so its destination PR does not need to be marked busy; the source PR's data was already ready.

**2. Wake-up — mark a result ready when an instruction writes back**

When an instruction finishes and writes its result, its destination PR is updated to **ready (`busy=false`)** in the BusyTable. Every Issue Queue entry waiting for that PR can receive the wake-up signal.

```scala
// From Dispatch.scala L424-L433
val wakeUp = io.wakeUpAll.wakeUpInt ++ io.wakeUpAll.wakeUpFp ++ io.wakeUpAll.wakeUpVec
busyTables.zip(wbPregs).zip(allocPregs).map{ case ((b, w), a) => {
  b.io.wakeUpInt := io.wakeUpAll.wakeUpInt   // integer writeback wake-up
  b.io.wakeUpFp  := io.wakeUpAll.wakeUpFp    // floating-point writeback wake-up
  b.io.wakeUpVec := io.wakeUpAll.wakeUpVec   // vector writeback wake-up
  b.io.og0Cancel := io.og0Cancel             // cancellation signal
  b.io.ldCancel  := io.ldCancel              // Load cancellation signal
  b.io.wbPregs   := w                         // writeback port numbers
  b.io.allocPregs := a                        // allocation port numbers
}}
```

**Three wake-up sources:** integer, floating-point, and vector writeback. Every BusyTable receives all three, so cross-type dependencies are also woken correctly (for example, an integer instruction depending on a floating-point result).

### 4.4.4 BusyTable reads — querying source-operand readiness

Dispatch queries source readiness through BusyTable read ports and passes the results to the Issue Queue:

```scala
// From Dispatch.scala L461-L496 (simplified)
busyTables.zip(idxRegType).zipWithIndex.map { case ((b, idxseq), i) => {
  // Build read addresses: extract psrc indices belonging to this register class
  val readAddr = VecInit(fromRename.map(x =>
    x.bits.psrc.zipWithIndex.filter(xx => idxseq.contains(xx._2)).map(_._1)
  ).flatten)
  // Build read enables: read only when the source type matches
  val readValid = VecInit(fromRename.map(x =>
    x.bits.psrc.zipWithIndex.filter(xx => idxseq.contains(xx._2))
      .map(y => x.valid && SrcType.isXp(x.bits.srcType(y._2)))
  ).flatten)
  // Connect read ports
  b.io.read.map(_.req).zip(readAddr).map(x => x._1 := x._2)
}}
```

**Key points in the read logic:**

1. Filter the source operand indices in `psrc` by register type (int/fp/vec/v0).
2. Issue a read only when the source type matches (for example, an integer source reads `intBusyTable`).
3. A true response from `busyTables(k).io.read(readidx).resp` means that the source operand is **ready**.

### 4.4.5 `allSrcState` — combining source readiness

Dispatch combines the read results of the five BusyTables into the common `allSrcState` structure passed to the Issue Queue:

```scala
// From Dispatch.scala L497-L519
val allSrcState = Wire(Vec(renameWidth, Vec(numRegSrc, Vec(numRegType, Bool()))))
for (i <- 0 until renameWidth){
  for (j <- 0 until numRegSrc){
    for (k <- 0 until numRegType){
      if (!idxRegType(k).contains(j)) {
        allSrcState(i)(j)(k) := false.B
      } else {
        val readEn = k match {
          case 0 => SrcType.isXp(fromRename(i).bits.srcType(j))  // integer source
          case 1 => SrcType.isFp(fromRename(i).bits.srcType(j))  // floating-point source
          case 2 => SrcType.isVp(fromRename(i).bits.srcType(j))  // vector source
          case 3 => SrcType.isV0(fromRename(i).bits.srcType(j))  // v0 source
        }
        // source ready = (matching type and BusyTable ready) or immediate
        allSrcState(i)(j)(k) := readEn && busyTables(k).io.read(readidx).resp
                              || SrcType.isImm(fromRename(i).bits.srcType(j))
      }
    }
  }
  // Handle the vl source separately
  allSrcStateVl(i) := vlBusyTable.io.read(i).resp || !fromRename(i).bits.vlRen
}
```

**Core formula:** `source ready = (type matches && BusyTable returns true) || is an immediate`

An immediate is inherently ready because it does not depend on a register, so `SrcType.isImm` directly contributes true.

:::color4
**❤** **Beginner tip:**

BusyTable logic can be summarized in one sentence: **mark a newly allocated destination PR busy, mark it ready after writeback, and let reads tell the IQ whether a source operand has arrived**. The multi-type, multi-port details are engineering implementations of this rule.

:::

***

## 4.5 LSQ enqueue — the dedicated path for memory instructions

### 4.5.1 Why do memory instructions need special handling?

In addition to entering a normal Issue Queue, a Load/Store instruction must enter the **LSQ (Load-Store Queue)**. Memory operations involve address dependencies, memory consistency, and Store-to-Load forwarding, so a dedicated queue is needed to manage them.

### 4.5.2 The LSQ enqueue interface in the source

```scala
// From Dispatch.scala L146-L147
val toMem = new Bundle {
  val lsqEnqIO = Flipped(new LsqEnqIO)
}
```

Dispatch sends enqueue requests to the LSQ through `lsqEnqIO`. Compared with a normal IQ, LSQ enqueue carries additional information:

```scala
// From Dispatch.scala L204-L241 (LSQ fields from the debug output)
// lsqEnqIO.req(i).bits contains:
// - instr, pc       : instruction encoding and PC
// - fuType, fuOpType: functional unit and operation code
// - psrc, pdest     : physical register numbers
// - robIdx          : ROB index
// - sqIdx           : Store Queue index (returned after LSQ allocation)
// - needAlloc       : whether an LSQ entry is needed (0=no, 1=Load, 2=Store)
// - canAccept       : whether the LSQ has free space
```

### 4.5.3 Dual enqueue into the LSQ and IQ

A memory instruction enters the IQ and LSQ **at the same time** during dispatch. The IQ schedules issue; the LSQ manages memory dependencies and consistency. The instruction can enqueue only when both structures have space.

> The analogy is applying for a bank loan: registration must succeed at both the "service window" (IQ) and the "risk-control system" (LSQ) before processing can begin.

***

## 4.6 LFST — the load-forwarding store table

### 4.6.1 Why is LFST needed?

In an out-of-order processor, a Load may depend on the address-computation result of an earlier Store. If dispatch can learn about this dependency early, it can mark the Load's source dependency in the Issue Queue and avoid issuing the Load blindly and cancelling it later.

LFST (Load-Forward-Store-Table) is XiangShan's dedicated module for **forwarding Store→Load dependency information early during dispatch**.

```scala
// From Dispatch.scala L153
val lfst = new DispatchLFSTIO
```

### 4.6.2 Passing LFST dependency information

In the source, LFST dependency information reaches the Issue Queue through the BusyTable `loadDependency` read port:

```scala
// From Dispatch.scala L466-L486
val srcLoadDependencyUpdate = fromRenameUpdate.map(x => x.bits.srcLoadDependency)
val srcType = fromRenameUpdate.map(x => x.bits.srcType)
srcLoadDependencyUpdate.zip(srcType).zipWithIndex.foreach {
  case ((sinks, srcTypes), uopIdx) =>
    for (srcidx <- 0 until 3) {
      val sink = sinks(srcidx)
      val srcType = srcTypes(srcidx)
      val fpRead = busyTables(1).io.read(uopIdx * 3 + srcidx).loadDependency
      if (srcidx < 2) {
        val intRead = busyTables(0).io.read(uopIdx * 2 + srcidx).loadDependency
        sink := Mux1H(Seq(
          SrcType.isFp(srcType) -> fpRead,
          SrcType.isXp(srcType) -> intRead,
        ))
      } else {
        sink := Mux(SrcType.isFp(srcType), fpRead, 0.U.asTypeOf(sink))
      }
    }
}
```

**Dependency-selection logic:** according to the source operand type (integer or floating point), read `loadDependency` from the corresponding BusyTable port and pass it downstream to the Issue Queue. This tells the IQ: "the source is ready, but it may depend on an unfinished Store, so issue it with special care."

***

## 4.7 RegCacheTagTable — managing register-cache tags

### 4.7.1 What is RegCache?

To reduce integer-register bypass latency, XiangShan introduces **RegCache**, a small register-cache mechanism. `RegCacheTagTable` manages RegCache tags and reads the tags for integer source operands during dispatch.

```scala
// From Dispatch.scala L400-L401
val rcTagTable = Module(new RegCacheTagTable(numRegSrcInt * renameWidth))
```

### 4.7.2 Connecting RegCacheTagTable reads

```scala
// From Dispatch.scala L453-L459
rcTagTable.io.allocPregs.zip(allocPregs(0)).map(x => x._1 := x._2)   // update tags on allocation
rcTagTable.io.wakeupFromIQ := io.wakeUpAll.wakeUpInt                    // update tags on IQ wake-up
rcTagTable.io.og0Cancel := io.og0Cancel                                 // cancellation signal
rcTagTable.io.ldCancel := io.ldCancel                                   // Load cancellation signal
```

```scala
// From Dispatch.scala L489-L494
// Read RegCache tags only for integer source operands
val rcTagUpdate = fromRenameUpdate.map(x =>
  x.bits.regCacheIdx.zipWithIndex.filter(x => idxseq.contains(x._2)).map(_._1)
).flatten
rcTagUpdate.zip(rcTagTable.io.readPorts.map(_.addr)).map(x => x._1 := x._2)
```

> **RegCache is an advanced optimization.** Beginners only need to know that it exists. The key idea is that frequently used integer-register values can be cached closer to the execution units, reducing register-file read latency.

***

## 4.8 Special handling of vector source operands — ignoring an old `vd`

### 4.8.1 Why do vector instructions need special handling?

Vector instructions have a special source, the **old `vd`** (old destination vector register). Some vector instructions read the old destination for a merge operation. In many cases, such as an invisible tail or an all-one mask, the old value is not actually used. It can then be marked ready to avoid an unnecessary wait.

```scala
// From Dispatch.scala L521-L542
val ignoreOldVdVec = Wire(Vec(renameWidth, Bool()))
for (i <- 0 until renameWidth){
  val isDependOldVd = fromRename(i).bits.vpu.isDependOldVd
  val isWritePartVd = fromRename(i).bits.vpu.isWritePartVd
  val vta = fromRename(i).bits.vpu.vta
  val vma = fromRename(i).bits.vpu.vma
  val vm = fromRename(i).bits.vpu.vm
  val vlIsVlmax = vlBusyTable.io_vl_read.vlReadInfo(i).is_vlmax
  val vlIsNonZero = vlBusyTable.io_vl_read.vlReadInfo(i).is_nonzero
  val ignoreTail = vlIsVlmax && (vm =/= 0.U || vma) && !isWritePartVd
  val ignoreWhole = (vm =/= 0.U || vma) && vta
  val ignoreOldVd = vlBusyTable.io.read(i).resp && vlIsNonZero && !isDependOldVd && (ignoreTail || ignoreWhole)
  ignoreOldVdVec(i) := readEn && ignoreOldVd
  // If old vd can be ignored, mark it ready
  allSrcState(i)(j)(k) := readEn && (busyTables(k).io.read(readidx).resp || ignoreOldVd)
                        || SrcType.isImm(fromRename(i).bits.srcType(j))
}
```

**Conditions for ignoring old `vd`:**

1. `vl` is ready and non-zero.
2. The instruction does not depend on the old `vd` value (`!isDependOldVd`).
3. The conditions for ignoring the tail or the whole destination are met (related to `vta`, `vma`, and `vm`).

> It is like cooking a dish that normally needs leftovers from the previous meal. If the recipe confirms that leftovers are not used, there is no need to wait for them to arrive.

***

## 4.9 When does dispatch stall?

### 4.9.1 Three major sources of stalls

Dispatch is **one of the stages most likely to become a bottleneck**, because it must satisfy the acceptance conditions of several downstream modules at once:

| **Stall source** | **Trigger** | **Effect** |
| --- | --- | --- |
| **ROB full** | No free ROB entries | No instruction can enqueue; the whole pipeline stalls |
| **Target IQ full** | The instruction's Issue Queue has no free entry | That instruction class stalls; other classes may continue |
| **LSQ full** | The Load/Store Queue has no free entry | Memory instructions stall; non-memory instructions may continue |

### 4.9.2 Stall propagation in the source

```scala
// From Dispatch.scala L324
io.toRenameAllFire := io.fromRename.map(x => !x.valid || x.fire).reduce(_ && _)
```

`toRenameAllFire` is the signal sent from dispatch back to rename. It is true only when every valid instruction completes its handshake (`fire`). If any instruction cannot be dispatched because a downstream structure is full, the signal is false. Rename stalls, and front-end decode stalls in turn: **back-pressure propagates from back to front**.

> This is like congestion on a highway: when the toll station at the front (IQ/ROB) is full, the cars behind it (instructions) stop one after another until the whole highway (pipeline) is blocked.

***

## 4.10 Data updates between dispatch and rename

### 4.10.1 `fromRenameUpdate` — correcting rename output

Dispatch does not merely accept rename output; it also **corrects selected fields** before passing it downstream:

```scala
// From Dispatch.scala L325-L342
val fromRenameUpdate = Wire(Vec(RenameWidth, Flipped(ValidIO(new DispatchUpdateUop))))
for (i <- 0 until RenameWidth) {
  fromRenameUpdate(i).valid := fromRename(i).valid
  // v0 does not need srcLoadDependency; srcState is updated through allSrcState
  fromRenameUpdate(i).bits.srcLoadDependency(3) := 0.U.asTypeOf(...)
  fromRenameUpdate(i).bits.srcState := 0.U.asTypeOf(...)
  fromRenameUpdate(i).bits.srcStateVl := 0.U
  connectSamePort(fromRenameUpdate(i).bits, fromRename(i).bits)
  // Correct ftqOffset: for branch/Store compression, use the last instruction's offset
  fromRenameUpdate(i).bits.ftqOffset := fromRename(i).bits.ftqLastOffset
  // Correct ftqPtr: add one when crossing an FTQ line
  fromRenameUpdate(i).bits.ftqPtr := fromRename(i).bits.ftqPtr + fromRename(i).bits.crossFtq
  // Correct isRVC: use the last instruction's compressed flag
  fromRenameUpdate(i).bits.isRVC := fromRename(i).bits.lastIsRVC
}
```

**Important corrected fields:**

* `ftqOffset`: ROB compression can put multiple instructions into one ROB entry, so the FTQ offset of the last instruction is required
* `ftqPtr`: adjust the pointer when crossing an FTQ line
* `srcState`: overwritten by BusyTable read results (rename cannot know readiness)
* `srcLoadDependency`: filled by LFST logic

***

## 4.11 Special dispatch logic in SingleStep debug mode

### 4.11.1 Why is SingleStep special handling needed?

In debug mode, the processor must pause after every machine instruction commits. Because of ROB compression, one machine instruction can correspond to multiple micro-operations. Dispatch must track exactly which micro-operation belongs to the machine instruction currently being stepped.

```scala
// From Dispatch.scala L545-L568
val s_holdRobidx :: s_updateRobidx :: Nil = Enum(2)
val singleStepState = RegInit(s_updateRobidx)

when(!io.singleStep) {
  singleStepState := s_updateRobidx
}.elsewhen(io.singleStep && fromRename(0).fire && io.enqRob.req(0).valid) {
  singleStepState := s_holdRobidx
  robidxStepHold := fromRename(0).bits.robIdx
}
```

**State machine:**

* `s_updateRobidx`: normal state; update the step's ROB index
* `s_holdRobidx`: hold the current step's ROB index and wait for commit before pausing

> This is advanced debug-related material; beginners only need to know that it exists.

***

## 4.12 Hands-on exercise: follow an ADD instruction through dispatch

### 4.12.1 Exercise: follow `add x10, x1, x2`

**Step 1: receive rename output**

```scala
// Dispatch.scala L106
val fromRename = Vec(RenameWidth, Flipped(DecoupledIO(new RenameOutUop)))
```

Assume that after rename, `add x10, x1, x2` has `psrc(0)=PR5`, `psrc(1)=PR8`, `pdest=PR50`, and `fuType=ALU`.

**Step 2: allocate a ROB entry**

```scala
// Dispatch.scala L109
val enqRob = Flipped(new RobEnqIO)
```

Request an entry from the ROB and obtain `robIdx`. The ROB records this instruction's commit-tracking information.

**Step 3: query source readiness in BusyTable**

* Query `intBusyTable`: are PR5 and PR8 ready?
* Assume PR5 has written back (ready) while PR8 has not (busy).
* `allSrcState = [true, false]` (source 1 ready, source 2 not ready).

**Step 4: route to an Issue Queue**

* `fuType=ALU` → look up `fuMapIQIdx` → ALU is present in Queue 0 through Queue 5.
* `needMultiIQ` → load balancing chooses a queue → assume Queue 2 is the least occupied.

**Step 5: write into the Issue Queue**

```scala
// Dispatch.scala L112
val toIssueQueues = Vec(IQEnqSum, DecoupledIO(new DispatchOutUop))
```

The instruction is written to Queue 2 with `psrc=[PR5, PR8]`, `pdest=PR50`, `robIdx`, and `srcState=[ready, busy]`.

**Step 6: wait for wake-up**

When a later instruction writes back PR8, `intBusyTable` wakes the entry. Source 2 becomes ready in the Issue Queue, and the instruction can issue.

### 4.12.2 Comparison: dispatching a Load

For `lw x10, 0(x5)`:

1. After rename: `psrc(0)=PR5` (base register), `pdest=PR50`, `fuType=LDU`.
2. Allocate a ROB entry.
3. **LSQ enqueue**: the Load must allocate an LQ entry in the LSQ.
4. Query BusyTable: is PR5 ready?
5. IQ routing: `LDU` → Queue 6 through Queue 8 (Load-specific IQs).
6. **Dual dependency**: both the IQ and LSQ must have space before the instruction can enqueue.

***

## 4.13 Frequently asked questions and troubleshooting

### Q1: When dispatch stalls, how can I identify the responsible module?

XiangShan's stall-reason tracking system (`stallReason`) records the reason for a stall in each cycle:

```scala
// From Dispatch.scala L157
val stallReason = Flipped(new StallReasonIO(RenameWidth))
```

Analyze the `stallReason` output to determine precisely whether the ROB, an IQ, or the LSQ caused the stall.

### Q2: Why is a move-eliminated instruction not marked busy in BusyTable?

```scala
// From Dispatch.scala L413
allocPregsValid(0) := VecInit(fromRename.map(x => x.valid && x.bits.rfWen && !x.bits.isMove))
```

For a move-eliminated instruction, `pdest` equals the source PR, whose data is already ready. Marking it busy would make the downstream IQ incorrectly believe that the PR contains no valid data, and dependent instructions would never issue.

### Q3: Can load balancing across multiple IQs add latency?

Yes. The comparison matrix (`compareMatrix`) has O(iqNum²) combinational logic depth and may become a timing bottleneck in a high-frequency design. XiangShan mitigates this pressure by registering the `IQSort` path.

***

## 4.14 Tiered learning path

🟢 **Must know first:** the three responsibilities of dispatch (ROB allocation, IQ routing, and BusyTable initialization), stall conditions, and back-pressure propagation

🔵 **Understand next:** `fuMapIQIdx` routing, the `needMultiIQ` load-balancing algorithm, BusyTable allocation/wake-up/read source implementation, and dual LSQ enqueue

🟣 **Deep dive:** LFST dependency forwarding, RegCacheTagTable tag management, ignoring an old vector `vd`, SingleStep debug dispatch, and timing optimization for multi-IQ load balancing

***

## 4.15 Summary

✅ **Key points:**

* Dispatch is the **bridge between rename and issue**. Its three core jobs are **ROB allocation, IQ routing, and source-readiness initialization**.
* **ROB allocation** preserves in-order commit after out-of-order execution; a full ROB stalls the pipeline.
* **IQ routing** uses `fuMapIQIdx` to send each instruction to the correct Issue Queue; multi-queue routing uses **load balancing** to select the least occupied IQ.
* **BusyTable** records physical-register busy/ready state: a newly allocated PR is busy and is woken ready on writeback. Each of the five register classes has an independent BusyTable.
* A **move-eliminated instruction** is **not marked busy** in BusyTable because its `pdest` shares the source PR's data.
* **LSQ enqueue** is the dedicated memory-instruction path and requires both the IQ and LSQ to be ready.
* **LFST** forwards Store→Load dependencies early so that Loads are not issued blindly.
* Dispatch stalls when the **ROB, target IQ, or LSQ is full**, and back-pressure propagates toward the front end.

🎉 **Congratulations on completing the dispatch stage!** You now understand the full path from an instruction receiving a physical-register identity to entering a workshop queue. Dispatch may look like a simple sorting center, but its routing, state initialization, load balancing, and stall control are all significant engineering challenges in a high-performance processor. Next, explore how instructions are selected and issued from the Issue Queue.

***

Next, continue with:

* [XiangShan out-of-order pipeline design](16-xiangshan-ooo-pipeline-design)
* [Dynamic instruction execution analysis](17-instruction-dynamic-execution-analysis)
* [Bug classification and analysis](21-bug-classification-and-analysis)


> Updated: 2026-06-02 10:56:42
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/rx0ps1qslgayqqlb>
