# 11-PHR 设计分析与刷新方案

> 本文档是 BPU 上下文切换刷新机制中 **PHR（Predicted History Register，预测路径历史寄存器）** 子模块的设计分析与刷新方案文档。
> §1~§3 为分析：PHR 的预测流水线、状态更新路径与整体架构；
> §4~§6 为方案：刷新方案、刷新后的预测流水线、刷新完成信号 `resetDone`。
>
> **范围**：PHR 模块内部的折叠路径历史维护逻辑、`phr` 环形缓冲与 `phrPtr`、各级 folded PHR 流水寄存器，以及在 `contextFlush` 窗口下对 redirect / s3_override / s1_train 三路更新路径的防污染处理。
> **背景**：当前代码（`bpu/Bpu.scala`）尚无 `contextFlush`/`flushState`/`bpuFlushEn` 等刷新机制，01～10 文档均为待实现的设计方案，本文档亦为待实现方案。
>
> **本文的刷新编译开关约定**
>
> 1. **编译开关**：PHR 刷新接口、BPU 顶层分发/完成聚合、寄存器清零、更新/读取/输出门控及 `resetDone` 生成，均遵循 [00-BPU 刷新机制编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1/§3.5 和 [01-BPU 刷新方案概览](01-BPU刷新方案概览.md) §6.2，受 elaboration-time Scala `Boolean` `HasBpuFlush` 统一裁剪。
> 2. **编码规则**：`PhrIO` 刷新端口使用 `Option.when(HasBpuFlush)`；参考 `sx_fire`/`sx_flush` 范式，所有参与刷新门控的 IO 先赋给模块内部 `Wire`，清零、完成与数据通路门控只使用内部 `Wire`，不直接使用 `io.*` 参与门控。可选端口的 `.get` 仅在 `if (HasBpuFlush)` 内用于驱动 `Wire`；刷新专用清零块、连线和完成逻辑放入 `if (HasBpuFlush)`；对既有数据通路追加的门控必须在关闭分支恢复原表达式。禁止用 `getOrElse` 代替刷新专用结构的 Scala 守卫。
> 3. **阶段边界**：01 当前落地阶段明确不包含 PHR；本文描述的 PHR 刷新接入属后续阶段。实现时须同步扩展 00 §3.5 和 01 的顶层分发/聚合范围，不得把本文的代码片段解读为当前占位阶段已经接入。
> 4. **关闭语义**：`HasBpuFlush=false` 时，三个刷新端口与所有消费逻辑均不参与 elaboration，PHR 原有状态、更新优先级、流水使能和输出数据通路必须与未引入刷新机制的基线一致；完整裁剪清单见 §4.5。
>
> 相关总览见 [[01-BPU刷新方案概览]]，主预测器刷新握手语义见该文档 §5。

---

## 1. PHR 存储结构

PHR 的全称是 **Predicted History Register**，它用于把程序最近经过的控制流路径编码成一串历史比特并保存下来。因为 PHR 记录的是推测执行路径，所以在分支预测结果尚未最终确认时，其内容也会随预测结果推测性地向前推进。

PHR 模块内部保存四类功能状态：

| 状态         | 主要结构                                                                     | 作用                                       |
| ------------ | ---------------------------------------------------------------------------- | ------------------------------------------ |
| 原始路径历史 | `phr`、`phrPtr`                                                          | 保存完整的环形路径历史及其逻辑边界         |
| Pending 数据 | `pendingValid`、`pendingTaken`、`pendingLowBits`、`pendingShiftBits` | 暂存尚未写入物理`phr` 的更新             |
| 折叠历史     | `s0~s3_foldedPhrReg`                                                       | 保存供不同流水级和预测器使用的压缩历史     |
| 恢复快照     | `PhrMeta`                                                                  | 保存预测时刻的指针和关键低位，用于恢复历史 |

### 1.1 原始路径历史：`phr` 与 `phrPtr`

核心寄存器定义如下，参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L45)：

```scala
private val phr = RegInit(0.U.asTypeOf(Vec(PhrHistoryLength, Bool())))
private val phrPtr = RegInit(0.U.asTypeOf(new PhrPtr))
```

**存储粒度与路径信息组织。**

`phr` 是由 `PhrHistoryLength` 个 1-bit 寄存器构成的位向量。整个 `phr` 本质上是一段很长的**路径历史位串**；**在默认配置下，总容量为 532 bit。**

```text
phr(0)   phr(1)   phr(2)  ...  phr(PhrHistoryLength-1)
 1bit     1bit     1bit                 1bit
```

单个存储单元为 1 bit，并不意味着一次路径更新只修改 1 bit。默认情况下，分支 PC 和目标地址会生成 15-bit `pathHash`；**一次正常的 taken 更新会改写指针附近的 15 个 1-bit 单元**（其中，低 2bit 为真正新插入的历史，高 13bit 为与原 PHR 低位数值逐位异或的结果）。因此需要区分：

```text
单个 PHR 存储单元的宽度   = 1 bit
一次 taken 更新推进的历史 = 2 bit（Shamt）
一次 taken 更新改写的窗口 = 15 bit
```

PHR 不是由若干个独立的 15-bit `pathHash` 顺序拼接而成，而是一份经过移位和混合持续演化的滚动路径状态。15-bit 数据的拆分、异或和具体写入方式将在 PHR 更新机制中介绍。

**存储长度。**

`PhrHistoryLength` 不是直接写死的，而是**根据预测器和 FTQ 参数计算**；例如，当前 `MinimalConfig` 下的参数会得到 52 bit 的 PHR，默认配置下会得到 532bit 的 PHR。对应代码参见 [`FrontendParameters.scala`](../../src/main/scala/xiangshan/frontend/FrontendParameters.scala#L39)：

```text
PhrHistoryLength = HistoryAlign 对齐(最大预测表路径历史长度 + Shamt × FTQSize + FtqFullFix)
```

各项含义如下：

- `最大预测表路径历史长度`：TAGE、ITTAGE、SC 路径表所需历史长度的最大值；
- `Shamt × FTQSize`：为 FTQ 中尚未提交的推测路径预留空间；
- `FtqFullFix = 4`：FTQ 满载时为恢复保留的额外空间；
- `HistoryAlign = 4`：最终长度按照 4 bit 对齐。

**环形组织。**

如果每出现一次新路径信息，就将几百位历史整体平移一次，硬件代价会很大。因此，PHR 使用环形缓冲区：

```text
物理寄存器：

  0   1   2   3          ...       N-2  N-1
┌───┬───┬───┬───┬────────────────┬───┬───┐
│   │   │   │   │                │   │   │
└───┴───┴───┴───┴────────────────┴───┴───┘
                    ↑
                  phrPtr
```

更新时不需要移动整个 `phr`，而是移动 `phrPtr`，并修改指针附近的少量 bit：

- `phr`：物理存储位置保持不动；
- `phrPtr`：决定从哪里开始解释这串历史；初值为 0，taken 更新时执行 `phrPtr - 2`，指针减法按照 `PhrHistoryLength` 环绕；not-taken 时指针保持不变。
- `phr + phrPtr`：共同组成逻辑上的完整路径历史。

读取时，代码将 `phr` 复制两份并拼接，然后依据指针进行旋转，参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L57)：

```scala
(Cat(phr.asUInt, phr.asUInt) >> (ptr.value + 1.U))(
  PhrHistoryLength - 1,
  0
)
```

因此需要区分两个概念：

```text
物理 PHR：phr 数组中实际存放的顺序
逻辑 PHR：从 phrPtr + 1 开始展开后得到的历史顺序，预测器所使用的是逻辑 PHR。
```

**指针结构。**

`phrPtr` 继承自通用环形队列指针，其中，`value` 用于索引 `phr`，`flag` 用于表达环形指针跨越末尾后的状态。定义参见 [`Bundles.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Bundles.scala#L30) 和 [`CircularQueuePtr.scala`](../../utility/src/main/scala/utility/CircularQueuePtr.scala#L23)。

```text
phrPtr
├── value：当前物理位置
└── flag ：是否已经绕环一圈的标记
```

---

### 1.2 Pending：尚未落入物理 PHR 的数据

当前实现还维护了一组 pending 寄存器：`pendingValid`、`pendingTaken`、`pendingLowBits`、`pendingShiftBits`。参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L48)。

S1 预测和 S3 override 产生的数据会**延后一拍写入物理 `phr`**。在这一拍间隔内，模块不能让预测器看到旧历史，因此会构造如下逻辑视图：

```text
逻辑 PHR = 物理 phr + pending 数据旁路
```

对应变量是 `pendingPhrValue`。

因此，某一拍预测器看到的最新 PHR 不一定完全等于 `phr` 寄存器阵列当时的物理内容，它可能还包含一笔尚未落入物理寄存器的 pending 更新。**pending 只保存一笔临时更新信息**，并不是第二份完整 PHR。

---

### 1.3 折叠历史寄存器

默认配置下的完整 PHR 长达 532 bit，不适合直接作为预测表索引。PHR 因此还保存多组压缩后的折叠历史：s0_foldedPhrReg、s1_foldedPhrReg、s2_foldedPhrReg、s3_foldedPhrReg。它们分别对应 BPU 的 S0～S3 流水级：

```text
完整逻辑 PHR ➡ 多种规格的 folded PHR
                    │
                    ├── S0 folded PHR
                    ├── S1 folded PHR
                    ├── S2 folded PHR
                    └── S3 folded PHR
```

不同预测器、不同预测表需要不同的历史长度和索引宽度，例如：

```text
历史长度 397 bit → 折叠为 9 bit，用于某张表的 index
历史长度 397 bit → 折叠为 13 bit，用于 tag
历史长度 211 bit → 折叠为 12 bit，用于另一种 tag
```

每种折叠历史由两个参数描述：

```scala
FoldedHistoryInfo(
  HistoryLength, // 使用原始 PHR 的多少位
  FoldedLength   // 最终压缩成多少位
)
```

真正保存的字段是：

```scala
val foldedHist = UInt(info.FoldedLength.W)
```

参见 [`Bundles.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Bundles.scala#L88)。

所有预测器需要的折叠规格会先求并集，再放入异构的 `MixedVec`：

```scala
val hist = MixedVec(...)
```

参见 [`Bundles.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Bundles.scala#L184)。

需要注意：**折叠历史不是另一份独立的路径历史，而是原始 PHR 的压缩缓存**。原始 `phr` 才是能够恢复完整历史的存储；folded PHR 主要用于让预测器快速取得适合自己的短历史。

---

### 1.4 `PhrMeta`：用于恢复的历史快照

模块还定义了以下元数据：

```scala
class PhrMeta {
  val phrPtr		// 包括 10bit value 值（默认值），1bit flag
  val phrLowBits	// 13 bit
}
```

`PhrMeta` 没有保存完整的 532 bit PHR，而是保存**指针位置 + 最容易被后续更新覆盖的低 13 bit**。

这是一份紧凑的历史快照。需要恢复某个预测时刻的历史时，可以组合：

```text
当前环形 PHR 中仍然保留的高位 + meta 中保存的 phrLowBits = 恢复出的完整逻辑 PHR
```

因此，每个在流水线或 FTQ 中的预测结果不必携带数百位完整 PHR，只需要携带一个较小的 `PhrMeta`。在非 FPGA 调试配置下，`PhrMeta` 还会附带一份 `predFoldedHist`，用于一致性检查。它属于调试信息，不是恢复所必需的功能状态。

---

### 1.5 存储关系总结

整个 PHR 的存储关系可以概括为：

```text
                物理历史本体
          phr：按位环形缓冲区
                    +
          phrPtr：逻辑历史边界
                    │
          pending：最新更新旁路
                    │
                    ▼
             当前逻辑完整 PHR
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
   各级 folded PHR          PhrMeta
   快速服务预测器          用于将来恢复
```

理解 PHR 存储结构时，最关键的是以下三点：

1. **PHR 的本体是一个按位存储的环形寄存器数组，而不是预测表。**
2. **`phrPtr` 决定如何把物理数组解释成按照新旧关系排列的逻辑历史。**
3. **folded PHR 和 `PhrMeta` 都是围绕原始 PHR 建立的辅助表示：前者用于快速预测，后者用于错误恢复。**

---

## 2. PHR 的读取路径

PHR 的读取可以分成两条互补通路：

```text
                         PHR 读取路径

物理 phr + phrPtr ──→ 环形展开 ──→ pending 旁路 ──→ 完整逻辑 PHR
                                                ├── 生成 PhrMeta
                                                └── 快照恢复/训练

增量维护的 folded PHR ──→ S0～S3 流水寄存器 ──→ 各分支预测器
```

- **完整逻辑 PHR 通路**保留数百位历史，主要用于生成恢复快照、重建旧历史和读取 folded PHR 更新所需的旧位；
- **folded PHR 通路**直接向预测器提供短历史，避免在预测关键路径上每拍重新折叠完整 PHR。

### 2.1 从物理 PHR 展开逻辑 PHR

物理 `phr` 是固定位置的 1-bit 寄存器阵列，必须结合 `phrPtr` 才能得到按新旧顺序排列的逻辑历史。`getPhr` 的实现为：

```scala
private def getPhr(ptr: PhrPtr): UInt =
  (Cat(phr.asUInt, phr.asUInt) >> (ptr.value + 1.U))(
    PhrHistoryLength - 1,
    0
  )
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L56)。这段代码分三步完成环形读取：

1. `Cat(phr.asUInt, phr.asUInt)` 将物理 PHR 复制两份并首尾拼接，使跨越数组末尾的读取可以自然延续到数组开头；
2. 右移 `ptr.value + 1` 位，使 `phr(ptr.value + 1)` 移到结果的最低位；
3. 截取低 `PhrHistoryLength` 位，得到完整逻辑 PHR。

因此，逻辑位与物理位置的关系为：

```text
逻辑 PHR[i] = 物理 phr[(ptr.value + 1 + i) mod PhrHistoryLength]
```

例如 PHR 长度为 8、`ptr.value = 5` 时：

```text
物理读取顺序：6 → 7 → 0 → 1 → 2 → 3 → 4 → 5

逻辑 bit 0 ← phr(6)
逻辑 bit 1 ← phr(7)
逻辑 bit 2 ← phr(0)
...
逻辑 bit 7 ← phr(5)
```

所以 `phrPtr` 指向逻辑 PHR 起点之前的一个物理位置，`phrPtr + 1` 才对应逻辑 bit 0。

### 2.2 pending 写旁路

S1 prediction 和 S3 override 的更新会延后一拍写入物理 `phr`。如果只执行 `getPhr(phrPtr)`，在这一拍内读到的物理内容仍然落后于已经更新的逻辑状态，因此读取侧还需要合并 pending 数据：

```scala
private val oldPhrValue = getPhr(phrPtr)

private val pendingPhrValue = Mux(
  pendingValid,
  Mux(
    pendingTaken,
    Cat(oldPhrValue(PhrHistoryLength - 1, PathHashWidth), pendingBits),
    Cat(oldPhrValue(PhrHistoryLength - 1, PathHashHighWidth), pendingLowBits)
  ),
  oldPhrValue
)
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L65)。其选择关系为：

| pending 状态                      | 读取结果                                               |
| --------------------------------- | ------------------------------------------------------ |
| `pendingValid = false`          | 直接使用环形展开后的`oldPhrValue`                    |
| `pendingValid && pendingTaken`  | 用 pending 中的 15 bit 替换逻辑 PHR 的低 15 bit        |
| `pendingValid && !pendingTaken` | 用 pending 中的`phrLowBits` 替换逻辑 PHR 的低 13 bit |

模块将旁路后的结果作为当前最新的完整逻辑历史：

```scala
private val phrValue = pendingPhrValue
```

因此需要区分：

```text
io.phr / 物理 phr：寄存器阵列的当前物理内容，不包含 pending
phrValue           ：环形展开并合并 pending 后的最新逻辑历史
```

`io.phr := phr.asUInt` 主要把原始物理位向量引出用于观察；预测器并不直接使用这份数百位原始输出进行索引。

### 2.3 folded PHR 的预测流水线

预测器实际使用的是多种规格的 folded PHR。PHR 模块增量维护一份 S0 folded PHR，并沿 BPU 流水线逐级寄存：

```scala
private val s0_foldedPhrReg = RegEnable(s0_foldedPhr,     ..., !s0_stall)
private val s1_foldedPhrReg = RegEnable(s0_foldedPhr,     ..., s0_fire)
private val s2_foldedPhrReg = RegEnable(s1_foldedPhrReg,  ..., s1_fire)
private val s3_foldedPhrReg = RegEnable(s2_foldedPhrReg,  ..., s2_fire)
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L87)。流水关系为：

```text
当前 S0 folded PHR
      │
      ├── !s0_stall ─→ s0_foldedPhrReg
      └── s0_fire   ─→ s1_foldedPhrReg
                           │
                           └── s1_fire ─→ s2_foldedPhrReg
                                                │
                                                └── s2_fire ─→ s3_foldedPhrReg
```

对应输出为：

```scala
io.s0_foldedPhr := s0_foldedPhr
io.s1_foldedPhr := s1_foldedPhrReg
io.s2_foldedPhr := s2_foldedPhrReg
io.s3_foldedPhr := s3_foldedPhrReg
```

其中 `s0_foldedPhr` 是组合选择结果：无更新时保持已有状态；redirect、S3 override 或 taken 的 S1 prediction 出现时，则旁路相应的新 folded PHR。其选择优先级与更新路径一致：

```text
redirect > S3 override > taken S1 prediction > 原 s0_foldedPhrReg
```

这种设计让预测器直接取得已经压缩好的历史，而不是在读取时从完整 `phrValue` 重新计算所有折叠结果。代码中的 `histFoldedPhr` 确实会从 `phrValue` 全量重算 folded PHR，但它只用于一致性断言和调试，不是正常预测输出通路。

### 2.4 `oldFoldedPhr`：读取本次更新前的历史

除了流水级 folded PHR，模块还输出 `oldFoldedPhr`，表示当前 redirect、override 或 S1 更新所依据的基准历史：

```scala
private val oldFoldedPhr = MuxCase(
  s1_foldedPhrReg,
  Seq(
    redirectData.valid -> computeAllFoldedPhr(redirectPhr),
    s3_override        -> s3_foldedPhrReg,
    s1_valid           -> s1_foldedPhrReg
  )
)
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L301)。各场景的读出为：

| 当前场景      | `oldFoldedPhr` 来源                            |
| ------------- | ------------------------------------------------ |
| redirect      | 从 redirect 的`PhrMeta` 重建完整历史后重新折叠 |
| S3 override   | `s3_foldedPhrReg`                              |
| S1 prediction | `s1_foldedPhrReg`                              |
| 无上述事件    | 默认使用`s1_foldedPhrReg`                      |

它与“更新后的 `s0_foldedPhr`”不同：`oldFoldedPhr` 保留本次更新的基准历史，供 aBTB 和 uTAGE 等提前预测路径使用。

### 2.5 `PhrMeta` 的生成和历史恢复

完整的 PHR 默认有 532 bit，不适合随每个预测项在 BPU/FTQ 中传递。模块在 S1 生成紧凑的 `PhrMeta`：

```scala
s1_phrMeta.phrPtr     := s1_phrPtr
s1_phrMeta.phrLowBits := pendingPhrValue(PathHashHighWidth - 1, 0)
s1_phrMeta.predFoldedHist.foreach(_ := s1_foldedPhrReg) // 仅调试
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L101)。功能恢复所需的核心字段是：

```text
phrPtr      ：预测时刻的环形指针
phrLowBits  ：预测时刻逻辑 PHR 的低 13 bit
```

`io.phrMeta` 由下游随 redirect/resolve meta 保存到 FTQ，并在需要恢复历史时通过 redirect 接口回传给 PHR。下游如何寄存和传递该元数据不属于本文的 PHR 刷新范围。

需要恢复某个预测时刻的完整历史时，模块先用 meta 中的指针展开当前物理 PHR，再用 meta 保存的低 13 bit 覆盖容易被后续更新改写的低位：

```scala
private def getRedirectPhr(phrMeta: PhrMeta): UInt = {
  val redirectErrorPhr = getPhr(phrMeta.phrPtr)
  Cat(
    redirectErrorPhr(PhrHistoryLength - 1, PathHashHighWidth),
    phrMeta.phrLowBits
  )
}
```

因此恢复关系为：

```text
当前物理 phr 按 meta.phrPtr 展开的高位
                    +
meta 中保存的低 13 bit
                    =
预测时刻的完整逻辑 PHR
```

环形缓冲区额外预留了 FTQ 推测深度所需的空间，使较老历史的高位在 meta 有效期间仍保留在物理 PHR 中；容易被覆盖的低 13 bit 则由 `PhrMeta` 单独保存。

### 2.6 训练 folded PHR 的读取

分支预测器训练时需要使用“该分支当初被预测时”的路径历史，而不是当前最新历史。commit 输入携带此前保存的 `PhrMeta`，PHR 先恢复预测时刻的完整历史，再全量计算各规格 folded PHR：

```scala
private val predictHist = getRedirectPhr(bpTrain.meta.phr)

AllFoldedHistoryInfo.foreach { info =>
  metaPhrFolded.getHistWithInfo(info).foldedHist :=
    computeFoldedHist(predictHist, info.FoldedLength)(info.HistoryLength)
}

io.trainFoldedPhr := metaPhrFolded
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L293)。这条路径只生成训练用 folded PHR，不直接修改功能性的 `phr`。

### 2.7 输出与消费者

PHR 的主要读取输出及当前消费者如下：

| PHR 输出           | 含义                                    | 当前主要消费者                                |
| ------------------ | --------------------------------------- | --------------------------------------------- |
| `s0_foldedPhr`   | 当前 S0 使用的更新后 folded PHR         | TAGE、SC                                      |
| `s1_foldedPhr`   | 与 S1 请求对齐的 folded PHR             | ITTAGE、uTAGE                                 |
| `s2_foldedPhr`   | 与 S2 流水级对齐的 folded PHR           | 当前 BPU 顶层仅接入本地线网，未直接送入预测器 |
| `s3_foldedPhr`   | 与 S3 请求对齐的 folded PHR             | uTAGE 的 override 路径                        |
| `oldFoldedPhr`   | 本次更新前或恢复基准的 folded PHR       | aBTB、uTAGE normal 路径                       |
| `trainFoldedPhr` | 根据训练项`PhrMeta` 恢复的 folded PHR | TAGE、ITTAGE、SC 训练路径                     |
| `phrMeta`        | 预测时刻的指针和低 13 bit 快照          | BPU 流水线、FTQ、redirect/resolve             |
| `phr`            | 未旋转、未合并 pending 的物理位向量     | 调试和观测                                    |

顶层连接集中在 [`Bpu.scala`](../../src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L252) 和 [`Bpu.scala`](../../src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L583)。

综上，预测关键路径主要读取流水化的 folded PHR；完整逻辑 PHR 则由环形展开和 pending bypass 构成，主要服务于快照生成、恢复和训练。两条通路共同保证预测器既能快速取得短历史，又能在 redirect 或训练时找回正确的历史状态。

---

## 3. PHR 的更新路径

PHR 的更新不只是修改 `phr` 寄存器阵列，还需要同步维护 `phrPtr`、pending 数据和 folded PHR。PHR 的一次功能更新可以归纳为：

1. 从 redirect、S3 override、S1 prediction 中按优先级选择更新来源；
2. 用该来源的 `cfiPc` 和 `target` 生成 15-bit `pathHash`；
3. taken 时将 `phrPtr` 减 2，并用 `hashHigh` 异或 `phrLowBits`；not-taken 时保持快照中的指针和低位；
4. redirect 当拍写入物理 PHR，S1/S3 更新先进入 pending、下一拍落盘；
5. taken 写入 15-bit 窗口，恢复型 not-taken 更新写回 13-bit 低位；
6. 同步选择或计算新的 folded PHR，保证预测器使用的压缩历史与逻辑 PHR 一致。

整体过程可以概括为：

```text
redirect / S3 override / S1 prediction
                    │
                    ▼
        组装 PhrUpdateData
   （valid、taken、cfiPc、target、PhrMeta）
                    │
                    ▼
         计算 15-bit pathHash
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
  shiftBits：2 bit       hashHigh：13 bit
        │                       │
        └───────────┬───────────┘
                    ▼
       更新 phrPtr、phrLowBits
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
  更新 folded PHR      更新物理 phr
                     （必要时经 pending）
```

### 3.1 更新来源及优先级

PHR 有三条功能更新路径：

| 更新来源      | 触发条件                                            | 更新基准                               | 作用                                                     |
| ------------- | --------------------------------------------------- | -------------------------------------- | -------------------------------------------------------- |
| redirect      | `io.train.redirect.valid`                         | redirect 携带的`PhrMeta`             | 恢复到被重定向预测的历史状态，并根据实际分支结果继续更新 |
| S3 override   | `io.train.s3_override`                            | S3 对应的`PhrMeta`                   | 用 S3 晚到的预测结果覆盖先前的 S1 预测                   |
| S1 prediction | `io.s1Train.valid`，在 BPU 顶层连接到 `s1_fire` | 当前 S1 的`phrPtr` 和 `phrLowBits` | 根据早期预测结果推测性地推进 PHR                         |

三路输入首先被整理成统一的 `PhrUpdateData`：

```scala
class PhrUpdateData extends PhrBundle {
  val valid   = Bool()
  val taken   = Bool()
  val cfiPc   = PrunedAddr(VAddrBits)
  val target  = PrunedAddr(VAddrBits)
  val phrMeta = new PhrMeta()
}
```

当多条路径同拍有效时，优先级为：`redirect > S3 override > S1 prediction`。

`phrPtr`、pending 和 folded PHR的选择逻辑为：

```scala
phrPtr := MuxCase(
  phrPtr,
  Seq(
    redirectData.valid -> redirectS0PhrPtr,
    s3_override        -> s3S0PhrPtr,
    s1_valid           -> s1S0PhrPtr
  )
)
```

需要注意，`io.commit` **不直接更新功能性的 `phr` 或 `phrPtr`**。它主要根据提交项携带的 `PhrMeta` 生成训练所需的 folded PHR；另外，在调试开关打开时维护独立的提交历史并执行一致性检查。

### 3.2 生成路径哈希

每条更新路径都使用控制流指令地址 `cfiPc` 和目标地址 `target` 计算路径哈希：

```scala
def pathHash(pc: PrunedAddr, target: PrunedAddr): UInt = {
  val hash = Cat(pc(9, 1), 0.U(4.W)) ^ target(16, 2)
  hash(PathHashWidth - 1, 0)
}
```

默认 `PathHashWidth = 15`，得到的 `pathHash` 被拆成 2bit shiftBits 和 13 bit hashHigh：

```text
             15-bit pathHash
┌──────────────────────────────────────┐
│ hashHigh：13 bit │ shiftBits：2 bit  │
└──────────────────────────────────────┘
```

- `shiftBits` 是 taken 更新时真正插入逻辑历史的 2 bit；
- `hashHigh` 与预测时刻 PHR 的低 13 bit 异或，使更多 PC/target 信息混入路径历史。

### 3.3 更新 `phrPtr` 和 `phrLowBits`

三条更新路径都调用 `getUpdatePtrs`，以各自 `PhrMeta` 中的指针和低 13 bit 为更新基准：

```scala
when(data.valid) {
  updateResult.phrPtr     := data.phrMeta.phrPtr
  updateResult.phrLowBits := data.phrMeta.phrLowBits

  when(data.taken) {
    updateResult.phrPtr     := data.phrMeta.phrPtr - Shamt.U
    updateResult.phrLowBits := hashHigh ^ data.phrMeta.phrLowBits
  }
}
```

其行为为：

| 分支结果  | 新`phrPtr`        | 新`phrLowBits`                 |
| --------- | ------------------- | -------------------------------- |
| taken     | `meta.phrPtr - 2` | `meta.phrLowBits XOR hashHigh` |
| not-taken | 保持`meta.phrPtr` | 保持`meta.phrLowBits`          |

`phrPtr` 是环形指针，因此减法按照 `PhrHistoryLength` 环绕。例如默认长度为 532、旧指针为 0 时：`new phrPtr = 0 - 2 mod 532 = 530`。这里的“减 2”表示**逻辑历史向前推进 2 bit**，并不表示只改写两个物理寄存器；taken 时实际会改写一个 15-bit 窗口。

### 3.4 更新数据的仲裁与 pending 时序

redirect 与 S1/S3 更新的物理写入时序不同：

| 更新来源                       | `phrPtr`/folded PHR      | 物理`phr`                    |
| ------------------------------ | -------------------------- | ------------------------------ |
| redirect                       | 当拍选择恢复后的新状态     | 当拍直接写入，优先于旧 pending |
| S3 override                    | 当拍选择 S3 计算出的新状态 | 先保存到 pending，下一拍写入   |
| taken 的 S1 prediction         | 当拍选择 S1 计算出的新状态 | 先保存到 pending，下一拍写入   |
| not-taken 的普通 S1 prediction | 保持不变                   | 不产生 pending 写入            |

pending 的获胜条件为：

```scala
val s1UpdateWins =
  s1_valid && io.s1Train.prediction.taken && !redirectData.valid && !s3_override

val s3UpdateWins =
  s3_override && !redirectData.valid

pendingValid := s3UpdateWins || s1UpdateWins
```

pending 保存：

```text
pendingTaken
pendingLowBits    // 更新后的低 13 bit
pendingShiftBits  // 新插入的 2 bit
```

在 pending 尚未写入物理阵列的一拍内，读取侧用 `pendingPhrValue` 将 pending 内容旁路到逻辑 PHR。因此，**对后续预测而言，逻辑历史已经更新，不需要等待物理写入完成**：

```text
更新发生当拍：phrPtr / folded PHR 更新，更新内容进入 pending
                         │
                         └── pending bypass 提供最新逻辑 PHR

下一拍：pending 内容写入物理 phr
```

若 redirect 有效，物理写入端直接选择 redirect 数据，因此 redirect 会覆盖同拍可能存在的旧 pending 写入。

### 3.5 写入物理 `phr`

物理写入端首先在 redirect 和 pending 之间选择数据：

```scala
val phrWriteValid = redirectData.valid || pendingValid
val phrWriteTaken = Mux(redirectData.valid, redirectData.taken, pendingTaken)
val phrWritePtr   = Mux(redirectData.valid, s0_phrPtr, phrPtr)
```

#### 3.5.1 taken 更新

taken 时，写入数据由更新后的 13-bit `phrLowBits` 和新的 2-bit `shiftBits` 拼接而成：

```text
phrWriteBits = Cat(updatedPhrLowBits, shiftBits)

┌───────────────────────────────────────────────┐
│ 更新后的 phrLowBits：13 bit │ shiftBits：2 bit │
└───────────────────────────────────────────────┘
```

随后将这 15 bit 分别**写入更新后指针的后 15 个物理位置**，所有地址都按照 `PhrHistoryLength` 环绕。以第一次 taken 更新为例：

```text
初始 phrPtr = 0
新 phrPtr   = 530

15 个写入位置：phr(531), phr(0), phr(1), ..., phr(13)
```

#### 3.5.2 not-taken 恢复写入

not-taken 时指针不移动，也不插入 `shiftBits`。对于 redirect 或 S3 override 这类需要恢复旧快照的更新，硬件会把 `PhrMeta` 保存的 13-bit `phrLowBits` 写回指针后的 13 个位置：

```scala
for (i <- 1 to PathHashHighWidth) {
  phr((phrWritePtr + i.U).value) := phrWriteLowBits(i - 1)
}
```

普通的 not-taken S1 prediction 不生成 pending，因此不会触发这次物理写入。

### 3.6 同步更新 folded PHR

物理 `phr` 更新的同时，模块还需要得到与新逻辑历史一致的 folded PHR。三条路径的更新基准不同：

| 更新来源      | folded PHR 的基准                                    | 处理方式                                                    |
| ------------- | ---------------------------------------------------- | ----------------------------------------------------------- |
| redirect      | 从`PhrMeta` 重建的完整 `redirectPhr`             | 先全量重算所有 folded PHR；若 taken，再增量加入本次路径信息 |
| S3 override   | `s3_foldedPhrReg` 和 S2 预读的 oldest bits         | 在现有 S3 folded PHR 上增量更新                             |
| S1 prediction | `s1_foldedPhrReg` 和包含 pending bypass 的逻辑 PHR | 在现有 S1 folded PHR 上增量更新                             |

最终的 S0 folded PHR 也按照 `redirect > S3 override > taken S1` 选择：

```scala
s0_foldedPhr := MuxCase(
  s0_foldedPhrReg,
  Seq(
    redirectData.valid                        -> redirectS0FoldedPhr,
    s3_override                               -> s3S0FoldedPhr,
    (s1_valid && io.s1Train.prediction.taken) -> s1S0FoldedPhr
  )
)
```

参见 [`Phr.scala`](../../src/main/scala/xiangshan/frontend/bpu/history/phr/Phr.scala#L276)。not-taken 时 `getNextFoldedPhr` 保持原 folded PHR，不执行增量变化。

---

## 4. 刷新方案

### 4.1 I/O 接口修改

PHR 全部为寄存器（无 SRAM），刷新耗时 1 cycle。当前 `PhrIO` 无任何刷新接口，后续阶段需新增受 `HasBpuFlush` 裁剪的可选接口。PHR 无独立 CSR 使能位：当 sticky `bpuFlushEn` 为1并使本次 phase-1 请求被接受后，PHR 必须参与该刷新事务。`HasBpuFlush` 决定刷新硬件是否生成；`HasBpuFlushDefault` 决定总使能是复位0后可 sticky 开启，还是固定为1。

PHR 的刷新方案使用**两类信号协同**：

1. **`contextFlush`（1-cycle 脉冲）**：触发寄存器清零。利用 last-connect 语义，在 `when(phrWriteValid)`、pending 更新、`RegEnable` 和 `MuxCase` 等正常写入之后追加 `when(contextFlush)` 块，以最高优先级覆盖三路更新及旧 pending 对所有功能寄存状态的当拍写入。该信号仅在 `s_waiting -> s_flushing` 迁移的那 1 拍拉高。
2. **`bpuFlushing`（刷新窗口信号）**：在 BPU 顶层状态机处于 `s_flushing` 期间持续为真（从 `contextFlush` 拉高当拍起，直到聚合 `resetDone` 置位迁入 `s_done`）。该信号用于**阻塞刷新窗口内继续到达的旧上下文更新**。由于 `contextFlush` 仅 1 拍，而 `s_flushing` 窗口可能持续多拍（等待 SRAM 型预测器清零完成），窗口内仍有旧上下文的 redirect / s3_override / s1_train 到达。若不阻塞，这些旧上下文写入会污染已清零的 PHR 寄存器。

**PhrIO 新增三个可选端口**：

```scala
// Phr.scala PhrIO 内（建议新增）
val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val resetDone:    Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))

private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

**BPU 顶层分发与接入**（原位扩展现有 BPU context-flush 代码块）：

```scala
if (HasBpuFlush) {
  ...

  // [替换] 替换原 fc.io.resetDone 聚合赋值。
  val predictorsDone = predictors.zipWithIndex.map { case (p, i) =>
    !fc.io.activeFlushMask(i) || p.io.resetDone.get
  }.reduce(_ && _)
  fc.io.resetDone := predictorsDone && phr.io.resetDone.get

  // [替换] 原逐预测器分发改用 BPU 模块内部 Wire。
  predictors.zipWithIndex.foreach { case (p, i) =>
    p.io.contextFlush.get := fc.io.contextFlush && fc.io.activeFlushMask(i)
    p.io.bpuFlushing.get  := fc.io.bpuFlushing && fc.io.activeFlushMask(i)
  }

  // [新增] PHR 不在 predictors 序列中且无独立 mask，单独分发。
  phr.io.contextFlush.get := fc.io.contextFlush
  phr.io.bpuFlushing.get  := fc.io.bpuFlushing
}
```

上述代码仅列出新增或替换项，其余现有 context-flush 逻辑保持不变。`bpuContextFlush`/`bpuFlushing` 将局部 `fc` 输出传给 PHR 刷新逻辑；`HasBpuFlush=false` 时两者为 `false.B`。

PHR 无独立 mask，每次已接受的刷新事务都参与；`fc.io.resetDone` 保持单一驱动，其结果为 `predictorsDone && phr.io.resetDone.get`。

---

### 4.2 跟踪寄存器与指针刷新

`phr` 环形缓冲与 `phrPtr` 在 contextFlush 末尾统一清零，恢复上电初始值。利用 Chisel 的 last-connect 语义，将清零块置于 `when(phrWriteValid)` 与所有正常写入之后，以最高优先级同时覆盖当拍三路更新和上拍 pending 落盘对 `phr` 的写入。`phrPtr` 必须恢复为 `flag=false, value=0`（与 `RegInit` 一致），否则 `updatePtr` 从旧指针起算，新写入落在错误位置，环形缓冲的"最新端"语义崩坏。

```scala
// Phr.scala 末尾（建议新增，覆盖三路更新对 phr/phrPtr 的写入）
if (HasBpuFlush) {
  when(contextFlush) {
    phr    := 0.U.asTypeOf(Vec(PhrHistoryLength, Bool()))
    phrPtr := 0.U.asTypeOf(new PhrPtr)
  }
}
```

清零后 T 拍末写入沿生效：`phr` 全 0；`phrPtr` 恢复 `flag=false`/`value=0`。配合 §4.4 的三路 valid 输入门控，T+1 拍起三路更新不再触发，`phr`/`phrPtr` 保持干净初始值。

### 4.3 Pending、folded PHR 与 `PhrMeta` 暂存寄存器刷新

除 `phr`/`phrPtr` 外，第一章还识别出三组会保留旧上下文的暂存状态：

| 状态组           | 寄存器                                                                       | 不清零的风险                                                                                        |
| :--------------- | :--------------------------------------------------------------------------- | :-------------------------------------------------------------------------------------------------- |
| Pending          | `pendingValid`、`pendingTaken`、`pendingLowBits`、`pendingShiftBits` | 刷新前已仲裁的 S1/S3 更新可在下一拍写回已清零的`phr`，或经 `pendingPhrValue` 旁路重新注入旧历史 |
| Folded PHR 流水  | `s0_foldedPhrReg`～`s3_foldedPhrReg`                                     | 旧折叠历史继续作为预测索引，或随 fire 向后级推移                                                    |
| `PhrMeta` 与预读快照 | PHR 内部的 `s1_phrPtr`、`s2_phrMeta`、`s3_phrMeta`、`s3_oldestBits` | 旧指针、`phrLowBits` 和 oldest bits 可继续参与恢复或 S3 folded PHR 更新 |

#### 4.3.1 PHR 内部 Pending、folded PHR 与 S1 快照清零

Pending 必须清空有效位和全部 payload。仅清 `pendingValid` 在功能上足以阻止写回，但 `pendingTaken`/`pendingLowBits`/`pendingShiftBits` 仍会保留旧上下文数据；本方案按上下文隔离目标将四者全部清零。

`s0/s1/s2/s3_foldedPhrReg` 与 `s1_phrPtr` 的使能为 `!s0_stall`/`fire`，单纯门控三路更新 valid 不能阻止它们在 T 拍采样旧值，因此必须在正常赋值之后用 context-flush last-connect 统一覆盖：

```scala
// Phr.scala 末尾（建议新增）
if (HasBpuFlush) {
  when(contextFlush) {
    pendingValid     := false.B
    pendingTaken     := false.B
    pendingLowBits   := 0.U
    pendingShiftBits := 0.U

    s0_foldedPhrReg := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    s1_foldedPhrReg := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    s2_foldedPhrReg := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    s3_foldedPhrReg := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))

    s1_phrPtr       := 0.U.asTypeOf(new PhrPtr)
  }
}
```

该清零块必须位于 pending 更新赋值、各 `RegEnable` 和 `s1_phrPtr` 正常赋值之后。T 拍即使 `pendingValid` 原本为 1，§4.1 会覆盖它对 `phr` 的当拍写入，本节则在 T 拍末将 pending 本身清零，保证 T+1 拍不再触发写回或旁路。

#### 4.3.2 `PhrMeta` 流水与 S3 预读快照清零

PHR 中有一组 PhrMeta 寄存器组（`s2_phrMeta`/`s3_phrMeta`），用于 `s2_phr`、`s2_oldestBits` 及 S3 更新计算。

`s1_phrMeta` 由 `s1_phrPtr`、`pendingPhrValue` 低位和 `s1_foldedPhrReg` 组合生成，本身不是独立寄存器。刷新后 pending 更新失效，且 `s1_phrPtr` 和 folded PHR 已清零，因此后续生成的 `s1_phrMeta` 为干净值。

`s2_oldestBits` 是由 `s2_phrMeta` 和 PHR 组合读出的 `Wire`，本身无需清零；`s3_oldestBits` 则是由 `s2_fire` 锁存的 `RegEnable` 寄存器，保存旧上下文的 oldest bits 并直接参与 S3 folded PHR 更新。为清除全部路径历史残留，PHR 内部 `s2_phrMeta`/`s3_phrMeta` 和 `s3_oldestBits` 均需显式清零：

```scala
// Phr.scala：对应 RegEnable 正常赋值之后
if (HasBpuFlush) {
  when(contextFlush) {
    s2_phrMeta    := 0.U.asTypeOf(new PhrMeta)
    s3_phrMeta    := 0.U.asTypeOf(new PhrMeta)
    s3_oldestBits := 0.U.asTypeOf(new PhrAllFoldedHistoryOldestBits(AllFoldedHistoryInfo))
  }
}
```

T 拍末三者同时清零；T+1 拍起，PHR 内部恢复和 S3 folded PHR 更新不再使用旧快照。

> `commitHist`/`commitHistPtr` 仅在 `EnableCommitGHistDiff` 开启时生成，用于提交历史与预测历史的调试比对；其结果只进入 `XSWarn` 和性能计数器，不参与 PHR 读出、预测索引、更新或恢复通路，因此不列入本文的功能刷新范围。

### 4.4 状态更新通路刷新

根据第三章，PHR 共有 redirect、S3 override 和 S1 prediction 三条功能更新路径。刷新窗口内必须同时屏蔽三路的有效信号，防止旧上下文数据进入 `phr`、`phrPtr`、pending 和 folded PHR 等存储结构。

三条路径的修改对照如下：

```scala
// Phr.scala
// 原代码
private val s1_valid    = io.s1Train.valid
private val s3_override = WireInit(false.B)
// ...
redirectData.valid := io.train.redirect.valid
s3_override        := io.train.s3_override

// 修改后
private val s1_valid = io.s1Train.valid && (if (HasBpuFlush) !bpuFlushing else true.B)

private val s3_override = WireInit(false.B)
s3_override := io.train.s3_override && (if (HasBpuFlush) !bpuFlushing else true.B)

redirectData.valid := io.train.redirect.valid && (if (HasBpuFlush) !bpuFlushing else true.B)
```

> **IO 门控写法例外**：本节的三个 valid 仅用于阻断 PHR 更新路径，允许直接使用 `io.*` 与 `bpuFlushing` 组合门控，不要求先赋给模块内部 raw `Wire`。该例外仅适用于此处的 redirect、S3 override 和 S1 prediction 三条 valid 路径，其他刷新门控仍保持原约束。

三路门控后的影响如下：

| 更新路径      | 门控后的有效信号                                   | 被阻断的写入                                         |
| :------------ | :------------------------------------------------- | :--------------------------------------------------- |
| redirect      | `redirectData.valid`                             | `phrPtr`、物理 `phr` 及 redirect folded PHR 更新 |
| S3 override   | `s3_override`，同步驱动`s3_overrideData.valid` | `phrPtr`、pending 及 S3 folded PHR 更新            |
| S1 prediction | `s1_valid`                                       | `phrPtr`、pending 及 S1 folded PHR 更新            |

三路门控使用 `bpuFlushing`以覆盖整个刷新窗口期，不能只使用单拍 `contextFlush`。T 拍之前已保存在 pending 中的更新不属于这三路新输入：其当拍对物理 `phr` 的写入由 context-flush 末尾清零覆盖，pending 寄存器组则由 §4.3 清零。

### 4.5 预测输出通路刷新

根据第二章，PHR 读取通路可分为两类：

1. **完整历史及快照通路**：由物理 `phr`、`phrPtr` 和 pending 旁路构成逻辑历史，并生成 `phr`、`phrMeta` 等输出；
2. **folded PHR 通路**：输出 `s0_foldedPhr`、`s1_foldedPhr`、`s2_foldedPhr`、`s3_foldedPhr`、`oldFoldedPhr` 和 `trainFoldedPhr`。

寄存器清零在 T 拍末写入沿才生效，因此 `contextFlush` 当拍两类通路仍可能读出旧历史。在现有 `io.*` 正常赋值之后追加末尾覆盖：由 PHR 本地状态生成的七个输出在 `contextFlush` 当拍置零；`trainFoldedPhr` 由 `io.commit.bits.meta.phr` 组合生成，不会随本地 PHR 存储结构清零，因此在完整 `bpuFlushing` 窗口内持续置零：

```scala
// Phr.scala：所有 io.* 正常输出赋值之后
if (HasBpuFlush) {
  when(contextFlush) {
    io.phr     := 0.U(PhrHistoryLength.W)
    io.phrMeta := 0.U.asTypeOf(new PhrMeta)

    io.s0_foldedPhr   := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    io.s1_foldedPhr   := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    io.s2_foldedPhr   := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    io.s3_foldedPhr   := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
    io.oldFoldedPhr   := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
  }

  when(bpuFlushing) {
    io.trainFoldedPhr := 0.U.asTypeOf(new PhrAllFoldedHistories(AllFoldedHistoryInfo))
  }
}
```

结合实际 `PhrIO` 与 bundle 定义：

- 上述八个输出均为纯 data，没有 `Valid` 包装或独立有效位；
- `PhrMeta` 中只有 `phrPtr`、`phrLowBits` 和可选调试字段 `predFoldedHist`，整体置零即可；`phrPtr.flag` 是环形指针翻转位，不是 valid；
- `PhrAllFoldedHistories` 中只包含各规格的 `foldedHist` data，没有其他控制位需要保留。

T 拍 `contextFlush` 和 `bpuFlushing` 同时有效，因此八个输出的 data 全部为零。T+1 拍起，前七个输出直接读出已清零的 PHR 本地状态；`trainFoldedPhr` 仍可能看到旧 `commit meta`，因此继续由 `bpuFlushing` 强制为零，直到整体刷新窗口结束。本方案不需要修改 `s0_fire`。

### 4.6 resetDone

PHR 为寄存器型，1-cycle 清零完成。在 `HasBpuFlush=true` 的配置中，`resetDone` 语义：`contextFlush` 有效当拍为假（正在清零），其余时刻为真。`HasBpuFlush=false` 时 `resetDone` 端口不生成。

```scala
// Phr.scala（建议新增）
if (HasBpuFlush) {
  io.resetDone.get := !contextFlush
}
```

BPU 顶层的 PHR 分发和 `resetDone` 聚合见 §4.1。

---

### 4.7 `HasBpuFlush` 编译裁剪清单

`HasBpuFlush` 是 elaboration-time Scala `Boolean`，不是运行时硬件信号。PHR 按 00 §3.1/§3.5 和 01 §6.2 使用“可选 I/O + Scala 条件生成”实现刷新机制。

| 范围               | `HasBpuFlush=true`                                                                                                         | `HasBpuFlush=false`                            |
| :----------------- | :--------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------- |
| `PhrIO`          | 生成`contextFlush`/`bpuFlushing`/`resetDone` 三个可选端口                                                              | 三个端口均不生成                                 |
| BPU 顶层接入       | 单独向 PHR 分发`bpuContextFlush`/`bpuFlushing`，并以 `predictorsDone && phr.io.resetDone.get` 驱动 `fc.io.resetDone` | PHR 分发和完成聚合扩展不参与 elaboration         |
| 模块内刷新信号     | `contextFlush`/`bpuFlushing` 由对应可选 IO 驱动                                                                          | 两者为`false.B`，不访问 `None.get`           |
| `phr`/`phrPtr` | 生成`contextFlush` 末尾清零                                                                                                | 清零逻辑不生成，保持原写入优先级                 |
| Pending            | 生成`pendingValid`/`pendingTaken`/`pendingLowBits`/`pendingShiftBits` 末尾清零                                       | 清零逻辑不生成                                   |
| Folded PHR 暂存    | 生成`s0_foldedPhrReg`～`s3_foldedPhrReg` 末尾清零                                                                        | 清零逻辑不生成，保持原`RegEnable` 语义         |
| `PhrMeta`/预读暂存 | 生成 PHR 内部 `s1_phrPtr`、`s2_phrMeta`、`s3_phrMeta`、`s3_oldestBits` 末尾清零 | PHR 内部清零逻辑不生成，原 meta/oldest-bits 流水保持基线语义 |
| 三路更新           | redirect、S3 override 和 S1 prediction 的 valid 在`bpuFlushing` 窗口内被阻断                                               | Scala`if` 选择 `true.B`，门控经常量折叠消失  |
| 读取输出           | `contextFlush` 当拍将八个输出的 data 置零；`trainFoldedPhr` 在完整 `bpuFlushing` 窗口持续置零 | 末尾输出置零块不生成，八个输出保持基线语义       |
| 完成握手           | 生成`io.resetDone.get := !contextFlush`                                                                                    | `resetDone` 端口和赋值均不生成                 |

`HasBpuFlush=false` 时必须保留的基线结构包括 `phr`/`phrPtr`、pending 寄存器、`s0~s3_foldedPhrReg`、`s1_phrPtr`、PHR 内部 `s2/s3_phrMeta`、`s3_oldestBits`、三路更新仲裁与所有原有 PHR 输出；它们是原功能数据通路，不属于刷新裁剪对象。

验收时必须分别 elaboration `HasBpuFlush=true` 和 `HasBpuFlush=false` 两种配置：

- 两种配置均不得出现 `None.get`、未连接端口或 FIRRTL 错误；
- 开启配置需验证 T 拍八个读取输出的 data 全零，整个 `bpuFlushing` 窗口内 `trainFoldedPhr` 持续为零，T 拍末 `phr`/`phrPtr`、pending 四个寄存器、四级 folded PHR 暂存、PHR 内部 `PhrMeta` 流水及 `s3_oldestBits` 全部清零，且整个 `bpuFlushing` 窗口内三路更新均被阻断；
- 关闭配置的生成 RTL 中不得出现 PHR 刷新端口、清零赋值、`resetDone` 逻辑及刷新新增的更新/输出门控，PHR 功能与时序必须与基线一致。

---

## 5. 逐拍追踪

假设 `HasBpuFlush=true`，`contextFlush` 与 fence.i redirect 在 T 拍同时有效，`bpuFlushing` 从 T 拍起持续为真，直到 BPU 顶层聚合完成条件满足并离开 `s_flushing`。PHR 无独立 mask，每次已接受的刷新事务都参与以下时序。

- **T-1 拍**（contextFlush 未到）

  - `contextFlush=false`、`bpuFlushing=false`，redirect、S3 override 和 S1 prediction 三路按基线逻辑正常更新 PHR。
  - pending 组可能保存一笔尚未落入物理 `phr` 的更新，其他 PHR 寄存器与流水快照也可能保留当前上下文历史。
  - `phr`、`phrMeta` 和六组 folded-history 输出正常输出当前历史数据。
- **T 拍**（contextFlush 有效，bpuFlushing 有效）

  - `contextFlush=true`，因此 PHR `io.resetDone.get=false`，表示清零正在执行。
  - `bpuFlushing=true`，`redirectData.valid`/`s3_override`/`s1_valid` 均被门控为假，三路新更新不能进入 `phr`、`phrPtr`、pending 或 folded PHR。
  - §4.5 的末尾组合覆盖立即将 `phr`、`phrMeta` 和六组 folded-history 输出的 data 全部置零，隔离当拍尚未清除的旧数据。
  - T 拍末，§4.2/§4.3 的 last-connect 清零生效：`phr`/`phrPtr`、pending 四个寄存器、`s0_foldedPhrReg`～`s3_foldedPhrReg`、`s1_phrPtr`、PHR 内部 `s2_phrMeta`/`s3_phrMeta` 以及 `s3_oldestBits` 全部置零。刷新前 pending 对物理 `phr` 的当拍落盘也被该末尾清零覆盖。
- **T+1 拍**（contextFlush 已过，bpuFlushing 仍有效）

  - `contextFlush=false`，PHR `io.resetDone.get=true`，表示 T 拍末的清零已生效；BPU 顶层仍需等待其他参与者完成。
  - PHR 内部存储结构（包括 `s3_oldestBits`）保持零值，`pendingValid=false`，不再触发旧 pending 写回或旁路。
  - `bpuFlushing=true`，三路新更新仍被阻断；§4.5 中前七个输出的 `contextFlush` 门控已解除，它们直接读出清零后的 PHR 本地状态；`trainFoldedPhr` 仍由 `bpuFlushing` 强制为零。
- **T+2 至 T+k 拍**（bpuFlushing 持续有效，等待 SRAM 型预测器清零完成）

  - PHR 内部存储结构保持零值，三路更新持续被 `bpuFlushing` 阻断。
  - `trainFoldedPhr` 持续被 `bpuFlushing` 强制为零，不输出刷新前 `commit meta` 恢复出的历史。
  - PHR `io.resetDone.get` 持续为真，但 `fc.io.resetDone` 仍由 `predictorsDone && phr.io.resetDone.get` 驱动，需等待所有被本次 mask 选中的预测器完成。
- **T+k+1 拍**（聚合 `resetDone` 置位，`bpuFlushing` 拉低，状态机迁入 `s_done`）

  - `bpuFlushing` 拉低，redirect、S3 override 和 S1 prediction 三路 valid 恢复跟随原 IO。
  - `trainFoldedPhr` 的强制置零解除，恢复根据当前 `commit meta` 组合生成训练历史。
  - PHR 从干净状态恢复正常更新，`io.resetDone.get` 保持为真。
- **下次刷新 T' 拍及 T'+1 拍**

  - T' 拍新的 `contextFlush` 拉高，三路更新再次被阻断，八个读取输出的 data 置零，PHR `io.resetDone.get` 拉低。
  - T'+1 拍 `contextFlush` 拉低，新一轮清零已生效，PHR `io.resetDone.get` 恢复为真；`bpuFlushing` 仍为真时，`trainFoldedPhr` 继续保持为零。

总结：§4.1 完成可选 IO、BPU 顶层分发与完成聚合；§4.2/§4.3 在 T 拍末清零 PHR 内部存储结构；§4.4 在 `bpuFlushing` 窗口内阻断三路更新；§4.5 在 `contextFlush` 当拍将八个读取输出的 data 置零，并在完整 `bpuFlushing` 窗口内持续将 `trainFoldedPhr` 置零；§4.6 以 `!contextFlush` 生成 PHR `resetDone`。PHR 在 T+1 拍完成本地清零，并在其余参与者完成前持续阻断三路旧上下文更新及旧训练历史输出。
