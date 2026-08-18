<!--
Original Chinese source retained in full for review.

# 8. Bypass Network

🔄在前面两章，我们看到了 Issue Queue 如何选出就绪的指令发射出去。但发射之后，指令的数据从哪里来？如果每条指令都要等结果写回寄存器堆再读取，那延迟可就太大了。Bypass Network 就是解决这个问题的"快递直达通道"。

:::info
读完本章，你将能够：

* ✅ 理解 Bypass Network 在数据通路中的角色和必要性
* ✅ 掌握七种数据来源的分类与选择逻辑
* ✅ 认识 Forward / Bypass / Bypass2 三级旁路的时序差异
* ✅ 了解跨调度域旁路的特殊处理

:::

***

## 8.1 整体定位：为什么需要旁路网络？

你可以把处理器的数据通路想象成一座城市的交通系统：

* **寄存器堆** = 公共仓库——所有人都要去仓库取货，但仓库有读端口数量限制，且读取有延迟
* **旁路网络** = 直达快线——上一家工厂刚产出的零件，不经过仓库，直接送到下一家工厂

如果不用旁路，一条加法指令的结果写回寄存器堆需要 1 拍，下一条依赖它的指令再从寄存器堆读出来又要 1 拍——白白浪费了 2 拍。旁路网络让结果直接从执行单元的输出"飞"到下一个执行单元的输入，省去了中间的寄存器堆读写延迟。

> ***核心价值：Bypass Network 是乱序处理器实现低延迟数据传递的关键基础设施，没有它，指令间的依赖将导致大量流水线气泡。***

***

## 8.2 七种数据来源

当 Issue Queue 发射一条 uop 到执行单元时，每个源操作数的数据从哪里来？香山定义了七种可能的数据来源，由 DataSource 模块编码：

```scala
// DataSource.scala
object DataSource {
  def reg: UInt      = "b1000".U   // 寄存器堆
  def regcache: UInt = "b0110".U   // 寄存器缓存
  def v0: UInt       = "b0101".U   // V0 寄存器
  def imm: UInt      = "b0100".U   // 立即数
  def bypass2: UInt  = "b0011".U   // 2 拍旁路
  def bypass: UInt   = "b0010".U   // 1 拍旁路
  def forward: UInt  = "b0001".U   // 当拍前递
  def zero: UInt     = "b0000".U   // 零寄存器
}
```

| **数据来源** | **编码** | **含义** | **比喻** |
| --- | --- | --- | --- |
| **zero** | <code>**0000**</code> | 读零寄存器（x0），值恒为 0 | 空盒子——本来就没东西 |
| **forward** | <code>**0001**</code> | 当拍前递——从执行单元输出直接获取 | 顺丰当日达 |
| **bypass** | <code>**0010**</code> | 1 拍旁路——延迟 1 周期的前递 | 次日达 |
| **bypass2** | <code>**0011**</code> | 2 拍旁路——延迟 2 周期的前递 | 隔日达 |
| **imm** | <code>**0100**</code> | 立即数——从指令字中提取 | 自带行李，不用取 |
| **regcache** | <code>**0110**</code> | 寄存器缓存——从 RegCache 快速读取 | 就近仓库取货 |
| **v0** | <code>**0101**</code> | V0 寄存器——专用向量寄存器 | 专用通道 |
| **reg** | <code>**1000**</code> | 寄存器堆——从物理寄存器堆正常读取 | 去总仓库取货 |

:::warning
❤️[此处为语雀卡片，点击链接查看](https://www.yuque.com/staff-xmw8rg/fb7qy3/ufmf4kq3vmz15du4#t4wyc)

新手建议\
你不需要记住所有编码。核心只需理解一件事：**数据来源的选择是根据"数据什么时候就绪"决定的**。越早就绪的数据，用越快的通道；如果数据已经在寄存器堆里了，就正常读取。

:::

***

## 8.3 Forward / Bypass / Bypass2：三级旁路

三级旁路的区别在于**数据从执行单元输出到消费端的延迟**

```scala
// BypassNetwork.scala# — 三级旁路数据生成
// forward: 当拍数据，零延迟
private val forwardDataVec: Vec[UInt] = VecInit(
  fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth))
)
 
// bypass: 1 拍延迟，RegNext 寄存
private val bypassDataVec = VecInit(
  fromExus.map(x => {
    if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
      ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)  // 跨域需额外处理
    else ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
  })
)
 
// bypass2: 2 拍延迟，RegNext(RegNext(...))
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```

### 8.3.1 时序关系

```plain
时钟周期:      T          T+1          T+2       T+3
                                    ┌──────────────────────┐
         指令 A 发射 ──→ 执行中 ──→ 	│  结果就绪（写回）   	 │
                                    └──────────────────────┘
                                    │          │           │
                                    ▼          ▼           ▼
      指令 B（依赖A）             forward    bypass     bypass2
                                （当拍前递） （1拍旁路）  （2拍旁路）
```

| **旁路级别** | **延迟** | **数据来源** | **适用场景** |
| --- | --- | --- | --- |
| forward | 0 拍 | 执行单元当拍输出 | 同调度域内，ALU 等低延迟单元 |
| bypass | 1 拍 | 执行单元输出寄存一拍 | 同调度域内，延迟不确定的单元 |
| bypass2 | 2 拍 | bypass 数据再寄存一拍 | 跨调度域（VF→VF/Mem） |

### 8.3.2 Forward：当拍前递

Forward 是最快的旁路方式。当一条指令在当前周期写回结果，而另一条依赖它的指令恰好在同一周期发射时，数据可以通过 Forward 通路当拍传递，无需任何等待。

Forward 数据直接取自执行单元的输出端口，不经过任何寄存器打拍：

```scala
// BypassNetwork.scala
private val forwardDataVec: Vec[UInt] = VecInit(
  fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth))
  // x.bits.data 是执行单元当拍输出的写回数据，零延迟
)
```

### 8.3.3 Bypass：1 拍旁路

有些执行单元的结果需要 1 拍后才能稳定（比如跨时钟域同步、或者需要经历一级流水），此时用 Bypass。Bypass 数据是执行单元输出的寄存 1 拍版本：

```scala
// BypassNetwork.scala
private val bypassDataVec = VecInit(
  fromExus.map(x => {
    if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
      // 跨域数据转换（整数↔浮点）：用 RegNext 无条件打一拍
      ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)
    else
      // 同域数据：用 RegEnable 条件打一拍，只有 valid 时才更新
      ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
  })
)
```

两种寄存策略的区别：

* **RegNext**：无条件每拍更新，用于跨域数据（I2F/F2I），因为数据格式需要稳定
* **RegEnable**：带使能的条件更新，只有 <code>**valid=1**</code> 时才锁存新数据，节省翻转功耗

### 8.3.4 Bypass2：2 拍旁路

Bypass2 是**专为跨调度域旁路设计的**。当向量执行单元（VF Exu）产生结果，需要旁路到整数/访存执行单元时，由于时序对齐的差异，数据需要额外延迟 2 拍。

```scala
// BypassNetwork.scala
// bypass2 的 source 限定：写向量寄存器 + (VF执行单元 或 含Load)
private val fromDPsHasBypass2Source = fromDPs.filter(x => 
  x.bits.exuParams.isIQWakeUpSource && x.bits.exuParams.writeVfRf && 
  (x.bits.exuParams.isVfExeUnit || x.bits.exuParams.hasLoadExu))
 
// bypass2 的 sink 限定：读向量寄存器 + (VF执行单元 或 Mem执行单元)
private val fromDPsHasBypass2Sink = fromDPs.filter(x => 
  x.bits.exuParams.isIQWakeUpSink && x.bits.exuParams.readVfRf && 
  (x.bits.exuParams.isVfExeUnit || x.bits.exuParams.isMemExeUnit))
 
// bypass2 数据 = bypass 数据再打一拍
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```

| **源** | **汇** | **原因** |
| --- | --- | --- |
| VF Exu（写向量寄存器堆） | VF Exu（读向量寄存器堆） | 向量域内部跨 IQ 旁路 |
| 带 Load 的 Exu | VF/Mem Exu（读向量寄存器堆） | Load 数据延迟较长 |

***

## 8.4 数据来源选择的完整逻辑

### 8.4.1 谁来决定用哪个来源？

数据来源的决策**不是 Bypass Network 自己做的**，而是在 Issue Queue 阶段就已经确定。Issue Queue 中的每个 Entry 会根据唤醒信息，为每个源操作数计算出一个 <code>**DataSource**</code> 值，随 uop 一起传递给 Bypass Network。

Bypass Network 只是一个**执行者**——根据 <code>**DataSource**</code> 的指示，从对应的通道取出数据，拼装成完整的执行单元输入。

### 8.4.2 选择优先级

BypassNetwork 中，每个源操作数通过 Mux1H 从多种数据来源中选择：

```scala
// BypassNetwork.scala
val originSrc = Mux1H(
  Seq(
    readForward    -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec),
    readBypass     -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), bypassDataVec),
    readBypass2    -> (if (bypass2ExuIdx >= 0) 
                       Mux1H(bypass2ValidVec3(bypass2ExuIdx)(srcIdx), bypass2DataVec) 
                       else 0.U),
    readZero       -> 0.U,
    readV0         -> (if (srcIdx < 3 && isReadVfRf) exuInput.bits.src(3) else 0.U),
    readRegOH      -> fromDPs(exuIdx).bits.src(srcIdx),    // ← 来自寄存器堆
    readRegCache   -> fromDPsRCData(exuIdx)(srcIdx),       // ← 来自 RegCache
    readImm        -> (if (exuParm.hasLoadExu && srcIdx == 0) immLoadSrc0.get 
                       else if (exuParm.aluNeedPc) immALU else imm)
  )
)
src := originSrc
```

:::warning
💡 核心思想\
每个源操作数在任意时刻**只有一种**数据来源是有效的。这就像你不会同时从快递柜和家里取同一个包裹——系统保证每个包裹只有一条路径。

:::

### 8.4.3 数据来源的匹配机制

Forward 和 Bypass 的核心操作是匹配物理寄存器编号。匹配信息在 Issue Queue 的唤醒阶段就已经计算好了——以 <code>**exuSources**</code>（one-hot 编码的执行单元索引）的形式传递：

```scala
// BypassNetwork.scala
// exuSources 是 one-hot 编码：标记该源操作数由哪个执行单元产出
private val forwardOrBypassValidVec3 = MixedVecInit(
  fromDPs.map { x =>
    VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.exuParams))).getOrElse(
      VecInit(Seq.fill(x.bits.exuParams.numRegSrc max 1)(VecInit(0.U(params.numExu.W).asBools)))
    ))
  }
)
```

Bypass Network 只需做一次 <code>**Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec)**</code> 即可选中对应的写回数据——无需在 Bypass Network 内部做物理寄存器号比较，所有匹配逻辑已在 IQ 唤醒阶段完成。

***

## 8.5 跨调度域旁路

香山的后端分为三个调度域：**Int（整数）**、**FP（浮点）**、**VF（向量浮点）**。跨域旁路是时序最复杂的情况。

### 8.5.1 为什么跨域旁路更难？

不同调度域的执行单元延迟不同、流水级数不同。比如整数 ALU 可能只需 1 拍，而向量 ALU 可能需要 3-4 拍。当一个域的执行单元产出的数据需要被另一个域的执行单元消费时，必须做好**时序对齐**。

### 8.5.2 I2F 与 F2I：整数与浮点之间的桥接

某些浮点指令需要整数操作数（如 <code>**FMV.X.W**</code> 的逆操作），某些整数指令需要浮点结果（如 <code>**FMV.W.X**</code> 的逆操作）。这些跨域数据需要额外的**数据类型转换**和**时钟门控处理**：

```scala
// BypassNetwork.scala
if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
  // 跨域：用 RegNext 无条件打一拍，因为数据格式需要稳定
  ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)
else
  // 同域：用 RegEnable 条件打一拍，省功耗
  ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
```

* **I2F（Int to Float）**：整数结果旁路到浮点执行单元时，数据需要打 1 拍（<code>**RegNext**</code>），因为时序对齐不同
* **F2I（Float to Int）**：浮点结果旁路到整数执行单元时，同样需要打 1 拍

### 8.5.3 VF 到 Int/Mem：最长的旁路路径

向量执行单元到 VF/Mem 域的旁路需要 **Bypass2（2 拍延迟）**，这是整个旁路网络中最长的路径。

```scala
// BypassNetwork.scala
// source: 写向量RF + (VF执行单元 或 含Load的执行单元)
// sink:   读向量RF + (VF执行单元 或 Mem执行单元)
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))  // bypass 再打一拍 = 2拍
)
```

原因有二：

1. **向量执行单元延迟更长**：向量数据通路比整数宽得多（128-256 bit），走线延迟更长
2. **跨 IQ 旁路**：source 和 sink 可能在不同的 Issue Queue 中，唤醒信号需要跨 IQ 传播，时序裕量更紧张

### 8.5.4 BypassNetwork 的 RegCache 写回

BypassNetwork 除了向执行单元提供旁路数据，还负责将 bypass 数据写回 RegCache：

```scala
// BypassNetwork.scala
// forward 级别的 RegCache 写信息
private val forwardIntWenVec = VecInit(
  fromExus.filter(_.bits.params.needWriteRegCache).map(x => x.valid && x.bits.intWen))
private val forwardTagVec = VecInit(
  fromExus.filter(_.bits.params.needWriteRegCache).map(x => x.bits.pdest))
 
// bypass 级别（打一拍后）的 RegCache 写信息
private val bypassIntWenVec = VecInit(forwardIntWenVec.map(x => GatedValidRegNext(x)))
private val bypassTagVec = VecInit(forwardTagVec.zip(forwardIntWenVec).map(x => RegEnable(x._1, x._2)))
private val bypassRCDataVec = VecInit(
  fromExus.zip(bypassDataVec).filter(_._1.bits.params.needWriteRegCache).map(_._2))
 
// 写入 RegCache
io.toDataPath.zipWithIndex.foreach{ case (x, i) => 
  x.wen  := bypassIntWenVec(i)    // RegCache 写使能（bypass 级别）
  x.data := bypassRCDataVec(i)    // RegCache 写数据（bypass 级别）
  x.tag.foreach(_ := bypassTagVec(i))  // RegCache 写标签
}
```

RegCache 的写入使用的是 **bypass 级别**（1 拍延迟）的数据，而不是 forward 级别的。这是因为 RegCache 的写入需要数据稳定后才能进行。

***

## 8.6 与 DataPath 的协作

Bypass Network 并不是孤立工作的，它是 **DataPath** 模块的一部分。DataPath 负责从 Issue Queue 出口到执行单元入口的**完整数据通路**，包括：

| **组件** | **职责** | **比喻** |
| --- | --- | --- |
| **RFReadArbiter（×5）** | 仲裁多个执行单元对 5 类物理寄存器堆（int/fp/vec/v0/vl）的并发读请求 | 仓库取货窗口调度员 |
| **WBCollideChecker（×5）** | 检查写回端口是否冲突，避免同一周期多条指令竞争同一写回端口 | 发货窗口排队检查 |
| **BypassNetwork** | 从多个数据来源中选出正确的操作数 | 快递分拣员——根据标签选择发货通道 |
| **RegCache** | 为整数寄存器提供快速读取通道 | 就近便利店——不用跑总仓库 |

从源码可以看到这些组件的实例化（DataPath.scala）：

```scala
// 5 类写回冲突检查器
private val intWbBusyArbiter = Module(new IntRFWBCollideChecker(backendParams))
private val fpWbBusyArbiter  = Module(new FpRFWBCollideChecker(backendParams))
private val vfWbBusyArbiter  = Module(new VfRFWBCollideChecker(backendParams))
private val v0WbBusyArbiter  = Module(new V0RFWBCollideChecker(backendParams))
private val vlWbBusyArbiter  = Module(new VlRFWBCollideChecker(backendParams))
 
// 5 类寄存器堆读仲裁器
private val intRFReadArbiter = Module(new IntRFBankReadArbiter(backendParams))
private val fpRFReadArbiter  = Module(new FpRFReadArbiter(backendParams))
private val vfRFReadArbiter  = Module(new VfRFReadArbiter(backendParams))
private val v0RFReadArbiter  = Module(new V0RFReadArbiter(backendParams))
private val vlRFReadArbiter  = Module(new VlRFReadArbiter(backendParams))
```

RFReadArbiter 的工作方式——按数据类型过滤源端口，只对需要读该类型寄存器堆的源发起仲裁：

```scala
// DataPath.scala — 整数RF读仲裁
intRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
  arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
    val srcIndices = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
    for (srcIdx <- 0 until numRegSrc) {
      if (srcIndices.contains(srcIdx)) {
        arbInSeq(srcIdx).valid := intRFBankRen(iqIdx)(exuIdx).get(srcIdx).asUInt.orR
        arbInSeq(srcIdx).bits.addr := fromIQDeqOg1Payload(iqIdx)(exuIdx).psrc(srcIdx)
        // ...
      } else {
        arbInSeq(srcIdx).valid := false.B  // 该源不需要读整数RF
      }
    }
  }
}
```

**协作流程**：

![36d7a0f99d480735151f8ccdcdc5fbdb.svg](img/8-bypass-network/figure-001-8-bypass-network-2.svg)

***

## 8.7 Bypass Network 的时序压力

### 8.7.1 关键时序路径

| **路径** | **描述** | **严重程度** |
| --- | --- | --- |
| Forward 数据选择 | N 个执行单元的写回数据做 Mux1H | 🔴 极高 |
| pdest 匹配 | 物理寄存器编号比对（在 IQ 阶段完成，结果以 exuSources 传递） | 🟡 已优化 |
| 跨域旁路 | I2F/F2I/VF2VF 的多拍延迟 | 🟡 中等 |
| Imm 提取 | 立即数解码与符号扩展 | 🟢 较低 |
| RFReadArbiter 仲裁 | 多个执行单元竞争有限读端口 | 🟡 中等 |

### 8.7.2 优化策略

**策略一：匹配前移到 Issue Queue**

Forward/Bypass 的物理寄存器匹配逻辑（"哪个执行单元写了我的源操作数"）是 O(N×M) 的比较操作（N 个源 × M 个执行单元）。香山将这部分逻辑**前移到 Issue Queue 的唤醒阶段**完成，以 <code>**exuSources**</code>（one-hot 编码）的形式传递给 Bypass Network。这样 Bypass Network 只需做一次简单的 <code>**Mux1H**</code> 选择，而不需要做比对。

```scala
// BypassNetwork.scala — exuSources 在 IQ 阶段计算好
private val forwardOrBypassValidVec3 = MixedVecInit(
  fromDPs.map { x =>
    VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.exuParams))).getOrElse(...))
  }
)
// BypassNetwork 只做 Mux1H，不做匹配
Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec)
```

**策略二：RegCache 减少寄存器堆读压力**

对于整数操作数，香山引入了 **RegCache（寄存器缓存）**。写回数据在经过 BypassNetwork 时同时写入 RegCache（bypass 级别，1 拍延迟），后续指令可以从 RegCache 快速读取，不必竞争寄存器堆有限的读端口：

```scala
// BypassNetwork.scala — bypass 数据写回 RegCache
io.toDataPath.zipWithIndex.foreach{ case (x, i) => 
  x.wen  := bypassIntWenVec(i)     // RegCache 写使能
  x.data := bypassRCDataVec(i)     // RegCache 写数据
  x.tag.foreach(_ := bypassTagVec(i))
}
```

这就像在仓库旁边开了一家便利店——常用货物就近取，不用每次都跑总仓。

**策略三：Bypass 数据门控**

Bypass 数据使用 RegEnable（带使能的寄存器）而非简单的 RegNext，只有当写回有效时才锁存数据。对于 I2F/F2I 等跨域旁路，则使用 RegNext 确保时序。这种差异化处理避免了不必要的寄存器翻转，节省了动态功耗：

```scala
// BypassNetwork.scala
if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
  ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)          // 跨域：无条件打拍
else
  ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth) // 同域：条件打拍
```

***

## 8.8 总结

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **为什么需要旁路**：不经过寄存器堆，让执行单元的结果直达下游，消除数据依赖延迟
* **八种数据来源**：zero / forward / bypass / bypass2 / imm / v0 / regcache / reg——根据数据何时就绪选择最合适的通道，每个源操作数在任意时刻只有一种来源有效（DataSource one-hot 编码）
* **三级旁路**：Forward（当拍）→ Bypass（1 拍）→ Bypass2（2 拍），对应不同的时序场景；跨域旁路（I2F、F2I 用 RegNext，VF→VF/Mem 用 Bypass2）需要额外延迟
* **与 DataPath 协作**：5 类 RFReadArbiter 仲裁读端口、5 类 WBCollideChecker 检查写回冲突、RegCache 缓存整数数据减少读端口竞争
* **时序优化**：匹配前移到 IQ 阶段（以 exuSources 传递）、RegCache 减少读端口竞争、差异化门控（RegEnable vs RegNext）节省功耗

核心原则：Bypass Network 的设计哲学是\*\*"让数据走最短的路径"**——数据刚产生就送走，能不经过寄存器堆就不经过。而时序优化的核心是**"把计算前移、把选择简化"\*\*——复杂的匹配逻辑交给 IQ，Bypass Network 只做轻量的 Mux 选择。


> 更新: 2026-07-01 16:37:51  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/ufmf4kq3vmz15du4>
-->

# 8. Bypass Network

<!-- 🔄在前面两章，我们看到了 Issue Queue 如何选出就绪的指令发射出去。但发射之后，指令的数据从哪里来？如果每条指令都要等结果写回寄存器堆再读取，那延迟可就太大了。Bypass Network 就是解决这个问题的"快递直达通道"。 -->
In the previous two chapters, we saw how the Issue Queue selects ready instructions for issue. But where does an instruction obtain its data after issue? Waiting for every result to be written back to the register file and then read again would add substantial latency. The Bypass Network provides a direct express path for this data.

:::info
<!-- 读完本章，你将能够： -->
After reading this chapter, you will be able to:

<!-- * ✅ 理解 Bypass Network 在数据通路中的角色和必要性 -->
* ✅ Understand the role and necessity of the Bypass Network in the datapath
<!-- * ✅ 掌握七种数据来源的分类与选择逻辑 -->
* ✅ Understand the classification and selection logic for the eight data sources
<!-- * ✅ 认识 Forward / Bypass / Bypass2 三级旁路的时序差异 -->
* ✅ Recognize the timing differences among the three bypass levels: Forward, Bypass, and Bypass2
<!-- * ✅ 了解跨调度域旁路的特殊处理 -->
* ✅ Understand the special handling required for bypasses across scheduling domains

:::

***

<!-- ## 8.1 整体定位：为什么需要旁路网络？ -->
## 8.1 Overall Positioning: Why Is a Bypass Network Needed?

<!-- 你可以把处理器的数据通路想象成一座城市的交通系统： -->
You can think of the processor datapath as a city's transportation system:

<!-- * **寄存器堆** = 公共仓库——所有人都要去仓库取货，但仓库有读端口数量限制，且读取有延迟 -->
* **Register file** = a public warehouse: everyone must collect parts there, but the warehouse has a limited number of read ports and nonzero read latency
<!-- * **旁路网络** = 直达快线——上一家工厂刚产出的零件，不经过仓库，直接送到下一家工厂 -->
* **Bypass Network** = a direct express route: a part just produced by one factory goes straight to the next factory without passing through the warehouse

<!-- 如果不用旁路，一条加法指令的结果写回寄存器堆需要 1 拍，下一条依赖它的指令再从寄存器堆读出来又要 1 拍——白白浪费了 2 拍。旁路网络让结果直接从执行单元的输出"飞"到下一个执行单元的输入，省去了中间的寄存器堆读写延迟。 -->
Without bypassing, writing an add result to the register file costs one cycle, and reading it back for the dependent instruction costs another cycle, wasting two cycles. The Bypass Network sends the result directly from an execution unit's output to the next execution unit's input, eliminating the intervening register-file read and write latency.

<!-- > ***核心价值：Bypass Network 是乱序处理器实现低延迟数据传递的关键基础设施，没有它，指令间的依赖将导致大量流水线气泡。*** -->
> ***Core value: The Bypass Network is essential infrastructure for low-latency data transfer in an out-of-order processor. Without it, inter-instruction dependencies would create many pipeline bubbles.***

***

<!-- ## 8.2 七种数据来源 -->
## 8.2 Eight Data Sources

<!-- 当 Issue Queue 发射一条 uop 到执行单元时，每个源操作数的数据从哪里来？香山定义了七种可能的数据来源，由 DataSource 模块编码： -->
When the Issue Queue issues a uop to an execution unit, where does each source operand come from? XiangShan defines eight possible data sources, encoded by the `DataSource` module:

<!--
```scala
// DataSource.scala
object DataSource {
  def reg: UInt      = "b1000".U   // 寄存器堆 / Register file
  def regcache: UInt = "b0110".U   // 寄存器缓存 / Register cache
  def v0: UInt       = "b0101".U   // V0 寄存器 / V0 register
  def imm: UInt      = "b0100".U   // 立即数 / Immediate
  def bypass2: UInt  = "b0011".U   // 2 拍旁路 / Two-cycle bypass
  def bypass: UInt   = "b0010".U   // 1 拍旁路 / One-cycle bypass
  def forward: UInt  = "b0001".U   // 当拍前递 / Same-cycle forwarding
  def zero: UInt     = "b0000".U   // 零寄存器 / Zero register
}
```
-->
```scala
// DataSource.scala
object DataSource {
  def reg: UInt      = "b1000".U   // Physical register file.
  def regcache: UInt = "b0110".U   // Register cache.
  def v0: UInt       = "b0101".U   // V0 register.
  def imm: UInt      = "b0100".U   // Immediate value.
  def bypass2: UInt  = "b0011".U   // Two-cycle bypass.
  def bypass: UInt   = "b0010".U   // One-cycle bypass.
  def forward: UInt  = "b0001".U   // Same-cycle forwarding.
  def zero: UInt     = "b0000".U   // Zero register.
}
```

<!-- | **数据来源** | **编码** | **含义** | **比喻** | -->
<!-- | --- | --- | --- | --- | -->
<!-- | **zero** | <code>**0000**</code> | 读零寄存器（x0），值恒为 0 | 空盒子——本来就没东西 | -->
<!-- | **forward** | <code>**0001**</code> | 当拍前递——从执行单元输出直接获取 | 顺丰当日达 | -->
<!-- | **bypass** | <code>**0010**</code> | 1 拍旁路——延迟 1 周期的前递 | 次日达 | -->
<!-- | **bypass2** | <code>**0011**</code> | 2 拍旁路——延迟 2 周期的前递 | 隔日达 | -->
<!-- | **imm** | <code>**0100**</code> | 立即数——从指令字中提取 | 自带行李，不用取 | -->
<!-- | **regcache** | <code>**0110**</code> | 寄存器缓存——从 RegCache 快速读取 | 就近仓库取货 | -->
<!-- | **v0** | <code>**0101**</code> | V0 寄存器——专用向量寄存器 | 专用通道 | -->
<!-- | **reg** | <code>**1000**</code> | 寄存器堆——从物理寄存器堆正常读取 | 去总仓库取货 | -->
| **Data source** | **Encoding** | **Meaning** | **Analogy** |
| --- | --- | --- | --- |
| **zero** | <code>**0000**</code> | Read the zero register (x0), whose value is always 0 | An empty box: it contains nothing by definition |
| **forward** | <code>**0001**</code> | Same-cycle forwarding directly from an execution-unit output | Same-day delivery |
| **bypass** | <code>**0010**</code> | One-cycle bypass forwarding | Next-day delivery |
| **bypass2** | <code>**0011**</code> | Two-cycle bypass forwarding | Delivery the day after next |
| **imm** | <code>**0100**</code> | Immediate value extracted from the instruction word | Carry-on baggage: no pickup required |
| **regcache** | <code>**0110**</code> | Fast read from the RegCache | Pick up from a nearby warehouse |
| **v0** | <code>**0101**</code> | The dedicated vector V0 register | A dedicated route |
| **reg** | <code>**1000**</code> | Normal read from the physical register file | Pick up from the central warehouse |

:::warning
<!-- ❤️[此处为语雀卡片，点击链接查看](https://www.yuque.com/staff-xmw8rg/fb7qy3/ufmf4kq3vmz15du4#t4wyc) -->
❤️[Yuque card; click the link to view it](https://www.yuque.com/staff-xmw8rg/fb7qy3/ufmf4kq3vmz15du4#t4wyc)

<!-- 新手建议\
你不需要记住所有编码。核心只需理解一件事：**数据来源的选择是根据"数据什么时候就绪"决定的**。越早就绪的数据，用越快的通道；如果数据已经在寄存器堆里了，就正常读取。 -->
**Beginner's tip**\
You do not need to memorize every encoding. The key idea is that **the source is selected according to when the data becomes ready**. Use the fastest path for data that becomes ready early; if the value is already in the register file, read it normally.

:::

***

<!-- ## 8.3 Forward / Bypass / Bypass2：三级旁路 -->
## 8.3 Forward / Bypass / Bypass2: Three Bypass Levels

<!-- 三级旁路的区别在于**数据从执行单元输出到消费端的延迟** -->
The three bypass levels differ in the **latency from an execution-unit output to the consumer**.

<!--
```scala
// BypassNetwork.scala# — 三级旁路数据生成 / Three-level bypass data generation
// forward: 当拍数据，零延迟 / same-cycle data, zero latency
private val forwardDataVec: Vec[UInt] = VecInit(
  fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth))
)

// bypass: 1 拍延迟，RegNext 寄存 / one-cycle latency, registered with RegNext
private val bypassDataVec = VecInit(
  fromExus.map(x => {
    if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
      ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)  // 跨域需额外处理 / extra handling for cross-domain data
    else ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
  })
)

// bypass2: 2 拍延迟，RegNext(RegNext(...)) / two-cycle latency, RegNext(RegNext(...))
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```
-->
```scala
// BypassNetwork.scala - three-level bypass data generation.
// Forward carries same-cycle data with zero latency.
private val forwardDataVec: Vec[UInt] = VecInit(
  fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth))
)

// Bypass is registered for one cycle with RegNext or RegEnable.
private val bypassDataVec = VecInit(
  fromExus.map(x => {
    if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
      ZeroExt(RegNext(x.bits.data), RegDataMaxWidth) // Extra handling for cross-domain data.
    else ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
  })
)

// Bypass2 registers the bypass data for one additional cycle.
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```

<!-- ### 8.3.1 时序关系 -->
### 8.3.1 Timing Relationship

<!--
```plain
&lt;!-- 时钟周期:      T          T+1          T+2       T+3 --&gt;
Clock cycle:      T          T+1          T+2       T+3
                                    ┌──────────────────────┐
         Instruction A issued ──→ executing ──→ 	│  result ready (writeback) 	 │
                                    └──────────────────────┘
                                    │          │           │
                                    ▼          ▼           ▼
      Instruction B (depends on A) forward    bypass     bypass2
                                (same-cycle) (1-cycle)  (2-cycle)
```
-->
```plain
Clock cycle:      T          T+1          T+2       T+3
                                    ┌──────────────────────┐
         Instruction A issued ──→ executing ──→ 	│  result ready (writeback) 	 │
                                    └──────────────────────┘
                                    │          │           │
                                    ▼          ▼           ▼
      Instruction B (depends on A) forward    bypass     bypass2
                                (same-cycle) (1-cycle)  (2-cycle)
```

<!-- | **旁路级别** | **延迟** | **数据来源** | **适用场景** | -->
<!-- | --- | --- | --- | --- | -->
<!-- | forward | 0 拍 | 执行单元当拍输出 | 同调度域内，ALU 等低延迟单元 | -->
<!-- | bypass | 1 拍 | 执行单元输出寄存一拍 | 同调度域内，延迟不确定的单元 | -->
<!-- | bypass2 | 2 拍 | bypass 数据再寄存一拍 | 跨调度域（VF→VF/Mem） | -->
| **Bypass level** | **Latency** | **Data source** | **Typical use** |
| --- | --- | --- | --- |
| forward | 0 cycles | Same-cycle output of the execution unit | Low-latency units such as ALUs within one scheduling domain |
| bypass | 1 cycle | Execution-unit output registered for one cycle | Units with uncertain latency within one scheduling domain |
| bypass2 | 2 cycles | Bypass data registered for one additional cycle | Across scheduling domains (VF to VF/Mem) |

<!-- ### 8.3.2 Forward：当拍前递 -->
### 8.3.2 Forward: Same-Cycle Forwarding

Forward is the fastest bypass mode. If one instruction writes back its result in the current cycle and a dependent instruction is issued in that same cycle, the data can travel through the Forward path without waiting.

Forward data is taken directly from the execution unit's output port, without passing through a register stage:

<!--
```scala
// BypassNetwork.scala
private val forwardDataVec: Vec[UInt] = VecInit(
  fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth))
  // x.bits.data 是执行单元当拍输出的写回数据，零延迟 / same-cycle writeback data; zero latency
)
```
-->
```scala
// BypassNetwork.scala
private val forwardDataVec: Vec[UInt] = VecInit(
  fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth))
  // Same-cycle writeback data with zero latency.
)
```

<!-- ### 8.3.3 Bypass：1 拍旁路 -->
### 8.3.3 Bypass: One-Cycle Bypass

Some execution-unit results become stable only one cycle later, for example after clock-domain synchronization or one pipeline stage. Such results use Bypass, which is the execution-unit output registered for one cycle:

<!--
```scala
// BypassNetwork.scala
private val bypassDataVec = VecInit(
  fromExus.map(x => {
    if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
      // 跨域数据转换（整数↔浮点）：用 RegNext 无条件打一拍 / cross-domain conversion: unconditionally register one cycle with RegNext
      ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)
    else
      // 同域数据：用 RegEnable 条件打一拍，只有 valid 时才更新 / same-domain data: conditionally register one cycle with RegEnable
      ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
  })
)
```
-->
```scala
// BypassNetwork.scala
private val bypassDataVec = VecInit(
  fromExus.map(x => {
    if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
      // Cross-domain conversion: unconditionally register one cycle with RegNext.
      ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)
    else
      // Same-domain data: conditionally register one cycle with RegEnable.
      ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
  })
)
```

<!-- 两种寄存策略的区别： -->
The two register strategies differ as follows:

<!-- * **RegNext**：无条件每拍更新，用于跨域数据（I2F/F2I），因为数据格式需要稳定 -->
* **RegNext**: Updates unconditionally every cycle for cross-domain data (I2F/F2I), whose format must remain stable
<!-- * **RegEnable**：带使能的条件更新，只有 <code>**valid=1**</code> 时才锁存新数据，节省翻转功耗 -->
* **RegEnable**: Updates conditionally and latches new data only when <code>**valid=1**</code>, reducing switching power

<!-- ### 8.3.4 Bypass2：2 拍旁路 -->
### 8.3.4 Bypass2: Two-Cycle Bypass

Bypass2 is **specifically designed for bypassing across scheduling domains**. When a vector execution unit (VF Exu) produces a result for an integer or memory execution unit, timing-alignment differences require an additional two-cycle delay.

<!--
```scala
// BypassNetwork.scala
// bypass2 的 source 限定：写向量寄存器 + (VF执行单元 或 含Load) / source restriction: writes the vector register file + (VF execution unit or an execution unit with Load)
private val fromDPsHasBypass2Source = fromDPs.filter(x =>
  x.bits.exuParams.isIQWakeUpSource && x.bits.exuParams.writeVfRf &&
  (x.bits.exuParams.isVfExeUnit || x.bits.exuParams.hasLoadExu))

// bypass2 的 sink 限定：读向量寄存器 + (VF执行单元 或 Mem执行单元) / sink restriction: reads the vector register file + (VF execution unit or Mem execution unit)
private val fromDPsHasBypass2Sink = fromDPs.filter(x =>
  x.bits.exuParams.isIQWakeUpSink && x.bits.exuParams.readVfRf &&
  (x.bits.exuParams.isVfExeUnit || x.bits.exuParams.isMemExeUnit))

// bypass2 数据 = bypass 数据再打一拍 / bypass data registered for one additional cycle
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```
-->
```scala
// BypassNetwork.scala
// Source restriction: writes the vector RF and is a VF or Load producer.
private val fromDPsHasBypass2Source = fromDPs.filter(x =>
  x.bits.exuParams.isIQWakeUpSource && x.bits.exuParams.writeVfRf &&
  (x.bits.exuParams.isVfExeUnit || x.bits.exuParams.hasLoadExu))

// Sink restriction: reads the vector RF and is a VF or memory consumer.
private val fromDPsHasBypass2Sink = fromDPs.filter(x =>
  x.bits.exuParams.isIQWakeUpSink && x.bits.exuParams.readVfRf &&
  (x.bits.exuParams.isVfExeUnit || x.bits.exuParams.isMemExeUnit))

// Register bypass data for one additional cycle, for two cycles total.
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```

<!-- | **源** | **汇** | **原因** | -->
<!-- | --- | --- | --- | -->
<!-- | VF Exu（写向量寄存器堆） | VF Exu（读向量寄存器堆） | 向量域内部跨 IQ 旁路 | -->
<!-- | 带 Load 的 Exu | VF/Mem Exu（读向量寄存器堆） | Load 数据延迟较长 | -->
| **Source** | **Sink** | **Reason** |
| --- | --- | --- |
| VF Exu (writes the vector register file) | VF Exu (reads the vector register file) | Cross-IQ bypass within the vector domain |
| Exu with Load | VF/Mem Exu (reads the vector register file) | Load data has longer latency |

***

<!-- ## 8.4 数据来源选择的完整逻辑 -->
## 8.4 Complete Data-Source Selection Logic

<!-- ### 8.4.1 谁来决定用哪个来源？ -->
### 8.4.1 Who Decides Which Source to Use?

The data-source decision is **not made by the Bypass Network itself**; it is determined in the Issue Queue. Each Issue Queue entry uses wakeup information to compute a <code>**DataSource**</code> value for every source operand and passes it to the Bypass Network together with the uop.

The Bypass Network is only the **executor**: it follows <code>**DataSource**</code>, selects data from the indicated channel, and assembles the complete execution-unit input.

<!-- ### 8.4.2 选择优先级 -->
### 8.4.2 Selection Priority

In `BypassNetwork`, each source operand is selected from multiple data sources with `Mux1H`:

```scala
// BypassNetwork.scala
val originSrc = Mux1H(
  Seq(
    readForward    -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec),
    readBypass     -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), bypassDataVec),
    readBypass2    -> (if (bypass2ExuIdx >= 0)
                       Mux1H(bypass2ValidVec3(bypass2ExuIdx)(srcIdx), bypass2DataVec)
                       else 0.U),
    readZero       -> 0.U,
    readV0         -> (if (srcIdx < 3 && isReadVfRf) exuInput.bits.src(3) else 0.U),
    readRegOH      -> fromDPs(exuIdx).bits.src(srcIdx),    // from the register file
    readRegCache   -> fromDPsRCData(exuIdx)(srcIdx),       // from RegCache
    readImm        -> (if (exuParm.hasLoadExu && srcIdx == 0) immLoadSrc0.get
                       else if (exuParm.aluNeedPc) immALU else imm)
  )
)
src := originSrc
```

:::warning
<!-- 💡 核心思想\
每个源操作数在任意时刻**只有一种**数据来源是有效的。这就像你不会同时从快递柜和家里取同一个包裹——系统保证每个包裹只有一条路径。 -->
💡 **Core idea**\
At any time, **exactly one** data source is valid for each source operand. You would not collect the same package from both a locker and home; the system guarantees one path per package.

:::

<!-- ### 8.4.3 数据来源的匹配机制 -->
### 8.4.3 Data-Source Matching Mechanism

The key operation for Forward and Bypass is matching physical-register numbers. The match is computed during Issue Queue wakeup and passed as <code>**exuSources**</code>, a one-hot encoded execution-unit index:

<!--
```scala
// BypassNetwork.scala
// exuSources 是 one-hot 编码：标记该源操作数由哪个执行单元产出 / one-hot encoded execution-unit source
private val forwardOrBypassValidVec3 = MixedVecInit(
  fromDPs.map { x =>
    VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.exuParams))).getOrElse(
      VecInit(Seq.fill(x.bits.exuParams.numRegSrc max 1)(VecInit(0.U(params.numExu.W).asBools)))
    ))
  }
)
```
-->
```scala
// BypassNetwork.scala
// exuSources is a one-hot execution-unit source computed during IQ wakeup.
private val forwardOrBypassValidVec3 = MixedVecInit(
  fromDPs.map { x =>
    VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.exuParams))).getOrElse(
      VecInit(Seq.fill(x.bits.exuParams.numRegSrc max 1)(VecInit(0.U(params.numExu.W).asBools)))
    ))
  }
)
```

The Bypass Network only needs one <code>**Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec)**</code> to select the corresponding writeback data. It does not compare physical-register numbers internally; all matching was completed during IQ wakeup.

***

<!-- ## 8.5 跨调度域旁路 -->
## 8.5 Bypassing Across Scheduling Domains

<!-- 香山的后端分为三个调度域：**Int（整数）**、**FP（浮点）**、**VF（向量浮点）**。跨域旁路是时序最复杂的情况。 -->
XiangShan's backend has three scheduling domains: **Int (integer)**, **FP (floating point)**, and **VF (vector floating point)**. Cross-domain bypassing is the most timing-sensitive case.

<!-- ### 8.5.1 为什么跨域旁路更难？ -->
### 8.5.1 Why Is Cross-Domain Bypassing Harder?

Execution-unit latency and pipeline depth differ across scheduling domains. An integer ALU may need only one cycle, whereas a vector ALU may need three or four. When data produced in one domain is consumed in another, **timing alignment** must be handled carefully.

<!-- ### 8.5.2 I2F 与 F2I：整数与浮点之间的桥接 -->
### 8.5.2 I2F and F2I: Bridging Integer and Floating-Point Domains

Some floating-point instructions need integer operands (for example, the inverse operation of <code>**FMV.X.W**</code>), while some integer instructions need floating-point results (for example, the inverse of <code>**FMV.W.X**</code>). These cross-domain values require additional **data-type conversion** and **clock-gating handling**:

<!--
```scala
// BypassNetwork.scala
// Cross-domain data is unconditionally registered for one cycle because its format must be stable.
if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
  // 跨域：用 RegNext 无条件打一拍，因为数据格式需要稳定 / cross-domain: unconditionally register one cycle with RegNext
  ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)
else
  // 同域：用 RegEnable 条件打一拍，省功耗 / same-domain: conditionally register one cycle with RegEnable
  ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
```
-->
```scala
// BypassNetwork.scala
if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
  // Cross-domain: unconditionally register one cycle with RegNext because the data format must be stable.
  ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)
else
  // Same-domain: conditionally register one cycle with RegEnable to save power.
  ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)
```

<!-- * **I2F（Int to Float）**：整数结果旁路到浮点执行单元时，数据需要打 1 拍（<code>**RegNext**</code>），因为时序对齐不同 -->
* **I2F (Int to Float)**: When an integer result is bypassed to a floating-point execution unit, it needs one <code>**RegNext**</code> cycle because timing alignment differs
<!-- * **F2I（Float to Int）**：浮点结果旁路到整数执行单元时，同样需要打 1 拍 -->
* **F2I (Float to Int)**: A floating-point result bypassed to an integer execution unit likewise needs one cycle

<!-- ### 8.5.3 VF 到 Int/Mem：最长的旁路路径 -->
### 8.5.3 VF to Int/Mem: The Longest Bypass Path

Bypassing from a vector execution unit to the VF/Mem domains requires **Bypass2 (two-cycle latency)**, the longest path in the entire bypass network.

<!--
```scala
// BypassNetwork.scala
// source: 写向量RF + (VF执行单元 或 含Load的执行单元) / writes the vector RF + (VF execution unit or execution unit with Load)
// sink:   读向量RF + (VF执行单元 或 Mem执行单元) / reads the vector RF + (VF execution unit or Mem execution unit)
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))  // bypass 再打一拍 = 2拍 / register bypass once more: two cycles total
)
```
-->
```scala
// BypassNetwork.scala
// Bypass data is registered for one additional cycle, for two cycles total.
private val bypass2DataVec = VecInit(
  fromDPsHasBypass2Source.map(x => RegNext(bypassDataVec(x)))
)
```

<!-- 原因有二： -->
There are two reasons:

<!-- 1. **向量执行单元延迟更长**：向量数据通路比整数宽得多（128-256 bit），走线延迟更长 -->
1. **Longer vector execution-unit latency**: Vector datapaths are much wider than integer datapaths (128-256 bits), so their wire delay is longer.
<!-- 2. **跨 IQ 旁路**：source 和 sink 可能在不同的 Issue Queue 中，唤醒信号需要跨 IQ 传播，时序裕量更紧张 -->
2. **Cross-IQ bypassing**: The source and sink may be in different Issue Queues, so wakeup signals must cross the IQ boundary, leaving less timing margin.

<!-- ### 8.5.4 BypassNetwork 的 RegCache 写回 -->
### 8.5.4 RegCache Writeback in BypassNetwork

In addition to supplying bypass data to execution units, `BypassNetwork` writes bypass data back to RegCache:

<!--
```scala
// BypassNetwork.scala
// forward 级别的 RegCache 写信息 / RegCache write information at the forward level
private val forwardIntWenVec = VecInit(
  fromExus.filter(_.bits.params.needWriteRegCache).map(x => x.valid && x.bits.intWen))
private val forwardTagVec = VecInit(
  fromExus.filter(_.bits.params.needWriteRegCache).map(x => x.bits.pdest))

// bypass 级别（打一拍后）的 RegCache 写信息 / RegCache write information at the bypass level
private val bypassIntWenVec = VecInit(forwardIntWenVec.map(x => GatedValidRegNext(x)))
private val bypassTagVec = VecInit(forwardTagVec.zip(forwardIntWenVec).map(x => RegEnable(x._1, x._2)))
private val bypassRCDataVec = VecInit(
  fromExus.zip(bypassDataVec).filter(_._1.bits.params.needWriteRegCache).map(_._2))

// 写入 RegCache / Write RegCache
io.toDataPath.zipWithIndex.foreach{ case (x, i) =>
  x.wen  := bypassIntWenVec(i)    // RegCache write enable (bypass level)
  x.data := bypassRCDataVec(i)    // RegCache write data (bypass level)
  x.tag.foreach(_ := bypassTagVec(i))  // RegCache write tag
}
```
-->
```scala
// BypassNetwork.scala
// Forward-level RegCache write information.
private val forwardIntWenVec = VecInit(
  fromExus.filter(_.bits.params.needWriteRegCache).map(x => x.valid && x.bits.intWen))
private val forwardTagVec = VecInit(
  fromExus.filter(_.bits.params.needWriteRegCache).map(x => x.bits.pdest))

// Bypass-level RegCache write information (one cycle later).
private val bypassIntWenVec = VecInit(forwardIntWenVec.map(x => GatedValidRegNext(x)))
private val bypassTagVec = VecInit(forwardTagVec.zip(forwardIntWenVec).map(x => RegEnable(x._1, x._2)))
private val bypassRCDataVec = VecInit(
  fromExus.zip(bypassDataVec).filter(_._1.bits.params.needWriteRegCache).map(_._2))

// Write the bypass-level data into RegCache.
io.toDataPath.zipWithIndex.foreach{ case (x, i) =>
  x.wen  := bypassIntWenVec(i)    // RegCache write enable (bypass level)
  x.data := bypassRCDataVec(i)    // RegCache write data (bypass level)
  x.tag.foreach(_ := bypassTagVec(i))  // RegCache write tag
}
```

RegCache writes use **bypass-level** data (one-cycle latency), rather than forward-level data, because a RegCache write must wait until the data is stable.

***

<!-- ## 8.6 与 DataPath 的协作 -->
## 8.6 Cooperation with DataPath

The Bypass Network does not operate in isolation; it is part of the **DataPath** module. DataPath provides the **complete datapath** from the Issue Queue outputs to the execution-unit inputs, including:

<!-- | **组件** | **职责** | **比喻** | -->
<!-- | --- | --- | --- | -->
<!-- | **RFReadArbiter（×5）** | 仲裁多个执行单元对 5 类物理寄存器堆（int/fp/vec/v0/vl）的并发读请求 | 仓库取货窗口调度员 | -->
<!-- | **WBCollideChecker（×5）** | 检查写回端口是否冲突，避免同一周期多条指令竞争同一写回端口 | 发货窗口排队检查 | -->
<!-- | **BypassNetwork** | 从多个数据来源中选出正确的操作数 | 快递分拣员——根据标签选择发货通道 | -->
<!-- | **RegCache** | 为整数寄存器提供快速读取通道 | 就近便利店——不用跑总仓库 | -->
| **Component** | **Responsibility** | **Analogy** |
| --- | --- | --- |
| **RFReadArbiter (x5)** | Arbitrate concurrent read requests from multiple execution units to the five physical register files (int/fp/vec/v0/vl) | Warehouse pickup-window coordinator |
| **WBCollideChecker (x5)** | Check writeback-port conflicts so multiple instructions do not contend for one port in the same cycle | Shipping-window queue checker |
| **BypassNetwork** | Select the correct operand from multiple data sources | Courier sorter that chooses a delivery route from the label |
| **RegCache** | Provide a fast read path for integer registers | A nearby convenience store instead of the central warehouse |

The source shows these components being instantiated in `DataPath.scala`:

<!--
```scala
// 5 类写回冲突检查器 / Five classes of writeback conflict checkers
private val intWbBusyArbiter = Module(new IntRFWBCollideChecker(backendParams))
private val fpWbBusyArbiter  = Module(new FpRFWBCollideChecker(backendParams))
private val vfWbBusyArbiter  = Module(new VfRFWBCollideChecker(backendParams))
private val v0WbBusyArbiter  = Module(new V0RFWBCollideChecker(backendParams))
private val vlWbBusyArbiter  = Module(new VlRFWBCollideChecker(backendParams))

// 5 类寄存器堆读仲裁器 / Five classes of register-file read arbiters
private val intRFReadArbiter = Module(new IntRFBankReadArbiter(backendParams))
private val fpRFReadArbiter  = Module(new FpRFReadArbiter(backendParams))
private val vfRFReadArbiter  = Module(new VfRFReadArbiter(backendParams))
private val v0RFReadArbiter  = Module(new V0RFReadArbiter(backendParams))
private val vlRFReadArbiter  = Module(new VlRFReadArbiter(backendParams))
```
-->
```scala
private val intWbBusyArbiter = Module(new IntRFWBCollideChecker(backendParams))
private val fpWbBusyArbiter  = Module(new FpRFWBCollideChecker(backendParams))
private val vfWbBusyArbiter  = Module(new VfRFWBCollideChecker(backendParams))
private val v0WbBusyArbiter  = Module(new V0RFWBCollideChecker(backendParams))
private val vlWbBusyArbiter  = Module(new VlRFWBCollideChecker(backendParams))

private val intRFReadArbiter = Module(new IntRFBankReadArbiter(backendParams))
private val fpRFReadArbiter  = Module(new FpRFReadArbiter(backendParams))
private val vfRFReadArbiter  = Module(new VfRFReadArbiter(backendParams))
private val v0RFReadArbiter  = Module(new V0RFReadArbiter(backendParams))
private val vlRFReadArbiter  = Module(new VlRFReadArbiter(backendParams))
```

`RFReadArbiter` filters source ports by data type and arbitrates only sources that need to read that type of register file:

<!--
```scala
// DataPath.scala — 整数RF读仲裁 / integer RF-read arbitration
intRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
  arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
    val srcIndices = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
    for (srcIdx <- 0 until numRegSrc) {
      if (srcIndices.contains(srcIdx)) {
        arbInSeq(srcIdx).valid := intRFBankRen(iqIdx)(exuIdx).get(srcIdx).asUInt.orR
        arbInSeq(srcIdx).bits.addr := fromIQDeqOg1Payload(iqIdx)(exuIdx).psrc(srcIdx)
        // ...
      } else {
        arbInSeq(srcIdx).valid := false.B  // 该源不需要读整数RF / this source does not need to read the integer RF
      }
    }
  }
}
```
-->
```scala
// DataPath.scala - integer register-file read arbitration.
intRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
  arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
    val srcIndices = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
    for (srcIdx <- 0 until numRegSrc) {
      if (srcIndices.contains(srcIdx)) {
        arbInSeq(srcIdx).valid := intRFBankRen(iqIdx)(exuIdx).get(srcIdx).asUInt.orR
        arbInSeq(srcIdx).bits.addr := fromIQDeqOg1Payload(iqIdx)(exuIdx).psrc(srcIdx)
        // ...
      } else {
        arbInSeq(srcIdx).valid := false.B  // This source does not read the integer RF.
      }
    }
  }
}
```

<!-- **协作流程**： -->
**Cooperation flow**:

![36d7a0f99d480735151f8ccdcdc5fbdb.svg](img/8-bypass-network/figure-001-8-bypass-network-2.svg)

***

<!-- ## 8.7 Bypass Network 的时序压力 -->
## 8.7 Bypass Network Timing Pressure

<!-- ### 8.7.1 关键时序路径 -->
### 8.7.1 Critical Timing Paths

<!-- | **路径** | **描述** | **严重程度** | -->
<!-- | --- | --- | --- | -->
<!-- | Forward 数据选择 | N 个执行单元的写回数据做 Mux1H | 🔴 极高 | -->
<!-- | pdest 匹配 | 物理寄存器编号比对（在 IQ 阶段完成，结果以 exuSources 传递） | 🟡 已优化 | -->
<!-- | 跨域旁路 | I2F/F2I/VF2VF 的多拍延迟 | 🟡 中等 | -->
<!-- | Imm 提取 | 立即数解码与符号扩展 | 🟢 较低 | -->
<!-- | RFReadArbiter 仲裁 | 多个执行单元竞争有限读端口 | 🟡 中等 | -->
| **Path** | **Description** | **Severity** |
| --- | --- | --- |
| Forward data selection | `Mux1H` over writeback data from N execution units | 🔴 Very high |
| `pdest` matching | Physical-register comparison (completed in IQ; result passed through `exuSources`) | 🟡 Optimized |
| Cross-domain bypass | Multi-cycle I2F/F2I/VF2VF latency | 🟡 Medium |
| Immediate extraction | Immediate decoding and sign extension | 🟢 Low |
| RFReadArbiter arbitration | Multiple execution units competing for limited read ports | 🟡 Medium |

<!-- ### 8.7.2 优化策略 -->
### 8.7.2 Optimization Strategies

<!-- **策略一：匹配前移到 Issue Queue** -->
**Strategy 1: Move matching into the Issue Queue**

The physical-register matching for Forward/Bypass ("which execution unit wrote my source operand?") is an O(N x M) comparison (N sources x M execution units). XiangShan **moves this logic into the Issue Queue wakeup stage** and passes the result to the Bypass Network as one-hot <code>**exuSources**</code>. The Bypass Network then performs only a simple <code>**Mux1H**</code> selection instead of comparisons.

<!--
```scala
// BypassNetwork.scala — exuSources 在 IQ 阶段计算好 / exuSources is computed in the IQ stage
private val forwardOrBypassValidVec3 = MixedVecInit(
  fromDPs.map { x =>
    VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.exuParams))).getOrElse(...))
  }
)
// BypassNetwork 只做 Mux1H，不做匹配 / BypassNetwork only performs Mux1H; it does not perform matching
Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec)
```
-->
```scala
// BypassNetwork.scala - exuSources is computed in the IQ stage.
private val forwardOrBypassValidVec3 = MixedVecInit(
  fromDPs.map { x =>
    VecInit(x.bits.exuSources.map(_.map(_.toExuOH(x.bits.exuParams))).getOrElse(...))
  }
)
Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec) // BypassNetwork selects; it does not match.
```

<!-- **策略二：RegCache 减少寄存器堆读压力** -->
**Strategy 2: Use RegCache to reduce register-file read pressure**

For integer operands, XiangShan introduces **RegCache**. Writeback data is also written to RegCache as it passes through the Bypass Network (bypass level, one-cycle latency), allowing later instructions to read it quickly without competing for the register file's limited read ports:

<!--
```scala
// BypassNetwork.scala — bypass 数据写回 RegCache / write bypass data back to RegCache
io.toDataPath.zipWithIndex.foreach{ case (x, i) =>
  x.wen  := bypassIntWenVec(i)     // RegCache write enable
  x.data := bypassRCDataVec(i)     // RegCache write data
  x.tag.foreach(_ := bypassTagVec(i))
}
```
-->
```scala
// BypassNetwork.scala - bypass data written back to RegCache.
io.toDataPath.zipWithIndex.foreach{ case (x, i) =>
  x.wen  := bypassIntWenVec(i)     // RegCache write enable
  x.data := bypassRCDataVec(i)     // RegCache write data
  x.tag.foreach(_ := bypassTagVec(i))
}
```

<!-- 这就像在仓库旁边开了一家便利店——常用货物就近取，不用每次都跑总仓。 -->
This is like opening a convenience store next to the warehouse: frequently used items are picked up nearby instead of requiring a trip to the central warehouse.

<!-- **策略三：Bypass 数据门控** -->
**Strategy 3: Gate bypass data**

Bypass data uses `RegEnable` (an enabled register), rather than a plain `RegNext`, and latches data only when writeback is valid. Cross-domain bypasses such as I2F/F2I use `RegNext` to preserve timing. This differentiated treatment avoids unnecessary register toggles and saves dynamic power:

```scala
// BypassNetwork.scala
if (x.bits.params.needDataFromI2F || x.bits.params.needDataFromF2I)
  ZeroExt(RegNext(x.bits.data), RegDataMaxWidth)          // cross-domain: unconditional register stage
else
  ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth) // same-domain: conditional register stage
```

***

<!-- ## 8.8 总结 -->
## 8.8 Summary

<!-- ## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font> -->
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> Key Takeaways</font>

<!-- * **为什么需要旁路**：不经过寄存器堆，让执行单元的结果直达下游，消除数据依赖延迟 -->
* **Why bypass is needed**: Send execution-unit results directly downstream without the register file, eliminating data-dependency latency.
<!-- * **八种数据来源**：zero / forward / bypass / bypass2 / imm / v0 / regcache / reg——根据数据何时就绪选择最合适的通道，每个源操作数在任意时刻只有一种来源有效（DataSource one-hot 编码） -->
* **Eight data sources**: `zero` / `forward` / `bypass` / `bypass2` / `imm` / `v0` / `regcache` / `reg`; select the best path based on data readiness, with exactly one valid source per operand (`DataSource` one-hot encoding).
<!-- * **三级旁路**：Forward（当拍）→ Bypass（1 拍）→ Bypass2（2 拍），对应不同的时序场景；跨域旁路（I2F、F2I 用 RegNext，VF→VF/Mem 用 Bypass2）需要额外延迟 -->
* **Three bypass levels**: Forward (same cycle) -> Bypass (one cycle) -> Bypass2 (two cycles), for different timing scenarios; cross-domain paths need the extra delay (I2F/F2I use `RegNext`, VF to VF/Mem uses Bypass2).
<!-- * **与 DataPath 协作**：5 类 RFReadArbiter 仲裁读端口、5 类 WBCollideChecker 检查写回冲突、RegCache 缓存整数数据减少读端口竞争 -->
* **Cooperation with DataPath**: five RFReadArbiters arbitrate read ports, five WBCollideCheckers detect writeback conflicts, and RegCache caches integer data to reduce read-port contention.
<!-- * **时序优化**：匹配前移到 IQ 阶段（以 exuSources 传递）、RegCache 减少读端口竞争、差异化门控（RegEnable vs RegNext）节省功耗 -->
* **Timing optimizations**: move matching into the IQ stage (pass it through `exuSources`), use RegCache to reduce read-port contention, and use differentiated gating (`RegEnable` vs. `RegNext`) to save power.

<!-- 核心原则：Bypass Network 的设计哲学是\*\*"让数据走最短的路径"**——数据刚产生就送走，能不经过寄存器堆就不经过。而时序优化的核心是**"把计算前移、把选择简化"\*\*——复杂的匹配逻辑交给 IQ，Bypass Network 只做轻量的 Mux 选择。 -->
Core principle: The Bypass Network should **take the shortest path for data**: send a value as soon as it is produced and avoid the register file whenever possible. Timing optimization follows the same principle of **moving computation earlier and simplifying selection**: IQ performs the complex matching, while the Bypass Network performs only a lightweight Mux selection.


<!-- > 更新: 2026-07-01 16:37:51
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/ufmf4kq3vmz15du4> -->
> Updated: 2026-07-01 16:37:51
> Source: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/ufmf4kq3vmz15du4>
