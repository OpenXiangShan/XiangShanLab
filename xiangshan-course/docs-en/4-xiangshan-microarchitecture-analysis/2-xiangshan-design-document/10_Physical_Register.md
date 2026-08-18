<!--
# 10. Physical Register

在前面的章节里，我们提到"物理寄存器堆"——执行单元要从这里读操作数，写回结果也要写到那里。但你有没有想过：**什么时候读？怎么读？写回端口不够怎么办？物理寄存器的一生是怎样的？**

📦读完本章，你将能够：

* ✅ 理解"发射前读取"与"发射后读取"两种策略的差异
* ✅ 掌握它们的优缺点和适用场景
* ✅ 认识写回端口竞争的成因与仲裁机制
* ✅ 了解物理寄存器从分配到释放的完整生命周期
* ✅ 理解物理寄存器分 Bank 设计的动机与实现

***

## 10.1 整体定位：物理寄存器堆是什么？

你可以把物理寄存器堆想象成一座公共仓库：

* 每个仓位对应一个物理寄存器（P-Register）
* 仓库有若干取货窗口（读端口）和入库窗口（写端口）
* 所有人都往这里存取货物，窗口数量有限，必须排队

在乱序处理器中，物理寄存器堆是数据存储的核心——重命名阶段为每条指令分配一个物理寄存器，执行结果写入该寄存器，后续指令从中读取操作数，直到 ROB 提交确认该值不再需要、旧寄存器被回收。

香山有 5 类物理寄存器堆，分别对应 5 种数据类型：

```scala
// DataPath.scala — 5 类寄存器堆的仲裁器
private val intRFReadArbiter = Module(new IntRFBankReadArbiter(backendParams))
private val fpRFReadArbiter  = Module(new FpRFReadArbiter(backendParams))
private val vfRFReadArbiter  = Module(new VfRFReadArbiter(backendParams))
private val v0RFReadArbiter  = Module(new V0RFReadArbiter(backendParams))
private val vlRFReadArbiter  = Module(new VlRFReadArbiter(backendParams))
```

| **寄存器堆类型** | **存储内容** | **位宽** | **Bank 数** |
| --- | --- | --- | --- |
| Int RF | 整数物理寄存器 | 64 bit | 多 Bank |
| FP RF | 浮点物理寄存器 | 64 bit | 1 |
| VF RF | 向量物理寄存器 | 128 bit | 1 |
| V0 RF | 向量 Mask 寄存器 | VLEN bit | 1 |
| VL RF | 向量长度寄存器 | 特殊 | 1 |

***

## 10.2 Post-Issue Register Read（发射后、执行前读取）

### 10.2.1 香山的流水级时序

```plain
OG0          OG1              OG2 (向量)      执行         写回
(Issue)  →  (Read RF +      → (向量专用)  →  (计算)   →  (写回RF)
             Bypass选择)
  ↑               ↑                             ↑
IQ选出指令    在这里读寄存器堆               使用操作数计算
              + BypassNetwork
              选择最终数据

```

关键区别：

* **Read Before Issue**：在 IQ 发射前就读好操作数，随指令一起送出
* **香山的 Post-Issue Read**：IQ 发射后，在 OG1 阶段通过 DataPath 读取 RF 并选择 Bypass 数据

### 10.2.2 OG1 阶段的具体实现

OG1 阶段是 DataPath 的核心——它同时完成 RF 读取和 Bypass 选择：

```scala
// DataPath.scala — OG1 阶段：RFReadArbiter 仲裁读请求
intRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
  arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
    val srcIndices = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
    for (srcIdx <- 0 until numRegSrc) {
      if (srcIndices.contains(srcIdx)) {
        // 只对需要读整数RF的源发起仲裁
        arbInSeq(srcIdx).valid := intRFBankRen(iqIdx)(exuIdx).get(srcIdx).asUInt.orR
        arbInSeq(srcIdx).bits.addr := fromIQDeqOg1Payload(iqIdx)(exuIdx).psrc(srcIdx)
        arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
        arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
      } else {
        arbInSeq(srcIdx).valid := false.B  // 该源不需要读此类型RF
      }
    }
  }
}
```

RF 读取结果与 Bypass 数据在 BypassNetwork 中做最终选择：

```scala
// BypassNetwork.scala — OG1 阶段：数据来源选择
val originSrc = Mux1H(
  Seq(
    readForward  -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec),
    readBypass   -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), bypassDataVec),
    readBypass2  -> ...,
    readZero     -> 0.U,
    readV0       -> ...,
    readRegOH    -> fromDPs(exuIdx).bits.src(srcIdx),    // ← 来自RF读取
    readRegCache -> fromDPsRCData(exuIdx)(srcIdx),       // ← 来自RegCache
    readImm      -> ...,
  )
)
```

***

## 10.3 向量执行单元的 Read After Issue

### 10.3.1 什么是"发射后读取"？

对于向量执行单元，操作数位宽极大（128~256 bit），读端口非常昂贵。向量执行单元采用更激进的 **Read After Issue** 策略——指令先发射到执行单元，执行单元自己负责按需读取操作数。

这就像你空手出门，到了目的地再取行李——路上更轻便，但到了之后还得跑一趟仓库。

```plain
向量 IQ 发射 → OG1 (仅读控制信息) → OG2 → 向量执行单元自己读向量RF → 计算
                                                  ↑
                                           在这里按需读RF

```

向量执行单元在 OG1 阶段不读完整的向量操作数，只传递地址等控制信息，执行单元内部再用专用端口读取。

***

## 10.4 寄存器堆的实现

### 10.4.1 基本 RegFile

```scala
// Regfile.scala
class Regfile(
  name: String, numPregs: Int,
  numReadPorts: Int, numWritePorts: Int,
  hasZero: Boolean, len: Int, width: Int,
  bankNum: Int = 1, isVlRegfile: Boolean = false,
) extends Module {
  val io = IO(new Bundle() {
    val readPorts  = Vec(numReadPorts, new RfReadPort(len, width))
    val writePorts = Vec(numWritePorts, new RfWritePort(len, width))
  })

  val mem = Reg(Vec(numPregs, UInt(len.W)))
  val memForRead = Wire(Vec(numPregs, UInt(len.W)))
  memForRead.zipWithIndex.map { case (m, i) =>
    if (i == 0) m := mem_0    // 寄存器0特殊处理
    else m := mem(i)
  }

  // 读取：地址先打一拍（RegNext），下一拍出数据
  for (r <- io.readPorts) {
    if (bankNum == 1) {
      r.data := memForRead(RegNext(r.addr))   // ← 读延迟 1 拍
    } else {
      // 多 Bank 读取逻辑...
    }
  }

  // 写入：同一周期内多个写端口可能写同一个地址，断言检查
  for (i <- 0 until writePorts.size - 1) {
    val hasSameWrite = writePorts.drop(i + 1)
    .map(w => w.wen && w.addr === writePorts(i).addr && writePorts(i).wen)
    .reduce(_ || _)
    assert(!hasSameWrite, "RegFile two or more writePorts write same addr")
  }

  // 写入逻辑：one-hot 选择写入数据
  for (i <- mem.indices) {
    if (hasZero && i == 0) {
      mem_0 := 0.U    // 寄存器0恒为零
    } else {
      val wenOH = VecInit(io.writePorts.map(w => w.wen && w.addr === i.U))
      val wData = Mux1H(wenOH, io.writePorts.map(_.data))
      when(wenOH.asUInt.orR) { mem(i) := wData }
    }
  }
}
```

关键设计点：

* **读延迟 1 拍**：<code>**r.data := memForRead(RegNext(r.addr))**</code>——地址先寄存一拍，下一拍才出数据。这就是 OG1 需要 1 个时钟周期的物理原因
* **寄存器 0 恒为零**：<code>**hasZero=true**</code> 时，P0 始终为 0（RISC-V x0 寄存器语义）
* **写冲突断言**：硬件断言确保两个写端口不会同时写同一个地址

### 10.4.2 Banked RegFile（分 Bank 寄存器堆）

整数寄存器堆采用多 Bank 设计，降低每个 Bank 的读端口数量：

```scala
// Regfile.scala
class RegfileBank(
  name: String, numPregs: Int, numBank: Int,
  numReadPorts: Int, numWritePorts: Int,
  hasZero: Boolean, len: Int, width: Int,
  isVlRegfile: Boolean = false,
) extends Module {
  val io = IO(new Bundle() {
    val readPorts  = Vec(numReadPorts, new RfReadPortBank(len, width, numBank))
    val writePorts = Vec(numWritePorts, new RfWritePort(len, width))
  })

  val bankRaddrWidth = log2Ceil(numBank)
  val bankEntryNum = 1 << (width - bankRaddrWidth)

  // 分 Bank 读取：每个 Bank 独立寻址
  for (r <- io.readPorts) {
    for (i <- 0 until numBank) {
      val startIdx = bankEntryNum * i
      val endIdx = math.min(bankEntryNum * (i + 1), numPregs)
      val thisBank = VecInit(memForRead.slice(startIdx, endIdx))
      r.data(i) := thisBank(RegNext(r.addr(i)))  // 每个 Bank 独立读
    }
  }
  // ...
}
```

Bank 设计的动机：

* 整数 RF 读端口需求量大（多个 ALU 同时需要读操作数）
* 将 RF 分成多个 Bank，每个 Bank 的端口数减少，面积和功耗显著降低
* DataPath 中的 <code>**IntRFBankReadArbiter**</code> 负责将读请求路由到正确的 Bank

| **维度** | **Read Before Issue** | **Read After Issue** |
| --- | --- | --- |
| **时序** | 读RF在发射路径上，增加关键路径延迟 | 发射路径更短，读RF延迟隐藏在执行单元内部 |
| **端口数量** | 每个发射口 × 每个源操作数 = 大量读端口 | 读端口在执行单元内部复用，数量可控 |
| **面积** | 读端口多 → 寄存器堆面积大 | 读端口少 → 寄存器堆面积小 |
| **旁路复杂度** | 旁路网络需要与RF读取结果做选择（RF vs Forward/Bypass） | 执行单元自己决定从哪里取数据，旁路逻辑更集中 |
| **灵活性** | 所有操作数必须提前确定来源 | 执行单元可以根据运行时情况决定是否真的需要读RF |
| **适用场景** | 标量执行单元（操作数少、位宽小） | 向量执行单元（操作数多、位宽大） |

***

## 10.5 Physical Register Write Back Port Contention（写回端口竞争）

### 10.5.1 问题：端口不够用

物理寄存器堆的**写端口数量是有限的**——每个写端口对应一条写回通路，面积和功耗代价极高。但后端可能有十几个执行单元同时写回，怎么办？

你不可能给每个执行单元都配一个独享写端口——那就像给工厂里每台机器都修一条专用公路，太奢侈了。

### 10.5.2 解决方案：写回冲突检测 + 仲裁

香山采用**写回端口共享 + 冲突检测 + 仲裁**的策略：多个执行单元共享写回端口，通过冲突检测器判断同一端口上是否有竞争，再通过仲裁器决定谁先写。

在 DataPath 中，每种数据类型都有独立的 <code>**RFWBCollideChecker**</code>（写回冲突检测器）：

```scala
// DataPath.scala
private val intWbBusyArbiter = Module(new IntRFWBCollideChecker(backendParams))
private val fpWbBusyArbiter  = Module(new FpRFWBCollideChecker(backendParams))
private val vfWbBusyArbiter  = Module(new VfRFWBCollideChecker(backendParams))
private val v0WbBusyArbiter  = Module(new V0RFWBCollideChecker(backendParams))
private val vlWbBusyArbiter  = Module(new VlRFWBCollideChecker(backendParams))
```

| **仲裁器** | **覆盖类型** | **职责** |
| --- | --- | --- |
| IntRFWBCollideChecker | 整数 RF | 检查整数写回端口冲突 |
| FpRFWBCollideChecker | 浮点 RF | 检查浮点写回端口冲突 |
| VfRFWBCollideChecker | 向量 RF | 检查向量写回端口冲突 |
| V0RFWBCollideChecker | V0 RF | 检查 V0 写回端口冲突 |
| VlRFWBCollideChecker | VL RF | 检查 VL 写回端口冲突 |

### 10.5.3 竞争发生时：发射阻塞

冲突检测器的核心逻辑是：**按写回端口号分组，同一端口上的多个写回请求通过仲裁器竞争**。

```scala
// RFWBCollideChecker.scala
abstract class RFWBCollideCheckerBase(params: RFWBCollideCheckerParams) extends Module {
  // 将输入按端口号分组，同组内按优先级排序
  protected val inGroup = io.in.flatten
  .groupBy(_.bits.wbCfg.get.port)
  .map(x => (x._1, x._2.sortBy(_.bits.wbCfg.get.priority)))

  // 每个端口一个仲裁器
  protected val arbiters = portRange.map { portIdx =>
    OptionWrapper(
      inGroup.isDefinedAt(portIdx),
      Module(new WBArbiter(..., inGroup(portIdx).size))
    )
  }
}
```

仲裁器内部有一个**防饿死机制**——使用饱和计数器记录每个输入端口连续失败的次数：

```scala
// RFWBCollideChecker.scala
class WBArbiter[T <: Data](val gen: T, val n: Int) extends Module {
  // 饱和计数器：记录连续失败的次数
  val cancelCounter = RegInit(VecInit(Seq.fill(n)(0.U(CounterWidth.W))))
  val isFull = RegInit(VecInit(Seq.fill(n)(false.B)))

  // 当连续失败次数达到阈值，标记为"满"
  // 被标记为"满"的端口下次请求时会被优先处理
  cancelCounterNext.zip(isFullNext).zip(cancelCounter).zip(isFull).zipWithIndex.foreach {
    case ((((cntNext, fullNext), cnt), full), i) =>
      when (io.in(i).valid && !io.in(i).ready) {
        cntNext := Mux(cnt === CounterThreshold.U, CounterThreshold.U, cnt + 2.U)
      }.elsewhen (io.in(i).valid && io.in(i).ready) {
        cntNext := Mux(cnt === 0.U, 0.U, cnt - 1.U)
      }
      fullNext := (cancelCounter(i) === CounterThreshold.U)
  }

  // 有"满"端口时，优先调度它们
  finalValid := io.in.zipWithIndex.map { case (in, i) =>
    in.valid && (!hasFull || !hasFullReq || isFull(i))
  }
}
```

### 10.5.4 竞争发生时：发射阻塞

当两个执行单元需要在同一周期写回同一个写回端口时，仲裁器会判定其中一个失败，对应的 Issue Queue 就会被阻塞——该指令不能发射，直到端口空闲。

```plain
Exu A 写回 → 端口 0 → ✅ 仲裁获胜，写入成功
Exu B 写回 → 端口 0 → ❌ 仲裁失败，阻塞 Exu B 的发射
Exu C 写回 → 端口 1 → ✅ 不同端口，无冲突
```

在 DataPath 中，写回冲突检测结果与读端口仲裁结果共同决定指令能否发射：

```scala
// DataPath.scala — 写回不阻塞信号
private val intWbNotBlock = intWbBusyArbiter.io.in.map(x => MixedVecInit(x.map(_.ready).toSeq)).toSeq
private val fpWbNotBlock  = fpWbBusyArbiter.io.in.map(x => MixedVecInit(x.map(_.ready).toSeq)).toSeq
// ...

// DataPath.scala — 综合判断：读端口 + 写回端口都不阻塞才允许发射
private val rdSrcsNotBlock = Wire(MixedVec(...))  // 每个源操作数是否不阻塞
private val rdNotBlock = Wire(MixedVec(...))       // 所有源操作数都不阻塞
```

:::warning
💡新手建议\
写回端口竞争是影响处理器吞吐量的重要因素之一。当你看到 <code>**stall_cycle_wb**</code> 这类性能计数器居高不下时，往往就是写回端口成了瓶颈。解决方案通常是增加写回端口或调整执行单元到端口的映射关系。

:::

***

## 10.6 Physical Register Lifecycle（物理寄存器生命周期）

一个物理寄存器从"出生"到"死亡"，要经历以下阶段：

```plain
① 分配         ② 写入          ③ 读取         ④ 提交确认      ⑤ 释放
(Rename阶段)   (写回阶段)      (后续指令读取)   (ROB提交)      (FreeList回收)
     │              │               │               │              │
     ▼              ▼               ▼               ▼              ▼
  从FreeList    执行单元结果    后续指令从RF     确认结果正确    旧物理寄存器
  取出空闲PRF   写入物理寄存器  或旁路获取       无需回滚        回收到FreeList
```

### 阶段详解

| **阶段** | **时机** | **发生了什么** | **比喻** |
| --- | --- | --- | --- |
| ① 分配 | Rename | 从 FreeList 中取出一个空闲的物理寄存器编号，分配给当前指令作为目标 | 领了一个空柜子 |
| ② 写入 | Writeback | 执行单元将计算结果写入该物理寄存器 | 往柜子里放了东西 |
| ③ 读取 | 后续指令发射 | 依赖该结果的后续指令从 RF 或旁路获取数据 | 别人来柜子取东西 |
| ④ 提交确认 | ROB Commit | ROB 确认该指令不是误推测，结果有效 | 确认东西没放错 |
| ⑤ 释放 | ROB Commit 后 | 该指令覆盖的旧物理寄存器被回收进 FreeList | |

:::danger
💫\_**易混淆点：释放的是旧物理寄存器，不是当前指令的目标寄存器。比如指令 ***<code>_**ADD r3, r1, r2**_</code>***，Rename 时把 r3 映射到新的 P30，旧的映射可能是 P15。提交后释放的是 P15，P30 仍然活着。**\_

:::

### 例外：Move Elimination

香山支持 **Move 指令消除**——对于 <code>**MV rd, rs**</code> 这种只是搬运数据的指令，不需要真正执行，直接在 Rename 阶段把 <code>**rd**</code> 重命名到 <code>**rs**</code> 的物理寄存器即可。此时不需要分配新的物理寄存器，也不需要写回，节省了执行资源。

***

## 10.7 Physical Register Bank（物理寄存器分体）

### 10.7.1 为什么需要分 Bank？

当物理寄存器堆的**读端口数量很多**时（比如整数 RF 可能有 20+ 个读端口），寄存器堆的面积和延迟会急剧增长——因为每个读端口都需要一套完整的地址解码和数据输出电路，端口数与面积近似呈 O(N²) 关系。

你可以把这想象成一家**超宽的仓库**——如果只有一个大门，所有人排队进出，效率极低。但如果把仓库分成几个**独立的隔间**，每个隔间有自己的门，就可以并行存取。

### 10.7.2 分 Bank 策略

香山支持将物理寄存器堆分为 **1/2/4 个 Bank**，按物理寄存器编号的低位交错分配：

```scala
Bank 0: PRF[0], PRF[2], PRF[4], PRF[6], ...
Bank 1: PRF[1], PRF[3], PRF[5], PRF[7], ...
```

```scala
// Regfile.scala — RegfileBank 实现
class RegfileBank(
  name: String, numPregs: Int, numBank: Int,
  numReadPorts: Int, numWritePorts: Int,
  hasZero: Boolean, len: Int, width: Int,
  isVlRegfile: Boolean = false,
) extends Module {
  val io = IO(new Bundle() {
    val readPorts  = Vec(numReadPorts, new RfReadPortBank(len, width, numBank))
    val writePorts = Vec(numWritePorts, new RfWritePort(len, width))
  })

  val bankRaddrWidth = log2Ceil(numBank)
  val bankEntryNum = 1 << (width - bankRaddrWidth)

  // 分 Bank 读取：每个 Bank 独立寻址、独立读出
  for (r <- io.readPorts) {
    for (i <- 0 until numBank) {
      val startIdx = bankEntryNum * i
      val endIdx = math.min(bankEntryNum * (i + 1), numPregs)
      val thisBank = VecInit(memForRead.slice(startIdx, endIdx))
      r.data(i) := thisBank(RegNext(r.addr(i)))  // 每个 Bank 独立读，地址先打一拍
    }
  }
}
```

同时，非 Bank 模式的 Regfile 也支持 Bank 读取逻辑：

```scala
// Regfile.scala — Regfile 的 Bank 读取分支
for (r <- io.readPorts) {
  if (bankNum == 1) {
    r.data := memForRead(RegNext(r.addr))   // 单 Bank：直接读
  } else {
    // 多 Bank：按低位判断 Bank，从目标 Bank 读出后 Mux 选择
    val banks = (0 until bankNum).map { case i =>
      memForRead.zipWithIndex.filter { case (m, index) => (index % bankNum) == i }.map(_._1)
    }
    val hitBankWire = VecInit((0 until bankNum).map { case i =>
      r.addr(bankWidth - 1, 0) === i.U     // 地址低位选 Bank
    })
    val hitBankReg = Reg(Vec(bankNum, Bool()))
    hitBankReg := hitBankWire
    val banksRdata = Wire(Vec(bankNum, UInt(len.W)))
    for (i <- 0 until numBank) {
      banksRdata(i) := RegEnable(VecInit(banks(i))(r.addr(r.addr.getWidth - 1, bankWidth)), hitBankWire(i))
    }
    r.data := Mux1H(hitBankReg, banksRdata)  // Mux 选择目标 Bank 数据
  }
}
```

### 10.7.3 分 Bank 后的读取

分 Bank 后，读取操作变为两步：

1. **确定 Bank**：根据地址低位判断要读哪个 Bank
2. **从对应 Bank 读出数据**：只在目标 Bank 激活读电路

DataPath 中的 <code>**IntRFBankReadArbiter**</code> 负责将读请求路由到正确的 Bank：

```scala
// DataPath.scala
private val intPregNumBank = coreParams.intPreg.numBank
private val intRFReadArbiter = Module(new IntRFBankReadArbiter(backendParams))

// 整数 RF 分 Bank 读取
val intRfRaddr = Wire(Vec(params.numPregRd(IntData()),
  Vec(intPregNumBank, UInt(intArbiterAddrWidth.W))))
IntRegFileBank("IntRegFile", intSchdParams.numPregs, intPregNumBank, ...)
for (portIdx <- intRfRaddr.indices) {
  intRfRaddr(portIdx) := VecInit(intRFReadArbiter.io.out(portIdx).map(_.bits.addr))
}
```

### 10.7.4 分 Bank 的代价

| **优势** | **代价** |
| --- | --- |
| 单 Bank 端口数减少 → 面积减小 | Bank 间选择逻辑增加 → Mux 开销 |
| 读延迟可能降低（扇出减小） | 地址解码多一级 → 多 1 拍延迟 |
| 功耗降低（只激活目标 Bank） | 写入时需要路由到正确 Bank |

:::warning
💡核心思想\
***分 Bank 是经典的**"分而治之"**策略——用少量的路由开销换取显著的面积和功耗收益。在香山中，仅整数 RF 使用了分 Bank 设计以应对大量读端口的需求，浮点和向量 RF 仍为单 Bank（***<code>_**bankNum=1**_</code>***）。***

:::

***

## 10.8 整数寄存器堆的特殊设计：RegCache

在 Read Before Issue 策略下，发射指令时必须读 RF。但 RF 读端口有限，而且**旁路命中时根本不需要读 RF**——数据已经从执行单元直接前递了。那能不能进一步减少 RF 的读压力？

香山引入了 **RegCache（寄存器缓存）**——在整数 RF 旁边放置一个小容量的高速缓存，保存最近写回的数据。当操作数可以从 RegCache 获取时，就不需要竞争 RF 读端口。

### 10.8.1 RegCache 的结构

```scala
// RegCache.scala — RegCache 模块
class RegCache(implicit p: Parameters) extends Module {
  val io = IO(new RegCacheIO())
  // Tag 表：记录哪些物理寄存器被缓存
  // Data 表：缓存最近写回的数据
}
```

RegCache 的数据来源是 BypassNetwork 中的 bypass 级数据（1 拍延迟），而不是直接来自 RF：

```scala
// BypassNetwork.scala — bypass 数据写回 RegCache
io.toDataPath.zipWithIndex.foreach { case (x, i) =>
  x.wen  := bypassIntWenVec(i)     // RegCache 写使能（仅整数）
  x.data := bypassRCDataVec(i)     // RegCache 写数据（bypass 级）
  x.tag.foreach(_ := bypassTagVec(i))
}
```

BypassNetwork 中的数据来源选择将 RegCache 作为一个独立通道：

```scala
// BypassNetwork.scala — 数据来源 one-hot 选择
val originSrc = Mux1H(Seq(
  readForward  -> ...,       // Forward 通路
  readBypass   -> ...,       // Bypass 通路
  readBypass2  -> ...,       // Bypass2 通路
  readZero     -> 0.U,       // 零
  readV0       -> ...,       // V0
  readRegOH    -> rfData,    // RF 读取
  readRegCache -> regCacheData,  // ← RegCache 读取
  readImm      -> ...,       // 立即数
))
```

### 10.8.2 RegCache vs RF

| **特性** | **RF** | **RegCache** |
| --- | --- | --- |
| 容量 | 全部物理寄存器 | 仅最近写回的子集 |
| 读端口 | 多但竞争激烈 | 少但快速 |
| 命中率 | 100%（总有数据） | 依赖时间局部性 |
| 数据来源 | 执行单元写回 | BypassNetwork bypass 级 |
| 用途 | 主存储 | 辅助加速整数读取 |

这就像在总仓库旁边开了一家便利店——常用货物就近取，不用每次都跑总仓。当便利店没有时，再去总仓也不迟。

***

## 10.9 总结

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **Post-Issue Register Read**：香山采用发射后、执行前读取策略——OG0 发射、OG1 读 RF + Bypass 选择。标量用 DataPath 集中读取，向量用 Read After Issue 由执行单元自己读
* **写回端口竞争**：多执行单元共享写回端口，通过 <code>**RFWBCollideChecker**</code> 冲突检测 + 仲裁；有防饿死饱和计数器机制；冲突时阻塞对应 Issue Queue 的发射
* **物理寄存器生命周期**：分配→写入→读取→提交确认→释放旧寄存器；Move Elimination 可跳过写入和执行
* **物理寄存器分 Bank**：仅整数 RF 使用，按低位交错分配，减少单 Bank 端口数，降低面积和功耗，代价是多一级选择延迟
* **RegCache**：仅整数 RF 的辅助加速机制，缓存 Bypass 级写回数据，减少 RF 读端口竞争

核心原则：物理寄存器堆的设计是\*\*"存储密度 vs 访问带宽"\*\*的经典权衡——读端口越多带宽越大，但面积和延迟也随之增长。分 Bank 和 RegCache 都是在这个权衡中寻找更优解的工程手段。


> 更新: 2026-07-01 18:03:07
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/zzuaz1ou8z2egm9i>
-->

# 10. Physical Register

The physical register file (PRF) is the backend's shared warehouse. Each slot is a physical register, read ports are pickup windows, and write ports are receiving windows. Because many execution units share a finite number of ports, accesses must be scheduled and arbitrated.

:::info
**After this chapter, you will be able to:**

* Distinguish Read-Before-Issue from XiangShan's post-issue register-read strategy.
* Understand their trade-offs and use cases.
* Explain why writeback ports contend and how the arbiter resolves conflicts.
* Trace a physical register from allocation through release.
* Understand why the integer PRF is banked.

:::

***

## 10.1 Overall Position: What Is the Physical Register File?

Rename allocates physical destinations from a FreeList. The main register-file domains are:

| **Register file** | **Contents** | **Width** | **Banks** |
| --- | --- | --- | --- |
| Int RF | Integer physical registers | 64 bit | Multiple banks |
| FP RF | Floating-point physical registers | 64 bit | One |
| VF RF | Vector physical registers | 128 bit | One |
| V0 RF | Vector mask registers | VLEN bits | One |
| VL RF | Vector-length state | Special | One |

***

## 10.2 Post-Issue Register Read (Before Execution)

### 10.2.1 XiangShan Pipeline Timing

XiangShan's timing is:

```plain
OG0: Issue Queue selects and issues a uop
  ↓
OG1: DataPath reads the RF and selects Forward/Bypass data
  ↓
OG2: Additional stage for vector execution only
  ↓
Execution unit computes and writes back
```

Read-Before-Issue reads operands before the issue queue emits the uop. XiangShan instead issues first and reads in OG1, allowing the issue path to remain short and allowing the data path to choose the appropriate operand source at runtime.

### 10.2.2 OG1 Implementation

`DataPath` uses `IntRFBankReadArbiter` to issue an RF-read request only for a source operand that requires the integer RF. The request carries its physical source-register address, ROB index, and issue-valid state:

```scala
// DataPath.scala: OG1 IntRFBankReadArbiter request arbitration.
intRFReadArbiter.io.in.zipWithIndex.foreach { case (arbInSeq2, iqIdx) =>
  arbInSeq2.zipWithIndex.foreach { case (arbInSeq, exuIdx) =>
    val srcIndices = fromIQ(iqIdx)(exuIdx).bits.exuParams.getRfReadSrcIdx(IntData())
    for (srcIdx <- 0 until numRegSrc) {
      if (srcIndices.contains(srcIdx)) {
        // Arbitrate only operands that need the integer RF.
        arbInSeq(srcIdx).valid := intRFBankRen(iqIdx)(exuIdx).get(srcIdx).asUInt.orR
        arbInSeq(srcIdx).bits.addr := fromIQDeqOg1Payload(iqIdx)(exuIdx).psrc(srcIdx)
        arbInSeq(srcIdx).bits.robIdx := fromIQ(iqIdx)(exuIdx).bits.robIdx
        arbInSeq(srcIdx).bits.issueValid := fromIQ(iqIdx)(exuIdx).valid
      } else {
        arbInSeq(srcIdx).valid := false.B // This source does not need this RF type.
      }
    }
  }
}
```

`BypassNetwork` then performs a one-hot selection across all eight data sources, rather than choosing only between RF and one bypass path:

```scala
// BypassNetwork.scala: OG1 operand-source selection.
val originSrc = Mux1H(
  Seq(
    readForward  -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), forwardDataVec),
    readBypass   -> Mux1H(forwardOrBypassValidVec3(exuIdx)(srcIdx), bypassDataVec),
    readBypass2  -> bypass2Data,
    readZero     -> 0.U,
    readV0       -> v0Data,
    readRegOH    -> fromDPs(exuIdx).bits.src(srcIdx),  // RF-read data
    readRegCache -> fromDPsRCData(exuIdx)(srcIdx),     // RegCache data
    readImm      -> immData,
  )
)
```

For each source operand, wakeup selects exactly one `DataSource`; an execution unit enables only the alternatives it supports.

| **`DataSource`** | **Selected value** |
| --- | --- |
| `reg` | The normal physical-register-file read. |
| `regcache` | A cached integer value from RegCache. |
| `v0` | The V0 mask-register operand. |
| `zero` | Constant zero (`x0`/P0). |
| `forward` | Same-cycle forwarding through the zero-cycle Forward path. |
| `bypass` | The one-cycle Bypass path. |
| `bypass2` | The two-cycle Bypass2 path used by applicable vector consumers. |
| `imm` | The immediate value formed for the uop. |

The selection controls are one-hot: `BypassNetwork` selects one value and never combines values from multiple source paths.

The result is a one-cycle issue-to-Exu input path for scalar units. Vector units add OG2 because their operands are wider and their execution front end is separate.

## 10.3 Read After Issue in Vector Units

### 10.3.1 What Does "Read After Issue" Mean?

"Read after issue" means that the vector execution unit itself reads vector operands after the issue decision. This avoids putting many wide vector read ports on the issue path and lets the vector unit decide whether an operand comes from its RF or a bypass source.

***

## 10.4 Register-File Implementation

### 10.4.1 Basic `Regfile`

The basic register file has registered read addresses and synchronous write ports:

```scala
// Address is registered; data is read on the following cycle.
r.data := memForRead(RegNext(r.addr))

// Optional RISC-V zero register.
if (hasZero) { mem_0 := 0.U }

// Assert that no pair of write ports targets the same address in one cycle.
for (i <- 0 until writePorts.size - 1) {
  val hasSameWrite = writePorts.drop(i + 1)
    .map(w => w.wen && w.addr === writePorts(i).addr && writePorts(i).wen)
    .reduce(_ || _)
  assert(!hasSameWrite, "RegFile two or more writePorts write same addr")
}
```

The one-cycle read latency explains the OG1 stage. When `hasZero=true`, P0 is always zero, matching the RISC-V x0 semantics. Write-conflict assertions protect the physical implementation.

### 10.4.2 Banked Register File

The integer RF has high read-port demand because several ALUs may read operands simultaneously. Splitting it into banks reduces ports, area, and power. `IntRFBankReadArbiter` routes each request to the proper bank.

| **Dimension** | **Read Before Issue** | **Read After Issue** |
| --- | --- | --- |
| Timing | RF read lies on the issue path | RF latency is hidden inside execution |
| Ports | Issue width x source operands | Reused inside execution units |
| Area | Many read ports, larger RF | Fewer ports, smaller RF |
| Bypass | Issue path selects RF vs bypass | Execution/data path centralizes selection |
| Flexibility | Operand source fixed early | Runtime can decide whether RF access is needed |
| Best fit | Scalar units with few narrow operands | Vector units with many wide operands |

***

## 10.5 Physical Register Writeback-Port Contention

### 10.5.1 The Problem

Many FUs can finish in the same cycle, but each register-file domain exposes only a limited number of write ports. Two results targeting one port cannot both write immediately.

### 10.5.2 Collision Detection and Arbitration

XiangShan uses per-domain collision checkers:

| **Checker** | **Domain** | **Responsibility** |
| --- | --- | --- |
| `IntRFWBCollideChecker` | Integer RF | Detect integer writeback conflicts |
| `FpRFWBCollideChecker` | FP RF | Detect floating-point conflicts |
| `VfRFWBCollideChecker` | Vector RF | Detect vector conflicts |
| `V0RFWBCollideChecker` | V0 RF | Detect mask-register conflicts |
| `VlRFWBCollideChecker` | VL RF | Detect vector-length conflicts |

The selected writeback is granted, while losing producers remain valid and retry. Saturating fairness counters prevent one producer from starving:

```scala
when (collide) {
  blockCounter := Mux(blockCounter === max.U, blockCounter, blockCounter + 1.U)
}.otherwise {
  blockCounter := 0.U
}
```

### 10.5.3 Issuance Blocking on Contention

When a collision persists, the corresponding Issue Queue is blocked rather than dropping the result:

```scala
issueQueue.io.out(i).ready := !writebackBlock(i) && downstreamReady(i)
```

:::warning
Writeback contention is a structural hazard, not a data dependency. It can delay an otherwise ready instruction and propagate back to issue, but it must never lose a result.

:::

## 10.6 Physical-Register Lifecycle

```plain
Rename: allocate a new physical destination from FreeList
   ↓
Execute/Writeback: write the result into that physical register
   ↓
Younger issue: read the RF or bypass result
   ↓
ROB commit: confirm the instruction was not speculative
   ↓
FreeList: reclaim the old physical register overwritten by Rename
```

| **Stage** | **When** | **Action** |
| --- | --- | --- |
| Allocate | Rename | Take a free physical-register number for the destination |
| Write | Writeback | Write the execution result |
| Read | Younger issue | Obtain the value from RF or bypass |
| Confirm | ROB commit | Make the mapping architectural |
| Release | After commit | Return the overwritten old register to FreeList |

### Stage Details

The lifecycle table above identifies the ownership transition at each stage: Rename owns allocation, writeback owns result creation, dependent issue consumes the value, and commit makes the mapping architectural before release.

### Exception: Move Elimination

Move Elimination is an exception: a move can reuse an existing physical mapping, so it may skip a new write and execution-unit operation.

***

## 10.7 Physical-Register Banks

### 10.7.1 Why Bank the PRF?

Only the integer RF is heavily banked. Interleaving entries by low address bits reduces the number of ports and the capacitance seen by each bank, lowering area and power.

### 10.7.2 Bank Strategy

```scala
val bankNum = 4
val bankId = pregIdx(log2Up(bankNum) - 1, 0)
val rowId = pregIdx >> log2Up(bankNum)
```

The bank ID selects the physical bank, while the remaining bits select a row. Read requests are routed by `IntRFBankReadArbiter`, and write data is routed to the bank selected by the destination index.

### 10.7.3 Reading a Banked RF

`IntRFBankReadArbiter` is the banked form of RF-read arbitration, not merely an address router. It groups requests by configured RF read port and instantiates an `OldestArbiter` for every `(read port, bank)` pair. An input carries its `bankValidVec`, physical-source address, `robIdx`, and `issueValid`; its `ready` is the conjunction of the bank-local arbiter ready signals. The bank-local outputs provide the addresses consumed by `IntRegFileBank`, and ROB age resolves same-port, same-bank conflicts.

```scala
// Send each bank-local winning address to the banked integer RF.
for (portIdx <- intRfRaddr.indices) {
  if (intRFReadArbiter.io.out.isDefinedAt(portIdx)) {
    intRfRaddr(portIdx) :=
      VecInit(intRFReadArbiter.io.out(portIdx).map(_.bits.addr))
  } else {
    intRfRaddr(portIdx) := 0.U
  }
}
```

A bypass hit can bypass the bank read entirely.

### 10.7.4 Costs and Benefits

| **Benefit** | **Cost** |
| --- | --- |
| Fewer ports per bank and smaller area | Extra bank-selection muxing |
| Lower fanout may reduce read delay | An additional address-decode stage may add a cycle |
| Only the selected bank is activated | Writes must be routed to the correct bank |

***

## 10.8 Special Integer-RF Design: RegCache

RegCache is a small auxiliary cache beside the integer RF. It stores recently written-back integer values to reduce RF read-port pressure. It is not the physical RF itself.

### 10.8.1 Structure

```scala
// Tag table: physical-register tag -> RegCache index
// Data array: RegCache index -> recently written value
// Valid/cancel state: whether the tag is still usable
```

### 10.8.2 RF versus RegCache

| **Property** | **RF** | **RegCache** |
| --- | --- | --- |
| Capacity | All physical registers | Recent subset |
| Read ports | Many but heavily contended | Fewer and fast |
| Hit rate | Always contains architectural data | Depends on temporal locality |
| Data source | Execution-unit writeback | BypassNetwork bypass stage |
| Purpose | Primary storage | Auxiliary integer-read acceleration |

***

## 10.9 Summary

* **Post-issue register read**: XiangShan issues in OG0 and reads RF/selects bypass data in OG1. Scalar units use centralized DataPath reads; vector units perform Read After Issue.
* **Writeback contention**: Shared write ports are protected by domain-specific `RFWBCollideChecker` logic, fair saturation counters, and Issue Queue backpressure.
* **Lifecycle**: A physical register is allocated, written, read, confirmed at ROB commit, and the old mapping is released. Move Elimination can skip write and execution.
* **Banking**: The integer RF is interleaved by low bits to reduce per-bank ports, area, and power, at the cost of selection/decode latency.
* **RegCache**: It caches bypass-level integer results and reduces integer RF read-port pressure.

> Updated: 2026-07-01 18:03:07
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/zzuaz1ou8z2egm9i>
