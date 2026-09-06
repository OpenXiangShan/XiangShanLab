# 11. Physical Register Cache

上一章我们聊到了物理寄存器堆——那个所有人都要去存取货物的"总仓库"。但总仓库的窗口有限，排队太久了怎么办？香山的答案是：在总仓库旁边开一家**便利店**——RegCache。

🏪读完本章，你将能够：

* ✅ 理解 RegCache 的设计动机与核心价值
* ✅ 掌握 RegCache 的读写机制与 Tag 匹配流程
* ✅ 认识 RegCache 的替换策略与 Age Timer
* ✅ 理解 IntRegCache 与 MemRegCache 的 Bank Set 划分

***

## 11.1 整体定位：为什么需要 RegCache？

在 Post-Issue Read 策略下，OG1 阶段需要读物理寄存器堆（RF）获取操作数。但 RF 的读端口非常昂贵——端口越多，面积和延迟越大。而实际情况是：

刚写回的数据最有可能被立即消费，而这些数据其实不需要再从 RF 读——它们就在写回通路上。但写回通路的数据稍纵即逝，下一拍就没了。如果后续指令没能赶上当拍的前递，就只好老老实实去 RF 排队读取。

RegCache 的作用就是抓住这些稍纵即逝的数据——在写回 RF 的同时，把数据也存一份到 RegCache 中。后续指令如果能从 RegCache 命中，就不用竞争 RF 读端口。

| **特性** | **RF（总仓库）** | **RegCache（便利店）** |
| --- | --- | --- |
| 容量 | 全部物理寄存器（~200项） | 最近写回的子集 |
| 读端口 | 多但竞争激烈 | 少且快速 |
| 命中率 | 100%（总能读到） | 依赖时间局部性 |
| 延迟 | 1 拍（地址先打一拍 RegNext） | 组合逻辑直读 + 1 拍地址寄存 |
| 用途 | 主存储 | 辅助加速，减少 RF 读压力 |

***

## 11.2 RegCache 的整体架构

RegCache 由**数据存储**和**Tag 管理**两大部分协作完成，它们是独立的模块：

```plain
┌── RegCache（数据存储）─────────────────────────────────────┐
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐   │
│  │ IntRegCache  │  │ MemRegCache  │  │ AgeTimer ×2    │   │
│  │ (DataModule) │  │ (DataModule) │  │ + AgeDetector  │   │
│  │ 整数写回数据  │  │ 访存写回数据  │  │ 决定替换谁      │   │
│  └──────────────┘  └──────────────┘  └────────────────┘   │
└───────────────────────────────────────────────────────────┘

┌─ RegCacheTagTable（Tag 管理，独立模块）─────────────────────┐
│  ┌──────────────┐  ┌──────────────┐                      	│
│  │ IntRCTagTable│  │ MemRCTagTable│                       │
│  │ tag→index映射│  │ tag→index映射 │                      	│
│  └──────────────┘  └──────────────┘                       │
│  + 处理 wakeup 写入、cancel（alloc/replace/ldCancel）     	│
└───────────────────────────────────────────────────────────┘

```

源码结构：

```scala
// RegCache.scala — RegCache 顶层（仅含数据存储，不含 TagTable）
class RegCache()(implicit p: Parameters, params: BackendParams) extends XSModule {
  val io = IO(new RegCacheIO())

  // 数据存储：IntRegCache 和 MemRegCache
  val IntRegCache = Module(new RegCacheDataModule("IntRegCache", IntRegCacheSize, ...))
  val MemRegCache = Module(new RegCacheDataModule("MemRegCache", MemRegCacheSize, ...))

  // 替换策略：AgeTimer + AgeDetector
  val IntRegCacheAgeTimer = Module(new RegCacheAgeTimer(IntRegCacheSize, ...))
  val MemRegCacheAgeTimer = Module(new RegCacheAgeTimer(MemRegCacheSize, ...))
  val IntRegCacheRepRCIdx = RegCacheAgeDetector(IntRegCacheSize, IntRegCacheWriteSize, ...)
  val MemRegCacheRepRCIdx = RegCacheAgeDetector(MemRegCacheSize, MemRegCacheWriteSize, ...)
}

// RegCacheTagTable.scala — Tag 表（独立模块）
class RegCacheTagTable(numReadPorts: Int)(implicit p: Parameters) extends XSModule {
  val IntRCTagTable = Module(new RegCacheTagModule("IntRCTagTable", IntRegCacheSize, ...))
  val MemRCTagTable = Module(new RegCacheTagModule("MemRCTagTable", MemRegCacheSize, ...))
}
```

***

## 11.3 RegCache 的读取流程

当 RegCache 的读取由 Wakeup Queue 驱动——当一条指令被唤醒时，Wakeup Queue 已经知道其操作数来自 RegCache 的哪个位置（rcIdx），后续直接用 rcIdx 读取数据。

### 第一步：Tag 查询（Wakeup 阶段，OG0 之前）

RegCacheTagTable 接收物理寄存器编号（pdest），在两个 Tag 子表中**同时**查找：

```scala
// RegCacheTagTable.scala — 读：同时查两个 Tag 表
io.readPorts.lazyZip(IntRCTagTable.io.readPorts.lazyZip(MemRCTagTable.io.readPorts))
  .foreach { case (r_in, (r_int, r_mem)) =>
    r_int.ren := r_in.ren      // 同时查 IntRCTagTable
    r_mem.ren := r_in.ren      // 同时查 MemRCTagTable
    r_int.tag := r_in.tag
    r_mem.tag := r_in.tag
 
    // 如果该 preg 刚被新分配（alloc），则 Tag 无效
    val matchAlloc = io.allocPregs.map(x => x.valid && r_in.tag === x.bits).reduce(_ || _)
    r_in.valid := (r_int.valid || r_mem.valid) && !matchAlloc
 
    // 命中后拼接完整 RegCacheIdx：MSB 区分 Int/Mem
    r_in.addr := Mux(r_int.valid, Cat("b0".U, r_int.addr), Cat("b1".U, r_mem.addr))
  }
```

* **IntRCTagTable 命中**：addr MSB = 0，指向 IntRegCache
* **MemRCTagTable 命中**：addr MSB = 1，指向 MemRegCache
* **两者都没命中或匹配 alloc**：该操作数退回到 RF 读取

### 第二步：数据读取（OG1 阶段）

根据 RegCacheIdx 从对应的 RegCacheDataModule 读出数据。**RegCacheDataModule 的读取是组合逻辑**——不需要像 RF 那样先打一拍地址：

```scala
// RegCacheDataModule.scala — 组合逻辑直读
for ((r, i) <- io.readPorts.zipWithIndex) {
  r.data := mem(r.addr)        // ← 组合逻辑，当拍出结果！
  when (r.ren) {
    assert(v(r.addr), s"$name readPorts $i read a invalid entry")
  }
}
```

但在 RegCache 顶层，地址会先寄存一拍（与 RF 类似的时序对齐）：

```scala
// RegCache.scala — 顶层读取：地址打一拍，MSB 选 Int/Mem
io.readPorts.lazyZip(IntRegCache.io.readPorts.lazyZip(MemRegCache.io.readPorts))
  .lazyZip(IntRegCacheAgeTimer.io.readPorts.lazyZip(MemRegCacheAgeTimer.io.readPorts))
  .foreach { case (r_in, (r_int, r_mem), (r_int_at, r_mem_at)) =>
    val in_addr = RegEnable(r_in.addr, r_in.ren)          // 地址先寄存
    val int_ren = GatedValidRegNext(r_in.ren & ~r_in.addr(RegCacheIdxWidth - 1))  // MSB=0 → Int
    val mem_ren = GatedValidRegNext(r_in.ren & r_in.addr(RegCacheIdxWidth - 1))   // MSB=1 → Mem
    r_int.ren  := int_ren
    r_mem.ren  := mem_ren
    r_int.addr := in_addr(RegCacheIdxWidth - 2, 0)        // 低半部分为 IntRegCache 地址
    r_mem.addr := in_addr(RegCacheIdxWidth - 2, 0)        // 低半部分为 MemRegCache 地址
    r_in.data  := Mux(in_addr(RegCacheIdxWidth - 1), r_mem.data, r_int.data)  // MSB 选择来源
  }
```

### 第三步：在 BypassNetwork 中选择最终数据源

BypassNetwork 将 RegCache 作为独立的数据来源通道，与其他通道（Forward、Bypass、RF 等）做 one-hot 选择：

```scala
// BypassNetwork.scala — 数据来源选择
val originSrc = Mux1H(Seq(
  readForward  -> ...,           // Forward 通路
  readBypass   -> ...,           // Bypass 通路
  readRegOH    -> rfData,        // RF 读取
  readRegCache -> regCacheData,  // ← RegCache 读取
  readImm      -> ...,           // 立即数
))
```

写入时还需要决定写入哪个 RegCacheIdx——这是由 AgeDetector 决定的替换索引：

```scala
// RegCache.scala — 替换索引：IntRegCache 用 MSB=0，MemRegCache 用 MSB=1
io.toWakeupQueueRCIdx.zipWithIndex.foreach { case (rcIdx, i) =>
  if (i < IntRegCacheWriteSize) {
    rcIdx := Cat("b0".U, IntRegCacheRepRCIdx(i))     // IntRegCache 替换位置
  } else {
    rcIdx := Cat("b1".U, MemRegCacheRepRCIdx(i - IntRegCacheWriteSize))  // MemRegCache 替换位置
  }
}
// 替换索引延迟 3 拍后用于实际写入
val delayToWakeupQueueRCIdx = RegNextN(io.toWakeupQueueRCIdx, 3)
writePorts.zip(delayToWakeupQueueRCIdx).foreach { case (w, rcIdx) =>
  w.addr := rcIdx
}
```

| **取消类型** | **触发条件** | **含义** |
| --- | --- | --- |
| allocCancel | 新 Rename 分配的 preg 与缓存 Tag 相同 | 旧值被覆盖，缓存失效 |
| replaceCancel | 同一 Tag 被新的写回更新 | 旧条目被替换 |
| ldCancel | Load 指令被取消 | 缓存的 Load 数据无效 |

***

## 11.4 RegCache 的写入流程

### 11.4.1 写入时机

RegCache 的写入与**IQ 唤醒**绑定——当一条指令从 Issue Queue 发射后，其写回信息通过 Wakeup Queue 传播。在写回生效时，数据同时写入 RF 和 RegCache。

### 11.4.2 写入条件

并非所有写回都需要写入 RegCache。写入需要同时满足：

| **条件** | **说明** |
| --- | --- |
| <code>**wakeup.valid**</code> | 写回信号有效 |
| <code>**rfWen**</code> | 目标是整数寄存器堆（RegCache 只缓存整数数据） |
| <code>**!LoadShouldCancel**</code> | Load 没有被取消 |
| <code>**!(is0Lat && og0Cancel)**</code> | 0 拍延迟指令未被发射当拍取消 |

```scala
// RegCacheTagTable.scala — Tag 表写入条件
(IntRCTagTable.io.writePorts ++ MemRCTagTable.io.writePorts)
  .lazyZip(wakeupFromIQNeedWriteRC).lazyZip(shiftLoadDependency)
  .foreach { case (w, wakeup, ldDp) =>
    w.wen  := wakeup.valid && wakeup.bits.rfWen
      && !LoadShouldCancel(Some(wakeup.bits.loadDependency), io.ldCancel)
      && !(wakeup.bits.is0Lat && io.og0Cancel(wakeup.bits.params.exuIdx))
    w.addr := wakeup.bits.rcDest.get(RegCacheIdxWidth - 2, 0)
    w.tag  := wakeup.bits.pdest
    w.loadDependency := ldDp
  }
```

### 11.4.3 写入内容

写入 RegCache 时需要两部分信息：

* **数据**：执行单元的计算结果（写入 RegCacheDataModule）
* **标签**：物理寄存器编号 <code>**pdest**</code>（写入 RegCacheTagModule，建立 Tag→Index 映射）

DataModule 的写入逻辑：

```scala
// RegCacheDataModule.scala
for (i <- mem.indices) {
  val wenOH = VecInit(io.writePorts.map(w => w.wen && w.addr === i.U))
  val wData = Mux1H(wenOH, io.writePorts.map(_.data))
  when (wenOH.asUInt.orR) {
    v(i)   := true.B     // 标记为有效
    mem(i) := wData      // 写入数据
  }
  // debug 模式下同时写 tag
  if (backendParams.debugEn) {
    val wTag = Mux1H(wenOH, io.writePorts.map(_.tag.get))
    when (wenOH.asUInt.orR) {
      tag.get(i) := wTag
    }
  }
}
```

***

## 11.5 RegCache 的替换策略：Age Timer

RegCache 的容量有限（IntRegCache 和 MemRegCache 各约 8 项），写满后必须替换。替换谁？香山使用 **Age Timer** 策略——替换**最老**的那项。

### 11.5.1 Age Timer 的工作原理

Age Timer 为 RegCache 的**每一项**维护一个 2-bit 计时器，记录该项的"年龄"。**计时器值越大，该项越老，越适合替换。**

但 Age Timer 的更新规则并非简单的"每拍加一"——**读写操作都会影响计时器**：

```scala
// RegCacheAgeTimer.scala
for ((atNext, i) <- ageTimerNext.zipWithIndex) {
  when (hasWriteReq(i)) {
    atNext := 0.U           // ← 写入：重置为 0（最新）
  }.elsewhen (hasReadReq(i)) {
    atNext := ageTimer(i)   // ← 读取：保持不变（被访问了，不老化）
  }.elsewhen (ageTimer(i) === 3.U && io.validInfo(i)) {
    atNext := 3.U           // ← 已达最大值且有效：饱和不变
  }.otherwise {
    atNext := ageTimer(i) + 1.U  // ← 其他：每拍加 1（逐渐变老）
  }
  ageTimer(i) := atNext
}
```

此外，Age Timer 还有一个 **Extra Timer** 机制，为每 4 个条目组提供额外的时间精度：

```scala
// RegCacheAgeTimer.scala
val ageTimerExtra = RegInit(VecInit((0 until 4).map(_.U(2.W))))
ageTimerExtra.foreach(i => i := i + 1.U)  // 每 4 个条目组共享一个 2-bit 额外计时器
```

Age 比较时，将 <code>**ageTimer**</code> 和 <code>**ageTimerExtra**</code> 拼接为 4-bit 值做比较：

```scala
// RegCacheAgeTimer.scala
def age_cmp_func(row: Int, col: Int): Bool = {
  if (row < col) {
    val res = Wire(Bool())
    when (io.validInfo(row) && !io.validInfo(col)) {
      res := false.B         // 有效项比无效项"年轻"（优先保留有效项）
    }.elsewhen (!io.validInfo(row) && io.validInfo(col)) {
      res := true.B          // 无效项比有效项"老"（优先替换无效项）
    }.otherwise {
      res := Cat(ageTimerNext(row), ageTimerExtra(row / (numEntries / 4))) >=
             Cat(ageTimerNext(col), ageTimerExtra(col / (numEntries / 4)))
    }
    res
  }
  else if (row == col) true.B
  else !age_cmp_func(col, row)
}
```

***

### 11.5.2 替换索引的计算

AgeDetector 根据 Age Timer 的 <code>**ageInfo**</code> 矩阵，为每个写端口选出最老的 RegCache 项作为替换目标：

```scala
// AgeDetector.scala
class RegCacheAgeDetector(numEntries: Int, numReplace: Int) extends XSModule {
  val io = IO(new Bundle {
    val ageInfo = Vec(numEntries, Vec(numEntries, Input(Bool())))
    val out     = Vec(numReplace, Output(UInt(log2Up(numEntries).W)))
  })

  // age(i)(j): entry i 比 entry j 更老
  val age = Seq.fill(numEntries)(Seq.fill(numEntries)(RegInit(true.B)))

  // 计算每行的"年龄得分"——比它年轻的项越多，得分越高
  val rowOnesSum = (0 until numEntries).map(i =>
                                            PopCount((0 until numEntries).map(j => get_age(i, j)))
                                           )

  // 得分最高的 = 最老的 = 替换目标
  // 第二老的 = 第二替换目标，依此类推
  io.out.zipWithIndex.foreach { case (out, idx) =>
    out := PriorityMux(
      rowOnesSum.map(_ === (numEntries - idx).U).zip((0 until numEntries).map(_.U))
    )
  }
}
```

在 RegCache 顶层调用：

```scala
// RegCache.scala
val IntRegCacheRepRCIdx = RegCacheAgeDetector(IntRegCacheSize, IntRegCacheWriteSize, IntRegCacheAgeTimer.io.ageInfo)
val MemRegCacheRepRCIdx = RegCacheAgeDetector(MemRegCacheSize, MemRegCacheWriteSize, MemRegCacheAgeTimer.io.ageInfo)
```

### 11.5.3 替换索引的延迟对齐

一个关键细节：替换索引在写回时计算，但数据写入 RegCache 需要经过 3 拍延迟（Wakeup Queue 的流水级）。因此替换索引也需要延迟 3 拍，确保索引和数据同步到达 RegCache 的写端口：

```scala
// RegCache.scala
io.toWakeupQueueRCIdx.zipWithIndex.foreach { case (rcIdx, i) =>
  if (i < IntRegCacheWriteSize) {
    rcIdx := Cat("b0".U, IntRegCacheRepRCIdx(i))     // MSB=0 → IntRegCache
  } else {
    rcIdx := Cat("b1".U, MemRegCacheRepRCIdx(i - IntRegCacheWriteSize))  // MSB=1 → MemRegCache
  }
}
 
// 替换索引延迟 3 拍，与数据对齐
val delayToWakeupQueueRCIdx = RegNextN(io.toWakeupQueueRCIdx, 3)
writePorts.zip(delayToWakeupQueueRCIdx).foreach { case (w, rcIdx) =>
  w.addr := rcIdx  // 延迟后的替换索引用于实际写入地址
}
```

替换索引通过 <code>**toWakeupQueueRCIdx**</code> 传给 Wakeup Queue，在 Wakeup Queue 中延迟 3 拍后回传给 RegCache 的写端口地址。同时还有 debug 断言验证这个对齐：

```scala
// RegCache.scala
if (params.basicDebugEn) {
  io.diffRcIdx.get.zipWithIndex.foreach { case (x, i) =>
    when (x.wen) {
      assert(x.rcIdx === delayToWakeupQueueRCIdx(i),
        "When rfWen is raised, the RcIdx to the wakeupQueue three clock cycles ago must match the RcIdx written back to the RegCache in the current clock cycle.")
    }
  }
}
```

***

## 11.6 RegCache 的取消机制

RegCache 的 Tag 表可能需要**取消**已写入的映射，发生在以下场景：

| **取消原因** | **触发条件** | **效果** |
| --- | --- | --- |
| **新物理寄存器分配** | Rename 阶段分配了新的 pdest，旧映射失效 | Tag 表中对应项标记为无效 |
| **Tag 被覆盖** | 另一个写回写入了同一个物理寄存器编号 | 旧项被替换，Tag 更新 |
| **Load Cancel** | Load 违约定向，数据无效 | 对应 RegCache 项标记为无效 |

源码：

```scala
// RegCacheTagTable.scala
// allocVec: 新分配的 preg 与缓存的 tag 相同 → 旧 tag 失效
val allocVec = (IntRCTagTable.io.tagVec ++ MemRCTagTable.io.tagVec).map { t =>
  io.allocPregs.map(a => a.valid && a.bits === t).asUInt.orR
}
 
// replaceVec: 同一个 tag 被新写入 → 旧条目被替换
val replaceVec = IntRCTagTable.io.tagVec.map { t =>
  IntRCTagTable.io.writePorts.map(w => w.wen && w.tag === t).asUInt.orR
} ++ MemRCTagTable.io.tagVec.map { t =>
  MemRCTagTable.io.writePorts.map(w => w.wen && w.tag === t).asUInt.orR
}
 
// ldCancelVec: Load 被取消 → 缓存数据无效
val ldCancelVec = (IntRCTagTable.io.loadDependencyVec ++ MemRCTagTable.io.loadDependencyVec).map { ldDp =>
  LoadShouldCancel(Some(ldDp), io.ldCancel)
}
 
// 综合取消条件 = (alloc || replace || ldCancel) && 该项有效
val cancelVec = allocVec.lazyZip(replaceVec).lazyZip(ldCancelVec)
  .lazyZip(IntRCTagTable.io.validVec ++ MemRCTagTable.io.validVec)
  .map { case (alloc, rep, ldCancel, v) =>
    (alloc || rep || ldCancel) && v
  }
 
(IntRCTagTable.io.cancelVec ++ MemRCTagTable.io.cancelVec).zip(cancelVec).foreach { case (cancelIn, cancel) =>
  cancelIn := cancel
}
```

:::warning
💡核心思想\
RegCache 的取消机制与 Issue Queue 的 Cancel 机制一脉相承——都是\*\*"乐观写入 + 悲观撤回"**。数据先写进来（乐观），如果后来发现写错了就取消掉（悲观）。这保证了 RegCache 中只保存**确实有效\*\*的数据。

:::

***

## 11.7 Register Cache Bank Set（RegCache 分体）

### 11.7.1 为什么 RegCache 也要分体？

RegCache 虽然容量比 RF 小得多，但它面临的**写端口压力**并不低——每个能产生整数写回的执行单元都需要一个写端口。如果所有写端口都连在一个单体 RegCache 上，面积和延迟仍然不可忽视。

### 11.7.2 IntRegCache 与 MemRegCache

香山将 RegCache 分为**两个独立的 Bank Set**：

| **Bank Set** | **名称** | **服务对象** | **写回来源** |
| --- | --- | --- | --- |
| Bank 0 | IntRegCache | 整数执行单元 | ALU、MUL、BJU 等 |
| Bank 1 | MemRegCache | 访存执行单元 | LDU 等 |

源码实例化：

```scala
// RegCache.scala — 两个 DataModule + 两个 AgeTimer
val IntRegCache = Module(new RegCacheDataModule("IntRegCache", IntRegCacheSize,
  IntRegCacheReadSize, IntRegCacheWriteSize,
  params.intSchdParams.get.rfDataWidth, RegCacheIdxWidth - 1, params.intSchdParams.get.pregIdxWidth))
 
val MemRegCache = Module(new RegCacheDataModule("MemRegCache", MemRegCacheSize,
  MemRegCacheReadSize, MemRegCacheWriteSize,
  params.intSchdParams.get.rfDataWidth, RegCacheIdxWidth - 1, params.intSchdParams.get.pregIdxWidth))
 
val IntRegCacheAgeTimer = Module(new RegCacheAgeTimer(IntRegCacheSize, IntRegCacheReadSize, IntRegCacheWriteSize, RegCacheIdxWidth - 1))
val MemRegCacheAgeTimer = Module(new RegCacheAgeTimer(MemRegCacheSize, MemRegCacheReadSize, MemRegCacheWriteSize, RegCacheIdxWidth - 1))
```

### 11.7.3 Bank 选择机制

两个 Bank Set 的选择通过 RegCache 索引的**最高位**实现：

```scala
// RegCache.scala — 读取时 MSB 选 Bank
io.readPorts.lazyZip(IntRegCache.io.readPorts.lazyZip(MemRegCache.io.readPorts))
  .lazyZip(IntRegCacheAgeTimer.io.readPorts.lazyZip(MemRegCacheAgeTimer.io.readPorts))
  .foreach { case (r_in, (r_int, r_mem), (r_int_at, r_mem_at)) =>
    val in_addr = RegEnable(r_in.addr, r_in.ren)
    val int_ren = GatedValidRegNext(r_in.ren & ~r_in.addr(RegCacheIdxWidth - 1))  // MSB=0 → Int
    val mem_ren = GatedValidRegNext(r_in.ren &  r_in.addr(RegCacheIdxWidth - 1))   // MSB=1 → Mem
    r_int.ren  := int_ren
    r_mem.ren  := mem_ren
    r_int.addr := in_addr(RegCacheIdxWidth - 2, 0)   // 低半部分为 Bank 内地址
    r_mem.addr := in_addr(RegCacheIdxWidth - 2, 0)
    r_in.data  := Mux(in_addr(RegCacheIdxWidth - 1), r_mem.data, r_int.data)  // MSB 选择来源
  }
```

写入时同样用 MSB 区分：

```scala
// RegCache.scala — 写入时按端口序号区分 Bank
IntRegCache.io.writePorts.zip(writePorts.take(IntRegCacheWriteSize)).foreach { case (w_int, w_in) =>
  w_int.addr := w_in.addr(RegCacheIdxWidth - 2, 0)   // Int 写端口
}
MemRegCache.io.writePorts.zip(writePorts.takeRight(MemRegCacheWriteSize)).foreach { case (w_mem, w_in) =>
  w_mem.addr := w_in.addr(RegCacheIdxWidth - 2, 0)   // Mem 写端口
}
```

```plain
RegCache Index: [BankBit | Bank内部索引]
                 ↑
            这一位决定去哪个Bank
         0 → IntRegCache
         1 → MemRegCache
```

### 11.7.4 分体的优势与代价

| **优势** | **代价** |
| --- | --- |
| 每个 Bank 的端口数减半 → 面积减小 | 读端口需要同时连接两个 Bank → Mux 开销 |
| 写端口不冲突——Int 和 Mem 天然分离 | Tag 查询需要查两张表 |
| 替换决策独立——Int 和 Mem 各自维护 Age Timer | 两个 Bank 之间不能共享空间 |

### 11.7.5 Tag 表的 Bank Set

与数据模块对应，Tag 表也分为 IntRCTagTable 和 MemRCTagTable 两部分。查询时**同时查两张表**，合并结果：

```scala
// RegCacheTagTable.scala — 两个 Tag 表同时查询
val IntRCTagTable = Module(new RegCacheTagModule("IntRCTagTable", IntRegCacheSize, ...))
val MemRCTagTable = Module(new RegCacheTagModule("MemRCTagTable", MemRegCacheSize, ...))

io.readPorts.lazyZip(IntRCTagTable.io.readPorts.lazyZip(MemRCTagTable.io.readPorts))
.foreach { case (r_in, (r_int, r_mem)) =>
  r_int.ren := r_in.ren      // 同时查 IntRCTagTable
  r_mem.ren := r_in.ren      // 同时查 MemRCTagTable
  r_int.tag := r_in.tag
  r_mem.tag := r_in.tag

  // 新分配的 preg 使旧 Tag 失效
  val matchAlloc = io.allocPregs.map(x => x.valid && r_in.tag === x.bits).reduce(_ || _)
  r_in.valid := (r_int.valid || r_mem.valid) && !matchAlloc

  // 命中后拼接完整 RegCacheIdx：MSB 区分 Bank
  r_in.addr := Mux(r_int.valid, Cat("b0".U, r_int.addr), Cat("b1".U, r_mem.addr))
}
```

* 如果 IntRCTagTable 命中 → 返回 <code>**0 | IntIndex**</code>
* 如果 MemRCTagTable 命中 → 返回 <code>**1 | MemIndex**</code>
* 如果都没命中或匹配 alloc → RegCache 未命中，退回 RF 读取

:::warning
💡 新手建议\
RegCache 分 Int/Mem 两个 Bank Set 是一个自然的选择——整数执行单元和访存执行单元本就属于不同的调度域，写回时序和端口需求不同，分开管理顺理成章。这就像便利店把生鲜区和日用品区分开——顾客按需选择，管理也更方便。

:::

***

## 11.8 RegCache 与 DataPath 的协作

RegCache 并不是独立工作的，它嵌入在 DataPath 的数据通路中，与 RF 读取和旁路网络紧密协作：

```plain
Issue Queue 发射 uop
       │
       ▼
  DataPath 接收
       │
       ├──→ DataSource = reg      → RFReadArbiter  → RF 读取
       ├──→ DataSource = regcache → RegCacheTagTable → RegCache 读取
       ├──→ DataSource = forward  → BypassNetwork 前递（0拍）
       ├──→ DataSource = bypass   → BypassNetwork 前递（1拍）
       ├──→ DataSource = bypass2  → BypassNetwork 前递（2拍，向量）
       ├──→ DataSource = imm      → 直接提取立即数
       └──→ DataSource = zero     → 直接输出零
```

DataSource 的编码定义：

```scala
// DataSource.scala — 完整的数据来源编码
object DataSource {
  def reg:      UInt = "b1000".U   // 从 RF 读取
  def regcache: UInt = "b0110".U   // 从 RegCache 读取
  def v0:       UInt = "b0101".U   // 从 V0 RF 读取
  def zero:     UInt = "b0000".U   // 零
  def forward:  UInt = "b0001".U   // Forward 前递（0拍）
  def bypass:   UInt = "b0010".U   // Bypass 前递（1拍）
  def bypass2:  UInt = "b0011".U   // Bypass2 前递（2拍）
  def imm:      UInt = "b0100".U   // 立即数
}
```

关键点在于：**每个源操作数只能从一种来源获取数据**。DataSource 的值在 Wakeup Queue 中确定后，BypassNetwork 忠实地执行选择。RegCache 的价值在于——当 DataSource 指示 <code>**regcache**</code> 时，操作数不需要竞争 RF 的读端口，从而释放了 RF 的带宽给其他指令。

在 BypassNetwork 中，RegCache 数据作为独立通道参与 one-hot 选择：

```scala
// BypassNetwork.scala — 数据来源 one-hot 选择
val originSrc = Mux1H(Seq(
  readForward  -> forwardData,      // DataSource.forward
  readBypass   -> bypassData,       // DataSource.bypass
  readBypass2  -> bypass2Data,      // DataSource.bypass2
  readZero     -> 0.U,              // DataSource.zero
  readV0       -> v0Data,           // DataSource.v0
  readRegOH    -> rfData,           // DataSource.reg
  readRegCache -> regCacheData,     // DataSource.regcache ← RegCache
  readImm      -> immData,          // DataSource.imm
))
```

***

## 11.9 总结

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **RegCache 的定位**：RF 旁边的"便利店"，缓存最近写回的整数数据，减少 RF 读端口竞争
* **读写机制**：写入与 IQ 唤醒绑定，同时写数据和 Tag；读取通过 Tag 查询物理寄存器编号，命中则从 RegCache 取数据，未命中则退回 RF
* **替换策略**：Age Timer 计时（写入重置、读取保持、空闲递增、饱和不变），替换最久未使用的项；无效项优先替换；替换索引延迟 3 拍与数据对齐
* **取消机制**：新分配覆盖 / Tag 更新 / Load Cancel 三种场景触发取消，保证 RegCache 只存有效数据
* **Bank Set 划分**：IntRegCache（服务整数执行单元）和 MemRegCache（服务访存执行单元），通过 RegCacheIdx 的 MSB 选择，独立管理替换和 Tag 表
* **DataPath 协作**：DataSource 在 Wakeup Queue 中确定，BypassNetwork 做 one-hot 选择；RegCache 作为独立数据通道与 RF、Forward、Bypass 等并列

核心原则：RegCache 是\*\*"用空间换带宽"\*\*的典型设计——用少量额外的存储空间，换取 RF 读端口的显著减负。而分 Bank Set 则进一步将写端口压力分摊到两个独立的存储体上，实现更精细的资源管理。


> 更新: 2026-07-01 18:52:47  
