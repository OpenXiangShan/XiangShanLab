# 9. Execute Unit

在前面的章节里，我们看到了指令如何被派发、如何在发射队列中等待、如何通过旁路网络获取操作数。现在，终于到了指令真正"干活"的地方——执行单元。

:::info
⚡读完本章，你将能够：

* ✅ 理解香山后端八类执行单元的整体布局与分工
* ✅ 掌握每类执行单元的功能范围、延迟特征和写回目标
* ✅ 认识标量与向量执行单元的关键差异
* ✅ 了解控制流指令和 CSR 指令的特殊处理机制

:::

***

## 9.1 整体定位：执行单元是干什么的？

你可以把执行单元想象成一座**工厂的车间**——每个车间负责不同的工序：

* 有的车间做加法（ALU），又快又简单
* 有的车间做乘法，稍微慢一点
* 有的车间做除法，慢得令人发指
* 有的车间专门跑向量指令，一次能处理一大堆数据
* 还有的车间负责和"外部世界"打交道（访存、CSR）

每个执行单元由一个或多个**功能单元** 组合而成。同一个执行单元可以包含多个 Fu——就像一个车间里可以装好几台不同的机器，谁闲着谁上工。

***

## 9.2 八类执行单元总览

香山的功能单元由 FuConfig 定义（FuConfig.scala），每个 FuConfig 描述了一个功能单元的全部属性：

```scala
// FuConfig.scala
case class FuConfig (
  name          : String,           // 功能单元名称
  fuType        : FuType.OHType,    // 功能类型编码
  fuGen         : (Parameters, FuConfig) => FuncUnit,  // 实例化函数
  srcData       : Seq[Seq[DataConfig]],  // 源操作数数据类型
  piped         : Boolean,          // 是否流水化
  maybeBlock    : Boolean = false,  // 是否可能阻塞流水线
  writeIntRf    : Boolean = false,  // 写整数寄存器堆
  writeFpRf     : Boolean = false,  // 写浮点寄存器堆
  writeVecRf    : Boolean = false,  // 写向量寄存器堆
  writeV0Rf     : Boolean = false,  // 写 V0 寄存器堆
  writeVlRf     : Boolean = false,  // 写 VL 寄存器堆
  latency       : HasFuLatency = CertainLatency(0),  // 延迟
  exceptionOut  : Seq[Int] = Seq(), // 可能产生的异常
  flushPipe     : Boolean = false,  // 是否需要冲刷流水线
  // ...
)
```

关键属性解读：

* **latency**：<code>**CertainLatency(n)**</code> 表示确定 n 拍延迟；<code>**UncertainLatency()**</code> 表示延迟不确定（如除法器）
* **piped**：是否流水化——流水化的功能单元每拍可接受新指令，非流水化的在执行期间独占
* **maybeBlock**：可能阻塞——执行期间会占据功能单元，其他指令等待
* **writeXxxRf**：写回目标寄存器堆类型，一个 Fu 可以同时写多个 RF（如写整数 RF + 写浮点 RF）

各功能单元按调度域分类如下：

| **调度域** | **功能单元** | **写回目标** | **典型延迟** |
| --- | --- | --- | --- |
| Int | alu, mul, div, bku, jmp, brh, csr, fence, i2f, mou | 整数 RF / 浮点 RF（i2f） | 0~数十拍 |
| FP | falu, fmac, fDivSqrt, fcvt, fcmp, f2i, f2v | 浮点 RF / 整数 RF（f2i） | 2~数十拍 |
| VF/Mem | vialuF, vimac, vidiv, vppu, vipu, vfalu, vfma, vfdiv, vfcvt, vmove, vsetfwf, vldu, vstu, vsegldu, vsegstu, ldu, stu, std | 向量 RF / V0 RF / VL RF / 整数 RF（ldu） | 0~数十拍 |

:::warning
❤️新手建议\
你不需要一次记住所有功能单元的名字。核心只需理解：**标量管单条数据，向量管一捆数据；整数/浮点/访存是三大计算类型，控制流和 CSR 是特殊指令**。

:::

***

## 9.3 Scalar Integer（标量整数）

标量整数执行单元是处理器中**最忙碌的车间**——几乎所有程序都在不停地做加、减、与、或、移位。它们的特点是：**快、多、简单**。

### 9.3.1 功能单元一览

| **功能单元** | **职责** | **延迟类型** | **写回目标** |
| --- | --- | --- | --- |
| ALU | 算术逻辑运算：加、减、与、或、异或、移位 | CertainLatency(0) | 整数 RF |
| MUL | 乘法运算 | CertainLatency(1) | 整数 RF |
| DIV | 除法运算 | UncertainLatency() | 整数 RF |
| BKU | 位操作：CLZ、CTZ、CPOP 等 | CertainLatency(0) | 整数 RF |
| I2F | 整数转浮点 | CertainLatency(2) | 浮点 RF |
| JMP | 跳转：JAL、JALR、AUIPC | CertainLatency(0) | 整数 RF + 重定向 |

```scala
// FuConfig.scala
val AluCfg = FuConfig(
  name = "alu", fuType = FuType.alu,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true, writeIntRf = true,
  immType = Set(Imm_I(), Imm_J(), Imm_U(), Imm_LUI32()),
  // latency 默认 CertainLatency(0)
)
 
val MulCfg = FuConfig(
  name = "mul", fuType = FuType.mul,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true, writeIntRf = true,
  latency = CertainLatency(2),      // ← 注意：是 2 拍，不是 1 拍
)
 
val DivCfg = FuConfig(
  name = "div", fuType = FuType.div,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = false, writeIntRf = true,  // ← piped=false，非流水化
  latency = UncertainLatency(),
  hasInputBuffer = (true, 4, true),  // 有 4 项输入缓冲
)
 
val BkuCfg = FuConfig(
  name = "bku", fuType = FuType.bku,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true, writeIntRf = true,
  latency = CertainLatency(2),       // ← 注意：是 2 拍，不是 0 拍
)
 
val I2fCfg = FuConfig(
  name = "i2f", FuType.i2f,
  srcData = Seq(Seq(IntData())),
  piped = true, writeFpRf = true, writeFflags = true,
  latency = CertainLatency(2, extraValue = 1),  // ← 含额外延迟
  needSrcFrm = true,
)
 
val JmpCfg = FuConfig(
  name = "jmp", fuType = FuType.jmp,
  srcData = Seq(Seq(IntData())),
  piped = true,
  immType = Set(Imm_I(), Imm_J(), Imm_U()),
)
 
val BrhCfg = FuConfig(
  name = "brh", fuType = FuType.brh,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true,
  immType = Set(Imm_B()),
)
 
val CsrCfg = FuConfig(
  name = "csr", fuType = FuType.csr,
  srcData = Seq(Seq(IntData())),
  piped = false, writeIntRf = true,
  latency = UncertainLatency(),
  exceptionOut = Seq(illegalInstr, virtualInstr, breakPoint, ecallU, ecallS, ecallVS, ecallM),
  flushPipe = true,
)
```

### 9.3.2 关键特征

**ALU 是 0 延迟的**——发射当拍就有结果，可以通过 Forward 通路当拍前递。这是整条流水线中最快的数据通路。

**MUL 是 2 拍延迟**——乘法器需要 2 个时钟周期完成计算，通过 Bypass 通路在 1 拍后前递。

**DIV 是非流水化的（piped=false）**——执行期间独占功能单元，其他指令只能等待。DIV 没有设置 <code>**maybeBlock=true**</code>（默认 false），但 <code>**piped=false**</code> 的效果等价于阻塞——非流水化的 FU 在执行一条指令时无法接受新指令。此外，DIV 配备了 4 项输入缓冲（<code>**hasInputBuffer = (true, 4, true)**</code>），允许在阻塞期间暂存少量待执行的指令。

**BKU 是 2 拍延迟**——位操作的延迟比 ALU 更长，需要 2 个时钟周期。

**I2F 跨域写回**：结果写浮点寄存器堆（<code>**writeFpRf=true**</code>），延迟为 <code>**CertainLatency(2, extraValue=1)**</code>，其中 <code>**extraValue=1**</code> 表示额外的延迟补偿。I2F 还需要浮点舍入模式信号（<code>**needSrcFrm=true**</code>），并可能写 fflags CSR（<code>**writeFflags=true**</code>）。

**JMP/BRH/CSR 可能产生重定向**——跳转指令、条件分支和 CSR 指令可能改变程序流：

```scala
// FuConfig.scala
def hasRedirect: Boolean = Seq(FuType.jmp, FuType.brh, FuType.csr).contains(fuType)
```

***

## 9.4 Scalar Floating-point（标量浮点）

| **功能单元** | **职责** | **延迟类型** | **写回目标** |
| --- | --- | --- | --- |
| FALU | 浮点加减、分类、移动 | CertainLatency | 浮点 RF |
| FMAC | 浮点乘加（FMA） | CertainLatency | 浮点 RF |
| FDivSqrt | 浮点除法/开方 | UncertainLatency() | 浮点 RF |
| FCVT | 浮点格式转换 | CertainLatency | 浮点 RF |
| FCMP | 浮点比较 | CertainLatency(0, extraValue=3) | **整数 RF** |
| F2I | 浮点转整数 | CertainLatency | 整数 RF |
| F2V | 浮点送入向量单元 | CertainLatency(0, extraValue=3) | 浮点 RF + 向量 RF + V0 RF |
| I2V | 整数送入向量单元 | CertainLatency(0, extraValue=3) | 浮点 RF + 向量 RF + V0 RF |

```scala
// FuConfig.scala
val FcmpCfg = FuConfig(
  name = "fcmp", FuType.fcmp,
  srcData = Seq(Seq(FpData(), FpData())),  // ← 读浮点寄存器
  piped = true,
  writeIntRf = true,                        // ← 写整数寄存器
  writeFflags = true,
  latency = CertainLatency(0, extraValue = 3),  // ← base=0 但有 extraValue=3
)
 
// FuConfig.scala
val F2vCfg = FuConfig(
  name = "f2v", FuType.f2v,
  srcData = Seq(
    Seq(FpData(), FpData()),  // 配置1：两个浮点源
    Seq(FpData()),             // 配置2：一个浮点源
  ),
  piped = true,
  writeFpRf = true, writeVecRf = true, writeV0Rf = true,  // ← 同时写三个 RF
  latency = CertainLatency(0, extraValue = 3),
  destDataBits = 128, srcDataBits = Some(64),
)
 
// FuConfig.scala
val I2vCfg = FuConfig(
  name = "i2v", FuType.i2v,
  srcData = Seq(Seq(IntData(), IntData())),  // ← 读整数寄存器
  piped = true,
  writeFpRf = true, writeVecRf = true, writeV0Rf = true,  // ← 同时写三个 RF
  latency = CertainLatency(0, extraValue = 3),
  destDataBits = 128, srcDataBits = Some(64),
  immType = Set(Imm_OPIVIU(), Imm_OPIVIS(), Imm_VRORVI()),
)
```

关键特征：

* **浮点功能单元需要 FPU 控制信号**（<code>**needSrcFrm=true**</code>），包括舍入模式。这适用于 FMAC、FDivSqrt、I2F 等：

```scala
// FuConfig.scala
def needFPUCtrl: Boolean = {
  import FuType._
  Seq(fmac, fDivSqrt, i2f).contains(fuType)
}
```

* **FDivSqrt 是非流水化的**（<code>**piped=false**</code>），延迟不确定（<code>**UncertainLatency()**</code>），与整数 DIV 类似
* **FCMP 跨域写回整数 RF**：比较结果是布尔值，存入整数寄存器。注意 FCMP 的延迟是 <code>**CertainLatency(0, extraValue=3)**</code>，base 延迟为 0 但有 3 拍额外延迟
* **F2V 和 I2V 同时写三个 RF**：<code>**writeFpRf=true, writeVecRf=true, writeV0Rf=true**</code>，这是向量-标量交互的关键桥接单元
* **F2V 有多组源数据配置**：<code>**srcData**</code> 包含两套配置（双浮点源和单浮点源），适应不同的浮点-向量转换指令

***

## 9.5 vector（向量）

向量执行单元是香山为 RISC-V V 扩展设计的核心模块，一次可处理多个数据元素：

| **功能单元** | **职责** | **写回目标** |
| --- | --- | --- |
| VIALUF | 向量整数 ALU | 向量 RF / V0 RF |
| VIMAC | 向量整数乘加 | 向量 RF |
| VIDIV | 向量整数除法 | 向量 RF |
| VPU/VIPU | 向量 permutation | 向量 RF |
| VPPU | 向量 population count 等 | 向量 RF |
| VFALU | 向量浮点 ALU | 向量 RF |
| VFMA | 向量浮点乘加 | 向量 RF |
| VFDIV | 向量浮点除法 | 向量 RF |
| VFCVT | 向量浮点格式转换 | 向量 RF |
| VMOVE | 向量寄存器搬运 | 向量 RF |
| VSETFWF | V 配置指令 | 向量 RF / VL RF |

源码定义：

```scala
// FuConfig.scala
val VialuCfg = FuConfig(
  name = "vialuFix", fuType = FuType.vialuF,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeVecRf = true, writeV0Rf = true, writeVxsat = true,
  latency = CertainLatency(1),
  needSrcVxrm = true, vlWakeUp = true, maskWakeUp = true,
  readVl = true, destDataBits = 128,
)
 
val VimacCfg = FuConfig(
  name = "vimac", fuType = FuType.vimac,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeVecRf = true, writeV0Rf = true, writeVxsat = true,
  latency = CertainLatency(2),
  needSrcVxrm = true, vlWakeUp = true, maskWakeUp = true,
  readVl = true, destDataBits = 128,
)
 
val VidivCfg = FuConfig(
  name = "vidiv", fuType = FuType.vidiv,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = false,  // ← 非流水化
  writeVecRf = true, writeV0Rf = true,
  latency = UncertainLatency(),
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128,
)
 
val VipuCfg = FuConfig(
  name = "vipu", fuType = FuType.vipu,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeIntRf = true, writeVecRf = true, writeV0Rf = true,  // ← 三重写回
  latency = CertainLatency(2),
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128,
)
 
val VmoveCfg = FuConfig(
  name = "vmove", fuType = FuType.vmove,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeIntRf = true, writeFpRf = true, writeVecRf = true, writeV0Rf = true,  // ← 四重写回！
  latency = CertainLatency(0, extraValue = 3),
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128, readVl = true,
)
```

VSET 指令实际有三种子配置：

```scala
// FuConfig.scala
val VSetRvfWvfCfg = FuConfig(  // vsetvli: 读向量源，写 VL + VTYPE + 整数 RF
  name = "vsetrvfwvf", fuType = FuType.vsetfwf,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true, writeVlRf = true, writeVType = true, writeIntRf = true,
  latency = CertainLatency(0), readVl = true, readOldVtype = true,
)
 
val VSetRiWvfCfg = FuConfig(   // vsetivli: 读整数源，写 VL + VTYPE
  name = "vsetriwvf", fuType = FuType.vsetiwf,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true, writeVlRf = true, writeVType = true,
  latency = CertainLatency(0),
)
 
val VSetRiWiCfg = FuConfig(    // vsetvli 写整数: 写整数 RF
  name = "vsetriwi", fuType = FuType.vsetiwi,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true, writeIntRf = true,
  latency = CertainLatency(0),
)
```

关键特征：

* **几乎所有向量功能单元都写 V0 RF**——向量指令的 mask 结果需要写回 V0 寄存器。这与原文说的"向量浮点不写 V0"完全相反
* **VIPU 是唯一写整数 RF 的向量算术单元**——permutation 结果可能写回整数寄存器
* **VMOVE 写回目标最多**——同时写 int RF + fp RF + vec RF + V0 RF，因为向量-标量搬运需要跨多个寄存器堆
* **VIDIV 是非流水化的**（<code>**piped=false**</code>），延迟不确定，与标量 DIV 类似
* **VSET 有三种子类型**，根据 FuType 区分，写回目标各不相同

***

## 9.6 Memory（访存）

| **功能单元** | **职责** | **写回目标** | **比喻** |
| --- | --- | --- | --- |
| LDU | 标量 Load | 整数 RF / 浮点 RF | 进货——从仓库搬东西 |
| STU | 标量 Store | 无（仅写存储器） | 出货——往仓库搬东西 |
| MOU | 内存序（FENCE 等） | 无 | 交警——保证秩序 |
| VLDU | 向量 Load | 向量 RF | 批量进货 |
| VSTU | 向量 Store | 无 | 批量出货 |
| STD | Store 数据搬运 | 无 | 传菜员——把数据送到 Store 单元 |

源码定义：

**<font style="background-color:rgba(0, 0, 0, 0);">Copy cod</font>**

```scala
// FuConfig.scala
val LduCfg = FuConfig(
  name = "ldu", fuType = FuType.ldu,
  srcData = Seq(Seq(IntData())),
  piped = false,
  writeIntRf = true, writeFpRf = true,  // ← 双重写回
  latency = UncertainLatency(3),         // ← 不确定延迟，基础 3 拍
  exceptionOut = Seq(loadAddrMisaligned, loadAccessFault, loadPageFault, ...),
  hasLoadError = true, trigger = true,
)

val StaCfg = FuConfig(  // Store 地址计算
  name = "sta", fuType = FuType.stu,
  srcData = Seq(Seq(IntData())),
  piped = false, latency = UncertainLatency(),
  exceptionOut = Seq(storeAddrMisaligned, storeAccessFault, ...),
)

val StdCfg = FuConfig(  // Store 数据搬运
  name = "std", fuType = FuType.stu,   // ← 与 STA 共享 FuType！
  srcData = Seq(Seq(IntData()), Seq(FpData())),  // ← 可读整数或浮点数据
  piped = true, latency = CertainLatency(0),
)

val MouCfg = FuConfig(
  name = "mou", fuType = FuType.mou,
  srcData = Seq(Seq(IntData())),
  piped = false,
  writeFakeIntRf = true,  // ← 写 FakeInt RF（用于流水线同步，非真实数据）
  latency = UncertainLatency(),
  exceptionOut = (LduCfg.exceptionOut ++ StaCfg.exceptionOut ++ StdCfg.exceptionOut).distinct,
)

val VlduCfg = FuConfig(
  name = "vldu", fuType = FuType.vldu,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = false,
  writeVecRf = true, writeV0Rf = true, writeVlRf = true,  // ← 三重写回
  latency = UncertainLatency(),
  vlWakeUp = true, maskWakeUp = true,
  hasLoadError = true, destDataBits = 128,
)
```

关键特征：

* **LDU 双重写回**：<code>**writeIntRf=true, writeFpRf=true**</code>，同一时刻只有一个有效，取决于加载的数据类型
* **STA 和 STD 共享 FuType（FuType.stu）**——Store 操作被拆分为地址计算（STA）和数据搬运（STD）两个阶段，由 <code>**name**</code> 区分
* **STD 读取两种数据源**：<code>**srcData = Seq(Seq(IntData()), Seq(FpData()))**</code>，可读整数或浮点数据
* **MOU 写 FakeInt RF**——<code>**writeFakeIntRf=true**</code>，这不是真实的寄存器堆写回，而是用于流水线同步的占位信号
* **VLDU 写三个 RF**：向量 Load 的结果写 vec RF + V0 RF + VL RF
* **所有访存单元都是非流水化的**（<code>**piped=false**</code>），延迟均为 UncertainLatency

***

## 9.7 Vector Floating-point（向量浮点）

向量浮点执行单元是标量浮点的"向量版"——一条指令同时处理多个浮点元素。

### 9.7.1 功能单元一览

| **功能单元** | **职责** | **延迟** | **写回目标** | **piped** |
| --- | --- | --- | --- | --- |
| VFALU | 向量浮点加减、比较 | CertainLatency(1) | 向量 RF + V0 RF + 浮点 RF + fflags | true |
| VFMA | 向量浮点乘加 | CertainLatency(3) | 向量 RF + V0 RF + fflags | true |
| VFDIV | 向量浮点除法和开方 | UncertainLatency() | 向量 RF + V0 RF + fflags | **false** |
| VFCVT | 向量浮点格式转换 | CertainLatency(2) | 向量 RF + V0 RF + fflags | true |

源码定义：

```scala
// FuConfig.scala
val VfaluCfg = FuConfig(
  name = "vfalu", fuType = FuType.vfalu,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeVecRf = true, writeV0Rf = true, writeFpRf = true,  // ← 也写浮点 RF！
  writeFflags = true,
  latency = CertainLatency(1),
  needSrcFrm = true, vlWakeUp = true, maskWakeUp = true,
  readVl = true, destDataBits = 128,
)
 
val VfmaCfg = FuConfig(
  name = "vfma", fuType = FuType.vfma,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeVecRf = true, writeV0Rf = true,  // ← 写 V0 RF！
  writeFflags = true,
  latency = CertainLatency(3),
  needSrcFrm = true, vlWakeUp = true, maskWakeUp = true,
  readVl = true, destDataBits = 128,
)
 
val VfdivCfg = FuConfig(
  name = "vfdiv", fuType = FuType.vfdiv,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = false,  // ← 非流水化
  writeVecRf = true, writeV0Rf = true,
  writeFflags = true,
  latency = UncertainLatency(),
  needSrcFrm = true, vlWakeUp = true, maskWakeUp = true,
  readVl = true, destDataBits = 128,
)
 
val VfcvtCfg = FuConfig(
  name = "vfcvt", fuType = FuType.vfcvt,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = true,
  writeVecRf = true, writeV0Rf = true,
  writeFflags = true,
  latency = CertainLatency(2),  // ← 2 拍，不是 1 拍
  needSrcFrm = true, vlWakeUp = true, maskWakeUp = true,
  readVl = true, destDataBits = 128,
)
```

### 9.7.2 关键特征

* **所有向量浮点功能单元都写 V0 RF**——向量浮点指令的 mask 结果也需要写回 V0 寄存器
* **VFALU 还写浮点 RF**——某些浮点-向量交互指令需要写回标量浮点寄存器
* **所有向量浮点功能单元都写 fflags**——浮点运算可能产生浮点异常标志
* **所有向量浮点功能单元都需要 FRM**（<code>**needSrcFrm=true**</code>）——与标量浮点共享舍入模式
* **VFDIV 是非流水化的**（<code>**piped=false**</code>），延迟不确定，与标量 FDivSqrt 类似
* **VFCVT 延迟是 2 拍**——不是 1 拍，格式转换比简单加减更复杂
* **需要额外的 OG2 流水级**——向量执行单元比标量单元多一级流水，因此旁路需要 Bypass2 延迟

```scala
// FuConfig.scala — 向量算术指令需要 OG2
def needOg2: Boolean = isVecArith || fuType == FuType.vsetfwf || isVecMem
```

:::warning
❤️新手建议\
向量浮点与向量整数的最大区别在于**写回目标**——向量浮点只写向量 RF，不会写 V0/VL；而向量整数中的某些指令（如 VSET）会写 VL 和 VTYPE。这是区分它们的关键。

:::

***

## 9.8 Vector Memory（向量访存）

向量访存是所有执行单元中**最复杂的**——一条向量 Load/Store 指令可能涉及数十个元素、多个地址计算、Segment 拆分，甚至不确定的执行时间。

### 9.8.1 功能单元一览

| **功能单元** | **职责** | **延迟** | **写回** |
| --- | --- | --- | --- |
| **VLDU** | 向量 Load | 3 拍起（Cache 命中） | 向量 RF |
| **VSTU** | 向量 Store | 3 拍起 | 无 |
| **VSEGLDU** | 向量 Segment Load | 3 拍起 | 向量 RF |
| **VSEGSTU** | 向量 Segment Store | 3 拍起 | 无 |

源码定义：

```scala
// FuConfig.scala
val VlduCfg = FuConfig(
  name = "vldu", fuType = FuType.vldu,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = false,
  writeVecRf = true, writeV0Rf = true, writeVlRf = true,  // ← 三重写回
  latency = UncertainLatency(),
  hasLoadError = true, trigger = true,
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128, readVl = true,
)
 
val VstuCfg = FuConfig(
  name = "vstu", fuType = FuType.vstu,
  srcData = Seq(Seq(VecData(), VecData(), VecData(), V0Data())),
  piped = false,
  latency = UncertainLatency(),
  hasLoadError = true, trigger = true,
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128, readVl = true,
)
 
val VseglduCfg = FuConfig(
  name = "vsegldu", fuType = FuType.vsegldu,
  piped = false,
  writeVecRf = true, writeV0Rf = true, writeVlRf = true,  // ← 三重写回
  latency = UncertainLatency(),
  hasLoadError = true,
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128, readVl = true,
)
 
val VsegstuCfg = FuConfig(
  name = "vsegstu", fuType = FuType.vsegstu,
  piped = false,
  latency = UncertainLatency(),
  hasLoadError = true,
  vlWakeUp = true, maskWakeUp = true,
  destDataBits = 128, readVl = true,
)
```

### 9.8.2 关键特征

* **所有向量访存单元都是非流水化的**（<code>**piped=false**</code>），延迟均为 <code>**UncertainLatency()**</code>——因为实际延迟取决于 Cache 是否命中、元素数量等因素
* **VLDU 和 VSEGLDU 写三个 RF**：<code>**writeVecRf=true, writeV0Rf=true, writeVlRf=true**</code>。V0 RF 写回 mask 结果，VL RF 写回实际加载的元素数
* **VSTU 和 VSEGSTU 不写寄存器堆**——Store 只写存储器
* **所有向量访存单元都支持 vlWakeUp 和 maskWakeUp**——向量访存完成后需要唤醒依赖 VL 和 mask 的后续指令
* **所有向量访存单元都有 LoadError**（<code>**hasLoadError=true**</code>）——Load 可能因访存异常而取消
* 一条向量访存指令可能被拆成多个 Flow——Dispatch 阶段的 conserveFlows 机制就是为它服务的
* Segment 指令只能从端口 0 发射——因为它们需要的资源最多

FuType 中的向量访存分类：

```scala
// FuType.scala
val vecMem = Seq(vldu, vstu, vsegldu, vsegstu)
val isVLoad    = Seq(vldu, vsegldu)
val isVStore   = Seq(vstu, vsegstu)
val isVSegLoad = Seq(vsegldu)
val isVSegStore = Seq(vsegstu)
```

***

## 9.9 Control Flow（控制流：分支与跳转）

控制流指令不"计算"数据，它们决定**程序的走向**——下一条该执行哪条指令？它们的特殊性在于：可能触发**流水线冲刷**。

### 9.9.1 功能单元一览

| **功能单元** | **职责** | **延迟** | **特殊输出** |
| --- | --- | --- | --- |
| **JMP** | 跳转：JAL、JALR、AUIPC | 1 拍 | 重定向（目标地址） |
| **BRH** | 分支：BEQ、BNE、BLT 等 | 1~2 拍 | 重定向（跳转/不跳转） |

```scala
// FuConfig.scala
val JmpCfg = FuConfig(
  name = "jmp", fuType = FuType.jmp,
  srcData = Seq(Seq(IntData())),
  piped = true,
  immType = Set(Imm_I(), Imm_J(), Imm_U()),
  // latency 默认 CertainLatency(0)
)
 
val BrhCfg = FuConfig(
  name = "brh", fuType = FuType.brh,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = true,
  immType = Set(Imm_B()),
  // latency 默认 CertainLatency(0)
)
```

### 9.9.2 关键特征

**JMP 和 BRH 都是 0 拍延迟**——发射当拍就能得到跳转结果。这意味着重定向信号可以在同一周期内发出，流水线冲刷可以在下一周期生效。

**可能产生重定向**——如果分支预测错误，需要冲刷流水线并从正确路径重新取指：

```scala
// FuConfig.scala
def hasRedirect: Boolean = Seq(FuType.jmp, FuType.brh, FuType.csr).contains(fuType)
```

**需要 PC 和 FTQ 指针**——用于计算跳转目标和比对预测结果：

```scala
// FuConfig.scala
def needPc: Boolean = Seq(FuType.jmp, FuType.brh, FuType.ldu).contains(fuType)
def needTargetPc: Boolean = Seq(FuType.jmp, FuType.brh).contains(fuType)
def needPdInfo: Boolean = Seq(FuType.jmp, FuType.brh).contains(fuType)
```

**JMP 指令会操作 RAS**——JAL 是 Push，JALR 可能是 Pop，用于函数调用/返回栈的维护：

```scala
// FuConfig.scala
def hasRasAction: Boolean = Seq(FuType.jmp).contains(fuType)
```

**JMP 和 BRH 设置 blockBackward**——确保后续指令不会在控制流确定之前提前发射：

```scala
// FuType.scala
val blockBackCompress = Seq(brh, jmp)
def isBlockBackCompress(fuType: UInt): Bool = FuTypeOrR(fuType, blockBackCompress)
```

:::warning
💡核心思想\
控制流指令的核心矛盾是：**预测可能对，也可能错**。对了就万事大吉，错了就要回滚。香山的设计哲学是"大胆预测，小心验证"——先按预测往下跑，等实际执行结果出来再确认。预测正确的开销为 0，预测错误的开销是十几拍的流水线冲刷。

:::

***

## 9.10 Control and Status Register（CSR 与控制寄存器）

CSR 指令是处理器中最"特殊"的一类——它们不直接参与计算，而是**读写处理器的内部控制状态**。它们就像是工厂的"管理办公室"——发号施令、查看报表、修改参数。

### 9.10.1 功能单元一览

| **功能单元** | **职责** | **延迟** | **特殊输出** | **piped** |
| --- | --- | --- | --- | --- |
| CSR | 读写 CSR 寄存器 | UncertainLatency() | 重定向 + 异常 + flushPipe | **false** |
| FENCE | 内存屏障 | UncertainLatency() | flushPipe | **false** |

```scala
// FuConfig.scala#
val CsrCfg = FuConfig(
  name = "csr", fuType = FuType.csr,
  srcData = Seq(Seq(IntData())),
  piped = false,                    // ← 非流水化
  writeIntRf = true,
  latency = UncertainLatency(),     // ← 延迟不确定
  exceptionOut = Seq(illegalInstr, virtualInstr, breakPoint, ecallU, ecallS, ecallVS, ecallM),
  flushPipe = true,                 // ← 需要冲刷流水线
)
 
val FenceCfg = FuConfig(
  name = "fence", fuType = FuType.fence,
  srcData = Seq(Seq(IntData(), IntData())),
  piped = false,                    // ← 非流水化
  latency = UncertainLatency(),     // ← 延迟不确定
  flushPipe = true,                 // ← 需要冲刷流水线
)
```

### 9.10.2 关键特征

* **CSR 和 FENCE 都是非流水化的**（<code>**piped=false**</code>），延迟均为 <code>**UncertainLatency()**</code>。这意味着 CSR/FENCE 执行期间独占功能单元，其他指令无法使用同一个执行单元。

**CSR 可能产生重定向**——比如写 satp 切换页表后需要冲刷 TLB 和流水线；写 mepc 后执行 MRET 也会重定向：

```scala
// FuConfig.scala
def hasRedirect: Boolean = Seq(FuType.jmp, FuType.brh, FuType.csr).contains(fuType)
```

**CSR 可能产生多种异常**——权限不足、非法 CSR 编号、ecall、断点等：

```plain
// FuConfig.scala
exceptionOut = Seq(illegalInstr, virtualInstr, breakPoint, ecallU, ecallS, ecallVS, ecallM)
```

**CSR 和 FENCE 都需要 flushPipe**——执行后必须冲刷流水线，确保所有后续指令都能看到 CSR/FENCE 的效果。

**FENCE 确保内存序**——所有在 FENCE 之前的访存指令完成后，才允许后续指令继续。

**CSR 需要触发器**——用于在 CSR 读写时触发调试模式。

**CSR 是不确定延迟唤醒的**——与 DIV、FDivSqrt、VIDIV、VFDIV 一样：

```scala
// FuConfig.scala
def needUncertainWakeupFuConfigs = Seq(
  CsrCfg, DivCfg, FdivCfg, VfdivCfg, VidivCfg
)
```

### 9.10.3 CSR 的分类

香山的 CSR 子系统按照 RISC-V 特权架构分层组织：

| **层级** | **对应模块** | **覆盖的 CSR** |
| --- | --- | --- |
| **Machine Level** | MachineLevel.scala | mstatus、mepc、mcause、mtvec... |
| **Supervisor Level** | SupervisorLevel.scala | sstatus、sepc、scause、stval... |
| **Virtual Supervisor** | VirtualSupervisorLevel.scala | vsstatus、vsepc... |
| **Hypervisor** | HypervisorLevel.scala | hstatus、hgeip... |
| **Debug** | Debug.scala | dcsr、dpc、dscratch... |
| **PMA/PMP** | PMAEntryModule / PMPEntryModule | 物理内存属性 / 物理内存保护 |

来源：[<font style="color:rgb(0, 176, 170);">NewCSR/</font>](https://github.com/OpenXiangShan/XiangShan/blob/master/src/main/scala/xiangshan/backend/fu/NewCSR/) 目录结构

***

## 9.11 执行单元的延迟与唤醒关系

理解执行单元的延迟，是理解整个后端唤醒机制的基础。香山后端从 Issue Queue 发射到写回的流水级如下：

```plain
发射 ──→ OG0 ──→ OG1 ──→ OG2 ──→ 执行 ──→ 写回
        (选通)  (读RF)  (向量专用)          (唤醒)
```

* **OG0**：发射当拍，Issue Queue 选出指令并输出
* **OG1**：寄存器堆读取 + BypassNetwork 数据选择
* **OG2**：向量执行单元专用的额外流水级（标量单元无此级）
* **执行**：功能单元实际计算
* **写回**：结果写回寄存器堆，同时唤醒依赖指令

各功能单元的延迟与唤醒路径：

```scala
// FuConfig.scala — 延迟与唤醒类型的关系
// CertainLatency(0)    → Forward（当拍前递）
// CertainLatency(n)    → Bypass / IQ Wakeup（确定延迟唤醒）
// UncertainLatency()   → Uncertain Wakeup（不确定延迟，需等待实际写回）
 
// FuType.scala — 不确定延迟的功能单元
def isUncertain(fuType: UInt): Bool = FuTypeOrR(fuType, csr, div, fDivSqrt, vidiv, vfdiv)
 
// FuConfig.scala — 需要不确定唤醒的配置
def needUncertainWakeupFuConfigs = Seq(CsrCfg, DivCfg, FdivCfg, VfdivCfg, VidivCfg)
 
// FuConfig.scala — 0 延迟功能单元
def is0latency(fuType: UInt): Bool = {
  val fuTypes = FuConfig.allConfigs.filter(_.latency == CertainLatency(0)).map(_.fuType)
  FuTypeOrR(fuType, fuTypes)
}
```

### 各功能单元详细延迟：

| **功能单元** | **延迟类型** | **实际延迟** | **唤醒路径** | **写回目标** |
| --- | --- | --- | --- | --- |
| ALU | CertainLatency(0) | 0 拍 | Forward | 整数 RF |
| BKU | CertainLatency(2) | 2 拍 | Bypass / IQ Wakeup | 整数 RF |
| MUL | CertainLatency(2) | 2 拍 | Bypass / IQ Wakeup | 整数 RF |
| DIV | UncertainLatency() | 数十拍 | Uncertain Wakeup | 整数 RF |
| I2F | CertainLatency(2, extraValue=1) | 2+拍 | Bypass / IQ Wakeup | 浮点 RF |
| JMP | CertainLatency(0) | 0 拍 | Forward + Redirect | 整数 RF |
| BRH | CertainLatency(0) | 0 拍 | Forward + Redirect | 无/重定向 |
| FALU | CertainLatency(1) | 1 拍 | Bypass | 浮点 RF |
| FMAC | CertainLatency(3) | 3 拍 | IQ Wakeup | 浮点 RF |
| FDivSqrt | UncertainLatency() | 数十拍 | Uncertain Wakeup | 浮点 RF |
| FCMP | CertainLatency(0, extraValue=3) | 0+3 拍 | Bypass | 整数 RF |
| FCVT | CertainLatency(2, extraValue=1) | 2+拍 | Bypass | 浮点+整数 RF |
| LDU | UncertainLatency(3) | 3+拍 | Uncertain Wakeup + ldCancel | 整数+浮点 RF |
| VIALUF | CertainLatency(1) | 1+OG2 拍 | Bypass2 | 向量+V0 RF |
| VIMAC | CertainLatency(2) | 2+OG2 拍 | Bypass2 | 向量+V0 RF |
| VIDIV | UncertainLatency() | 不确定 | Uncertain Wakeup | 向量+V0 RF |
| VFALU | CertainLatency(1) | 1+OG2 拍 | Bypass2 | 向量+V0+浮点 RF |
| VFMA | CertainLatency(3) | 3+OG2 拍 | Bypass2 + IQ Wakeup | 向量+V0 RF |
| VFDIV | UncertainLatency() | 不确定 | Uncertain Wakeup | 向量+V0 RF |
| VFCVT | CertainLatency(2) | 2+OG2 拍 | Bypass2 | 向量+V0 RF |

唤醒类型对照：

| **唤醒类型** | **适用场景** | **延迟范围** | **机制** |
| --- | --- | --- | --- |
| Forward | CertainLatency(0) 的 ALU/JMP/BRH | 0 拍 | 执行单元当拍输出直接前递 |
| Bypass | CertainLatency(n), n>0 的标量单元 | 1~3 拍 | RegNext/RegEnable 寄存后前递 |
| Bypass2 | 带 OG2 的向量单元 | 2+ 拍 | bypass 数据再寄存一拍，跨域前递 |
| IQ Wakeup | 多拍延迟的确定延迟单元 | 3+ 拍 | 通过 MultiWakeupQueue 延迟唤醒 |
| Uncertain Wakeup | UncertainLatency 的 DIV/FDivSqrt/LDU/CSR/VFDIV/VIDIV | 不确定 | 等待实际写回信号才唤醒 |

```scala
// FuType.scala# — 需要浮点舍入模式的功能单元
val vectorNeedFrm = Seq(vfalu, vfma, vfdiv, vfcvt)  // 向量浮点需要 FRM
 
// 会阻塞后续指令的功能单元
val blockBackCompress = Seq(brh, jmp)  // 分支/跳转阻塞后续发射
 
//  需要 OG2 流水级的功能单元
def needOg2: Boolean = isVecArith || fuType == FuType.vsetfwf || isVecMem
```

***

## 9.12 总结

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **标量整数**：ALU（0拍）、MUL（2拍）、BKU（2拍）、DIV（不确定）；ALU/BKU 走 Forward/Bypass，DIV 走 Uncertain Wakeup
* **标量浮点**：FALU（1拍）、FMAC（3拍）是主力，FDivSqrt 最慢（不确定）；FCMP 结果写回整数 RF，FCVT 可能同时写浮点和整数 RF；FMAC/FDivSqrt/I2F 需要 FRM
* **标量访存**：LDU 延迟不确定（基础3拍），Store 拆分为 STA+STD；LDU 写整数+浮点 RF；Load Cancel 是后端唤醒取消机制的核心驱动
* **向量整数/浮点**：一条指令处理多个元素，几乎所有向量 Fu 都写 V0 RF；需要额外的 OG2 流水级，走 Bypass2 旁路；VIPU 还写整数 RF，VMOVE 写四个 RF
* **向量访存**：最复杂的执行单元，一条指令可能拆成多个 Flow；VLDU 写向量+V0+VL 三个 RF；Segment 指令限端口 0；全部非流水化
* **控制流**：JMP/BRH 都是 0 拍延迟，可能触发重定向，预测错误代价极高；设置 blockBackward 阻止后续指令抢先；JMP 操作 RAS
* **CSR/FENCE**：读写处理器内部状态，延迟均不确定，可能产生重定向和异常；都有 flushPipe；CSR 属于 needUncertainWakeup 列表

核心原则：执行单元的多样性决定了后端数据通路的复杂性——不同的延迟需要不同的唤醒机制（Forward→Bypass→Bypass2→IQ Wakeup→Uncertain Wakeup），不同的写回目标需要不同的旁路通道，不同的特殊行为（重定向、flushPipe、blockBackward、ldCancel）需要不同的流水线控制。理解执行单元的特征，就是理解整个后端设计的关键。


> 更新: 2026-07-01 17:35:11  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/nwksndztdhde83p9>
