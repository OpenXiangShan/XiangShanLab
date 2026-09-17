# 12-CommonHR 设计分析与刷新方案

> 本文档是 BPU 上下文切换刷新机制中 **CommonHR（Common History Register）** 子模块的设计分析与刷新方案文档。
> CommonHR 是一个**独立模块**（`Module(new CommonHR)`，[Bpu.scala:72](../../../src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L72)），**不是** `BasePredictor` 的子类，因此当前没有 `enable`/`sramResetDone`/`contextFlush`/`resetDone` 接口。它仅向 SC 提供三类全局历史：GHR（16-bit）、BW（8-bit）、IMLI（8-bit）。
>
> **范围**：CommonHR 的预测流水线、三路状态更新路径、整体存储架构、上下文切换刷新方案、刷新完成信号 `resetDone`。
> **前置文档**：刷新握手语义（`contextFlush`/`resetDone`/`bpuFlushEn`、状态机）见 [01-BPU刷新方案概览](01-BPU刷新方案概览.md)；体例与论证范式参考 [06-RAS设计分析与刷新方案](06-RAS设计分析与刷新方案.md)、[07-uRAS设计分析与刷新方案](07-uRAS设计分析与刷新方案.md)。
>
> **本文的刷新编译开关约定**
>
> 1. **编译开关**：CommonHR 刷新接口、BPU 顶层分发/完成聚合、寄存器清零、更新/读取/输出门控及 `resetDone` 生成，均遵循 [00-BPU 刷新机制编译开关实现方案](00-BPU刷新机制编译开关实现方案.md) §3.1/§3.5 和 [01-BPU 刷新方案概览](01-BPU刷新方案概览.md) §6.2，受 elaboration-time Scala `Boolean` `HasBpuFlush` 统一裁剪。
> 2. **编码规则**：`CommonHRIO` 的刷新端口使用 `Option.when(HasBpuFlush)`；可选端口的 `.get` 只允许出现在 `if (HasBpuFlush)` 内。刷新专用清零块、顶层连线和完成逻辑均放入 Scala 守卫；对既有数据通路追加的门控必须在关闭配置下恢复原表达式。§4.3 的更新与流水控制信号按 PHR §4.4 的 valid 门控范式，允许直接使用 `io.*` 与 `bpuFlushing` 组合门控。禁止用 `getOrElse` 隐藏裁剪边界。
> 3. **参与者契约**：CommonHR 不在 `predictors` 序列中、不占用逐预测器 mask 位，与 PHR 一样在每次已接受的刷新事务中固定参与。BPU 顶层需单独分发 `contextFlush`/`bpuFlushing`，并将 `commonHR.io.resetDone.get` 显式加入完成聚合。
> 4. **关闭语义**：`HasBpuFlush=false` 时，三个刷新端口及其所有消费逻辑均不参与 elaboration；CommonHR 原有状态、更新优先级、流水使能和输出通路必须与未接入刷新机制的基线一致。完整裁剪清单见 §4.6。
>
> **规范与实现边界**：00/01 当前版本仍将 CommonHR 标记为后续计划；本文给出 CommonHR 正式纳入时必须遵循的扩展契约。采纳实现时需同步更新 00/01 的参与者范围描述。当前 RTL 中 CommonHR 尚无刷新接口，下文“建议新增/建议修改”均为待落地实现。

---

## 1. CommonHR 的存储结构

CommonHR 的全称是 **Common History Register（公共历史寄存器）**。它在 BPU 顶层独立实例化，不属于 `predictors: Seq[BasePredictor]`，主要**为 SC（Statistical Corrector）集中维护三类控制流历史**：

| 历史 | 含义                     | 默认宽度 | SC 中的用途        |
| ---- | ------------------------ | -------: | ------------------ |
| GHR  | Global History Register  |   16 bit | GlobalTable 索引   |
| BW   | Backward History         |    8 bit | BackwardTable 索引 |
| IMLI | Inner-Most Loop Iterator |    8 bit | IMLITable 索引     |

三种宽度并非在 CommonHR 内写死：`GhrHistoryLength` 和 `BWHistoryLength` 分别取 SC 对应表的最大历史长度，`ImliHistoryLength` 来自 IMLI 表参数，参见 [`Parameters.scala`](../../../src/main/scala/xiangshan/frontend/bpu/Parameters.scala#L76-L79)。

CommonHR 内部的功能状态可归纳为五类：

| 状态类别   | 主要结构                                              | 作用                                              |
| ---------- | ----------------------------------------------------- | ------------------------------------------------- |
| 当前历史   | `commonHR`、`imli`                                | 保存最新的 GHR、BW 和 IMLI                        |
| 历史队列   | `histQueue`                                         | 保存多个在途预测块对应的 GHR/BW 快照              |
| 队列指针   | `enqPtr`、`predPtr`、`writePtr`、`recoverPtr` | 区分新预测、预测读取、S3 回写和 override 恢复位置 |
| 流水快照   | `s1/s2/s3_commonHR`、`s1/s2/s3_imli`              | 让某次预测携带当时的历史逐级前进                  |
| 恢复与校验 | `r1_redirect`、`r1_commonHR`、`debugCommonHR`   | redirect 后重建历史，并检查推测更新的一致性       |

### 1.1 `CommonHREntry`：GHR/BW 的基本存储单元

历史队列和流水线都使用 `CommonHREntry`，定义见 [`Bundles.scala`](../../../src/main/scala/xiangshan/frontend/bpu/history/commonhr/Bundles.scala#L27-L33)：

```scala
class CommonHREntry extends CommonHRBundle {
  val valid = Bool()
  val ghr   = UInt(GhrHistoryLength.W)
  val bw    = UInt(BWHistoryLength.W)
  val predStartPc = Some(PrunedAddr(VAddrBits)) // debug
}
```

- `ghr`：记录近期条件分支的 Taken/Not-Taken 结果；新历史从低位移入。
- `bw`：记录近期条件分支是否为 **Taken 的后向分支**；同样从低位移入。
- `valid`：说明 GHR/BW 是否已经由有效的 S3 预测结果建立。SC 的 GlobalTable 和 BackwardTable 读取受它门控。
- `predStartPc`：用于核对 S0 入队预测块与 S3 回写预测块是否匹配，只承担调试和断言用途。

`commonHR` 是最新已形成的 GHR/BW 寄存器：

```scala
private val commonHR = RegInit(0.U.asTypeOf(new CommonHREntry))
```

它在 S3 正常完成时写入 `s3_newCommonHR`，在 redirect 的下一拍优先写入 `r1_commonHR`。注意，预测侧通常不直接读取 `commonHR`，而是通过 `histQueue` 和旁路选择得到与当前 S0 预测块匹配的历史。

### 1.2 GHR 与 BW 表示什么

GHR 和 BW 都按“预测块内的条件分支顺序”推进。对于一次 S3 更新：

- 第一条 Taken 分支之前的条件分支被视为 Not-Taken，向历史中写入 0；
- 若第一条 Taken 分支本身是条件分支，GHR 再写入 1；
- BW 在对应位置写入“该条件分支是否 Taken 且向后跳转”；
- 若预测块中没有 Taken 分支，则所有命中的条件分支都以 0 推入历史；
- 无条件跳转本身不占用 GHR/BW 历史位，但它之前的条件分支仍会推进历史。

因此两者可以直观理解为：

```text
GHR：最近若干个条件分支的方向历史
     0 = Not-Taken，1 = Taken

BW ：与这些条件分支对应的后向 Taken 特征
     1 = Taken 且目标地址在分支 PC 之前，其他情况为 0
```

二者共用 [`getNewHR`](../../../src/main/scala/xiangshan/frontend/bpu/history/commonhr/Helpers.scala#L25-L35) 完成移位和新位拼接。由于寄存器宽度固定，移出最高位的旧历史自然被丢弃。

### 1.3 `imli`：最内层循环迭代计数

IMLI 使用独立寄存器保存：

```scala
private val imli = RegInit(0.U(ImliHistoryLength.W))
```

它近似刻画连续 Taken 后向条件分支的迭代次数：

- 当前分支是 Taken、后向、条件分支：计数加 1；
- 已经全为 1 时保持饱和，不回绕到 0；
- 不满足上述条件：清零。

正常预测路径在 S1 就依据 `s1_imliTaken` 提前更新 IMLI，以缩短预测关键路径；redirect 和 S3 override 则分别从恢复元数据或 S3 快照重新计算。

IMLI 不放在 `CommonHREntry` 中，而是拥有独立的 `imli` 本体和 `s1/s2/s3_imli` 流水寄存器。这是因为它的正常更新时间在 S1，早于 GHR/BW 的 S3 更新。

### 1.4 `histQueue`：在途预测块的历史快照

从 S0 发起预测到 S3 得到最终结果存在多拍延迟。若只保存一份 `commonHR`，S0、S1、S2、S3 同时在途的多个预测块会相互覆盖历史视图。CommonHR 因此使用一个小型环形队列：

```scala
private val histQueue = RegInit(
  VecInit(Seq.fill(HistQueueSize)(0.U.asTypeOf(new CommonHREntry)))
)
```

`HistQueueSize` 默认是 8，见 [`history/commonhr/Parameters.scala`](../../../src/main/scala/xiangshan/frontend/bpu/history/commonhr/Parameters.scala#L20-L27)。每项保存一份 `CommonHREntry`：

```text
histQueue(0) ... histQueue(7)
     │               │
     └── 每项：valid + ghr + bw + 调试 PC
```

队列不是一条软件意义上的提交日志，而是预测流水线的短期历史窗口：S0 为新预测块预留条目，S3 将该块的更新后历史写回对应条目，S3 override 时再从较早条目取回正确的历史基线。

### 1.5 四类环形指针

`enqPtr`、`predPtr`、`writePtr`、`recoverPtr` 都是 `HistPtr`，包含物理下标 `value` 和绕回标志 `flag`：

| 指针           | 主要用途                            |
| -------------- | ----------------------------------- |
| `enqPtr`     | 指向 S0 新预测块要占用的队列位置    |
| `predPtr`    | 指向正常 S0 读取的历史位置          |
| `writePtr`   | 指向 S3 结果应写回的位置            |
| `recoverPtr` | 指向 S3 override 时可回退读取的位置 |

四个指针初值均为 0。RTL 通过环形距离断言约束它们的相对关系：`writePtr` 到 `predPtr` 的距离不能超过 3，`predPtr` 到 `recoverPtr` 的距离不能超过 2。这与当前四级预测流水结构相匹配。

### 1.6 流水线快照与恢复元数据

GHR/BW 和 IMLI 各有一组 S1～S3 流水寄存器：

```text
s0_commonHR ──s0_fire──> s1_commonHR ──s1_fire──> s2_commonHR ──s2_fire──> s3_commonHR
s0_imli     ──s0_fire──> s1_imli     ──s1_fire──> s2_imli     ──s2_fire──> s3_imli
```

S3 快照组合成 `CommonHRResolveMeta(valid, ghr, bw, imli)`，用于 SC 训练；同时，BPU 顶层把 GHR/BW/IMLI、去重后的命中掩码、分支属性和位置组合成 `CommonHRMeta`，随预测结果保存到 FTQ，供将来的 redirect 恢复。

这两类元数据职责不同：

- `CommonHRResolveMeta`：保存预测时使用的历史，主要服务提交训练；
- `CommonHRMeta`：额外保存块内分支布局，服务误预测后的历史重建。

### 1.7 存储关系总结

```text
                         最新历史本体
                    commonHR(GHR/BW) + imli
                              │
                 ┌────────────┴────────────┐
                 ▼                         ▼
          histQueue + 四指针         IMLI 独立旁路
          对齐多个在途预测块          支持 S1 提前更新
                 │                         │
                 └────────────┬────────────┘
                              ▼
                    S0～S3 历史流水快照
                              │
                   ┌──────────┴──────────┐
                   ▼                     ▼
                SC 预测             FTQ 元数据
                                  训练与 redirect 恢复
```

理解 CommonHR 存储结构时，最关键的是：

1. `commonHR`/`imli` 保存最新历史本体，但 S0 读取还需要 `histQueue` 对齐在途预测块。
2. GHR/BW 在 S3 更新，IMLI 正常情况下在 S1 提前更新，三者并非完全同拍写入。
3. FTQ 保存的是预测时刻的紧凑快照和块内分支信息，而不是第二份持续更新的 CommonHR。

---

## 2. CommonHR 的读取路径

CommonHR 的读取路径分为三部分：

```text
histQueue / 恢复值 / S3 旁路 ──优先级选择──> s0_commonHR ──> SC
                                              │
                                              └──> S1/S2/S3 快照 ──> 元数据

imli / redirect / override / S1 更新 ───────> s0_imli ────────────> SC
                                              └──> S1/S2/S3 快照
```

### 2.1 `s0_commonHR` 的五路选择

[`CommonHR.scala`](../../../src/main/scala/xiangshan/frontend/bpu/history/commonhr/CommonHR.scala#L352-L361) 使用 `MuxCase` 生成 S0 的 GHR/BW，优先级从高到低如下：

| 优先级 | 条件                           | 读取值                    | 原因                                                           |
| -----: | ------------------------------ | ------------------------- | -------------------------------------------------------------- |
|      1 | `r0_valid`                   | `r0_commonHR`           | redirect 当拍先提供恢复元数据中的旧历史，占位且`valid=false` |
|      2 | `r1_valid`                   | `r1_commonHR`           | redirect 次拍提供加入正确分支结果后的重建历史                  |
|      3 | `s3_override`                | `histQueue(recoverPtr)` | S3 发现早期预测错误，回退到可恢复快照                          |
|      4 | `s0_fire && s3_fire && sync` | `s3_newCommonHR`        | 读写指针同步时直接旁路 S3 新值，避免读到旧队列项               |
|      5 | `s0_fire`                    | `histQueue(predPtr)`    | 正常预测读取                                                   |

没有任一条件命中时输出全 0。该顺序说明 redirect 的恢复优先于所有推测路径，override 又优先于正常读和 S3 同拍旁路。

### 2.2 正常预测读取与 S3 同拍旁路

正常情况下，S0 从 `histQueue(predPtr.value)` 取得与当前预测块对应的 GHR/BW。若同一拍 S3 正在形成新历史，并且 `predPtr === writePtr`，队列寄存器要到时钟沿后才更新；此时直接读取队列会晚一拍。因此 RTL 将 `s3_newCommonHR` 旁路到 S0：

```text
predPtr != writePtr：读取 histQueue(predPtr)
predPtr == writePtr 且 S0/S3 同拍 fire：读取 s3_newCommonHR
```

这与 PHR 的 pending 写旁路作用相似：都用于让预测端立即看到逻辑上的最新历史，但 CommonHR 旁路的是一个完整 `CommonHREntry`。

### 2.3 S3 override 回退读取

S3 override 表示后级发现 S1 采用的预测结果需要被覆盖。此时当前年轻预测块基于错误路径产生，S0 不能继续使用正常的 `predPtr` 历史，而是读取：

```scala
histQueue(recoverPtr.value)
```

同时，队列控制逻辑依据是否有足够历史，将 `recoverPtr` 选择性前移，并把 `predPtr` 重定位到实际恢复点。RTL 的注释指出，按当前流水结构最多需要恢复两级历史。

### 2.4 redirect 的 r0/r1 两拍读取

redirect 恢复分为两拍：

```text
r0（redirect 当拍）
FTQ 中的 CommonHRMeta ──> r0_commonHR
                           ghr/bw = 预测时旧快照
                           valid  = false

r1（下一拍）
旧快照 + redirect 正确结果 ──getNewHR──> r1_commonHR
                                      valid = false
```

`valid=false` 是有意设计：redirect 窗口中 GHR/BW 仅用作恢复和占位，不让 SC 的 GlobalTable/BackwardTable 发起一次建立在过渡历史上的读取。r1 时还会强制覆盖 `s1_commonHR`、`s2_commonHR`，清除错误路径在流水线中的历史快照。

### 2.5 IMLI 的读取与旁路

`s0_imli` 是 Wire，不只是 `imli` 寄存器的简单输出。它按如下优先级得到当前值：

1. redirect：从 `meta.imli` 出发，根据正确分支是否为 Taken 后向条件分支执行饱和加一或清零；
2. S3 override：从 `s3_imli` 快照出发重新计算；
3. 正常 `s1_fire`：从 `s1_imli` 出发，根据 `s1_imliTaken` 加一或清零；
4. 其他情况：读取 `imli`。

因此 SC 在 S0 看到的是已经包含最新 redirect、override 或 S1 更新效果的 IMLI，而不必等待寄存器在下一拍生效。

### 2.6 SC 如何使用 CommonHR

BPU 顶层把 `commonHR.io.s0_commonHR` 和 `commonHR.io.s0_imli` 直接送入 SC。SC 在 S0 组合生成索引：

| SC 表         | 历史来源                         | 读请求是否受`CommonHREntry.valid` 门控 |
| ------------- | -------------------------------- | ---------------------------------------- |
| GlobalTable   | `ghr(info.HistoryLength-1, 0)` | 是                                       |
| BackwardTable | `bw(info.HistoryLength-1, 0)`  | 是                                       |
| IMLITable     | `imli`                         | 否                                       |

索引逻辑见 [`Sc.scala`](../../../src/main/scala/xiangshan/frontend/bpu/sc/Sc.scala#L143-L183)。不同 Global/Backward 表只截取各自需要的低位历史。SC 训练时则从 FTQ 返回的 `CommonHRResolveMeta` 读取预测当时的 GHR/BW/IMLI，而不是使用训练发生时的当前 CommonHR。

### 2.7 S3 元数据输出

随流水线到达 S3 后：

```scala
s3ResolveMeta.valid := s3_commonHR.valid
s3ResolveMeta.ghr   := s3_commonHR.ghr
s3ResolveMeta.bw    := s3_commonHR.bw
s3ResolveMeta.imli  := s3_imli
```

此外，S2 的条件分支命中会先按 `cfiPosition` 去重，再寄存成 `s3DedupHitMask`。BPU 顶层将它与 S3 的历史、分支属性和位置共同保存到 redirect meta。这样，未来发生误预测时可以知道预测块中哪些更老的条件分支应先写回历史。

---

## 3. CommonHR 的更新路径

CommonHR 有三条功能更新路径：

```text
优先级最高  redirect：从 FTQ 快照恢复，并加入已解析的正确分支结果
              │
              ├── GHR/BW：r0 取快照，r1 重建
              └── IMLI  ：r0 当拍恢复并更新

              S3 override：纠正较年轻预测块所使用的历史与队列位置

正常路径      S3 fire：生成并写入新 GHR/BW
              S1 fire：提前更新 IMLI
```

### 3.1 S2 预计算：去重、计数与后向判断

S3 更新前，CommonHR 在 S2 对每个 mBTB CFI 候选项预计算：

1. `dedupHitPositions` 按位置去除重复的条件分支命中，只保留相同位置中最先出现的一项；
2. `s2_numHit` 统计预测块内去重后的条件分支总数；
3. 对每个候选项计算 `numLessCandidate(i)`，即位置早于它的条件分支数量；
4. 根据候选 CFI PC 与目标地址判断它是否为后向分支。

这些结果在 `s2_fire` 时进入 S3 寄存器，避免把位置比较、PC 重建和后向判断全部压在 S3 更新路径上。

### 3.2 `getNewHR` 的更新规则

GHR 和 BW 使用同一个更新函数：

```scala
numShift = taken ? numLess : numHit

if (taken && isCond)
  newHR = (oldHR << numShift) 拼接 histBit
else
  newHR = oldHR << numShift
```

其中：

- 对 GHR，`histBit` 默认等于 `taken`，所以 Taken 条件分支写入 1；
- 对 BW，`histBit = taken && isCond && isBackward`；
- `taken=true` 时只处理第一条 Taken 分支之前的条件分支，再视情况加入这条 Taken 条件分支；
- `taken=false` 时处理块中全部条件分支，它们均以移位补 0 的形式记为 Not-Taken。

例如，旧 GHR 为 `...abcd`，块内有三个条件分支，第二个是第一条 Taken 分支：

```text
第一条条件分支：Not-Taken -> 0
第二条条件分支：Taken     -> 1
第三条条件分支：不再执行   -> 不进入历史

新 GHR = 截断((旧 GHR << 2) | 0b01)
```

### 3.3 S3 正常更新 GHR/BW

S3 为每个可能的 first-Taken 候选并行生成一份 `s3_takenCommonHR(i)`，再由 `firstTakenBranchOH` 进行 one-hot 选择：

```text
各候选的位置、属性、后向特征
          │
          ▼
s3_takenCommonHR(0..N-1)
          │ firstTakenBranchOH
          ▼
  s3_selectedTakenCommonHR
```

若预测块没有 Taken 分支，则选择 `s3_defaultCommonHR`，把旧 GHR/BW 左移 `s3_numHit` 位。最终：

```scala
s3_newCommonHR := Mux(s3_taken, s3_selectedTakenCommonHR, s3_defaultCommonHR)
```

当 `s3_fire` 时，正常状态寄存器更新为 `s3_newCommonHR`；同拍还会写入 `histQueue(writePtr)`，随后 `writePtr` 前移。

### 3.4 正常路径下的队列维护

没有 redirect 和 S3 override 时，S0 与 S3 可独立操作队列：

- `s0_fire`：向 `histQueue(enqPtr)` 写入空白的 `initCommonHR`，记录当前 `s0_startPc`，然后 `enqPtr + 1`；满足距离条件时 `predPtr + 1`。
- `s3_fire`：向 `histQueue(writePtr)` 写入 `s3_newCommonHR`，然后 `writePtr + 1`；有足够 override 历史时 `recoverPtr + 1`。

`initCommonHR` 的 GHR/BW/valid 都为 0，主要用于占位和通过 `predStartPc` 检查 S0/S3 的块对应关系；真正历史由 S3 回写或组合旁路提供。

### 3.5 S3 override 更新

S3 override 发生时，模块需要同时完成“写入已确认结果”和“让年轻预测回到正确历史”两件事：

1. 将 `s3_newCommonHR` 写到 `histQueue(writePtr)`；
2. 在下一项写入 `initCommonHR`，为新的 S0 目标块占位；
3. `writePtr` 前移一项，`enqPtr` 重定位到 `writePtr + 2`；
4. 根据 `hasOverrideHist` 计算实际恢复位置，并同时赋给 `predPtr` 和 `recoverPtr`；
5. S0 当拍从旧 `recoverPtr` 指向的队列项读取恢复历史。

IMLI 不从队列恢复，而是以随预测流水到 S3 的 `s3_imli` 为基线，根据覆盖后的第一条 Taken 分支重新执行饱和加一或清零。

### 3.6 redirect 恢复 GHR/BW

redirect 不能只把 GHR/BW 还原为预测时快照，因为触发 redirect 的分支已经得到正确结果，该结果也应进入历史。恢复过程如下：

**r0：取回旧快照并重定位队列。**

- `r0_commonHR.ghr/bw := redirect.meta.ghr/bw`；
- 将 `enqPtr` 设为 `writePtr + 1`；
- 将 `predPtr`、`recoverPtr` 设为 `writePtr - 1`；
- 在队列中放入恢复快照和占位项；
- 立即从 `redirect.meta.imli` 重新计算 IMLI。

**r1：加入正确分支结果。**

- 根据 meta 中保存的 `hitMask`、`position` 和 `attribute` 统计 redirect 分支之前的旧条件分支；
- 使用 redirect 的 `taken`、分支类型和目标方向调用 `getNewHR`；
- `r1_commonHR` 优先覆盖 `commonHR`；
- 同时覆盖 `s1_commonHR`、`s2_commonHR` 和 `histQueue(recoverPtr)`，阻断错误路径残留。

这里的总体关系是：

```text
恢复后历史 = 预测该块时的 CommonHR 快照
           + 该块中 redirect 分支之前的条件分支结果
           + redirect 分支本身的正确结果（若它是条件分支）
```

### 3.7 IMLI 更新优先级

IMLI 的写入优先级在 RTL 中明确为：

```text
redirect > s3_override > s1_fire > 保持
```

三条更新路径都遵循同一规则：Taken 后向条件分支执行饱和加一，否则清零；区别只在于使用的历史基线不同：

| 路径        | IMLI 基线              |
| ----------- | ---------------------- |
| redirect    | `redirect.meta.imli` |
| S3 override | `s3_imli`            |
| 正常 S1     | `s1_imli`            |

这种优先级保证恢复事件不会被同拍的年轻推测更新覆盖。

### 3.8 更新优先级总结

各类状态的更新优先级并不完全相同：

| 状态                 | 高优先级到低优先级                                                                |
| -------------------- | --------------------------------------------------------------------------------- |
| `commonHR`         | `r1_valid` > `s3_fire` > 保持                                                 |
| `s0_commonHR` 读取 | `r0_valid` > `r1_valid` > `s3_override` > S3 同拍旁路 > 正常队列读 > 0      |
| `imli`/`s0_imli` | `r0_valid` > `s3_override` > `s1_fire` > 保持                               |
| 队列主控制           | `r0_valid` > `s3_override` > 正常 S0/S3 并行维护；`r1_valid` 另行回写恢复项 |

CommonHR 的核心设计思想可以概括为：

1. 用 GHR、BW、IMLI 为 SC 提供三种互补的控制流相关性；
2. 用 `histQueue` 和四个指针对齐多拍、并行在途的预测块；
3. 用 S3 旁路避免同步读写造成历史滞后；
4. 用 S3 override 回退解决后级覆盖，用 r0/r1 两拍恢复解决已提交 redirect；
5. 用 FTQ 元数据把“预测时历史”带到未来，从而支持准确训练和误预测恢复。

---

## 4. 刷新方案

CommonHR 全部为寄存器（无 SRAM），清零耗时 1 cycle。CommonHR 无独立 CSR mask 位：`HasBpuFlush=true` 且 BPU 接受一次刷新事务后，CommonHR 固定参与；`HasBpuFlush=false` 时，整套刷新扩展必须在 elaboration 阶段消失。

刷新使用两类信号协同：

1. `contextFlush`：单拍清零脉冲，通过末尾 last-connect 覆盖当拍正常写入；
2. `bpuFlushing`：覆盖完整刷新窗口，阻断延迟到达的 redirect、override、流水推进和 S3/S1 更新。

### 4.1 I/O 接口修改

`CommonHRIO` 新增三个受编译开关裁剪的端口：

```scala
// CommonHR.scala：CommonHRIO 内
val contextFlush: Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val bpuFlushing:  Option[Bool] = Option.when(HasBpuFlush)(Input(Bool()))
val resetDone:    Option[Bool] = Option.when(HasBpuFlush)(Output(Bool()))

private val contextFlush = if (HasBpuFlush) io.contextFlush.get else false.B
private val bpuFlushing  = if (HasBpuFlush) io.bpuFlushing.get  else false.B
```

BPU 顶层完整接入如下，该代码用于替换 BPU 顶层原有的 `flushCtrl` 守卫块，不得在原有实例之外新增第二个 `BpuFlushCtrl`：

```scala
if (HasBpuFlush) {
  ...

  // [替换] 替换原 fc.io.resetDone 聚合赋值。
  val predictorsDone = predictors.zipWithIndex.map { case (p, i) =>
    !fc.io.activeFlushMask(i) || p.io.resetDone.get
  }.reduce(_ && _)
  fc.io.resetDone := predictorsDone && phr.io.resetDone.get && commonHR.io.resetDone.get

  // [替换] 原逐预测器分发改用 BPU 模块内部 Wire。
  predictors.zipWithIndex.foreach { case (p, i) =>
    p.io.contextFlush.get := fc.io.contextFlush && fc.io.activeFlushMask(i)
    p.io.bpuFlushing.get  := fc.io.bpuFlushing && fc.io.activeFlushMask(i)
  }

  // [新增] PHR 不在 predictors 序列中且无独立 mask，单独分发。
  phr.io.contextFlush.get := fc.io.contextFlush
  phr.io.bpuFlushing.get  := fc.io.bpuFlushing

  // [新增] CommonHR 不在 predictors 序列中且无独立 mask，单独分发。
  commonHR.io.contextFlush.get := fc.io.contextFlush
  commonHR.io.bpuFlushing.get  := fc.io.bpuFlushing
}
```

### 4.2 核心状态与流水暂存清零

清零块必须放在 CommonHR 所有正常赋值之后，并整体置于 `if (HasBpuFlush)` 内：

```scala
// CommonHR.scala 末尾
if (HasBpuFlush) {
  when(contextFlush) {
    commonHR   := 0.U.asTypeOf(new CommonHREntry)
    debugCommonHR := 0.U.asTypeOf(new CommonHREntry)
    imli       := 0.U
    histQueue  := 0.U.asTypeOf(histQueue)
    enqPtr     := HistPtr(false.B, 0.U)
    predPtr    := HistPtr(false.B, 0.U)
    writePtr   := HistPtr(false.B, 0.U)
    recoverPtr := HistPtr(false.B, 0.U)

    s1_commonHR := 0.U.asTypeOf(new CommonHREntry)
    s2_commonHR := 0.U.asTypeOf(new CommonHREntry)
    s3_commonHR := 0.U.asTypeOf(new CommonHREntry)
    s1_imli     := 0.U
    s2_imli     := 0.U
    s3_imli     := 0.U

    s3_hitMask          := 0.U.asTypeOf(s3_hitMask)
    s3_numLessCandidate := 0.U.asTypeOf(s3_numLessCandidate)
    s3_numHit           := 0.U
    s3_bwTakenCandidate := 0.U.asTypeOf(s3_bwTakenCandidate)
  }
}
```

四个指针必须恢复到与 `RegInit(HistPtr(false.B, 0.U))` 相同的初态。`s0_commonHR` 和 `s0_imli` 是 Wire，不属于寄存状态，使用 §4.4 的组合输出隔离即可，不应列为“寄存器清零”。

`r1_redirect`、`r1_s0StartPc` 等 payload 寄存器只在 `r1_valid` 为真时消费；§4.3 会阻止刷新窗口产生新的 `r1_valid`，因此功能上无需清零 payload。`debugCommonHR` 虽不参与预测，但会与 `commonHR` 进行一致性断言，因此必须和功能本体同拍清零，避免刷新后产生伪错误；该赋值仍只能生成在打开配置中。

### 4.3 更新与流水推进门控

根据第三章，CommonHR 的状态更新和历史流水推进由 S0～S3 fire、S3 override 以及 redirect 三类控制信号驱动。刷新窗口内必须同时屏蔽这些信号，防止旧上下文数据进入 `commonHR`、`imli`、`histQueue`、四个指针和各级流水暂存。这些信号**使用 `bpuFlushing` 覆盖整个刷新窗口**，不能只使用单拍 `contextFlush`。

`r1_valid` 必须由已经门控的 `r0_valid` 延迟产生。因此落地时需将当前靠前声明的 `r1_valid = RegNext(io.redirect.valid, false.B)` 移到 `r0_valid` 之后，并改为 `RegNext(r0_valid, false.B)`；否则刷新触发拍的 redirect 会在 T+1 重新注入旧上下文。

修改对照如下：

```scala
// CommonHR.scala
// 原代码
private val s0_fire = io.stageCtrl.s0_fire
private val s1_fire = io.stageCtrl.s1_fire
private val s2_fire = io.stageCtrl.s2_fire
private val s3_fire = io.stageCtrl.s3_fire

private val s3_override = io.update.s3Override
private val r1_valid     = RegNext(io.redirect.valid, false.B)

// 修改后
private val s0_fire = io.stageCtrl.s0_fire && (if (HasBpuFlush) !bpuFlushing else true.B)
private val s1_fire = io.stageCtrl.s1_fire && (if (HasBpuFlush) !bpuFlushing else true.B)
private val s2_fire = io.stageCtrl.s2_fire && (if (HasBpuFlush) !bpuFlushing else true.B)
private val s3_fire = io.stageCtrl.s3_fire && (if (HasBpuFlush) !bpuFlushing else true.B)

private val s3_override = io.update.s3Override && (if (HasBpuFlush) !bpuFlushing else true.B)
private val r0_valid     = io.redirect.valid && (if (HasBpuFlush) !bpuFlushing else true.B)
private val r1_valid     = RegNext(r0_valid, false.B)
```

> **IO 门控写法例外**：本节的 `s0_fire`～`s3_fire`、`s3_override` 和 `r0_valid` 仅用于阻断 CommonHR 的状态更新与流水推进，允许直接使用 `io.*` 与 `bpuFlushing` 组合门控，不要求先赋给模块内部 raw `Wire`。该例外仅适用于本节这六个控制信号，其他刷新门控仍保持原约束。

各信号门控后的影响如下：

| 控制路径       | 门控后的有效信号                          | 被阻断的状态变化                                                   |
| -------------- | ----------------------------------------- | ------------------------------------------------------------------ |
| 流水推进       | `s0_fire`～`s3_fire`                  | S0～S3 CommonHR/IMLI 快照推进、S2 候选信息写入 S3 暂存             |
| S1/S3 推测更新 | `s1_fire`、`s3_fire`、`s3_override` | IMLI 更新、GHR/BW 更新、`histQueue` 与四指针更新及 override 恢复 |
| redirect       | `r0_valid`、`r1_valid`                | redirect 恢复数据在当拍及下一拍重新注入 CommonHR                   |

### 4.4 预测输出隔离

根据第二章，CommonHR 输出通路可分为两类：

1. **S0 预测历史通路**：`s0_commonHR` 和 `s0_imli` 送往 SC 参与索引计算；
2. **S3 元数据通路**：`s3ResolveMeta` 和 `s3DedupHitMask` 向 BPU 顶层输出预测历史及条件分支命中信息。

寄存器清零在 T 拍末才生效，因此 S0 两路输出在 `contextFlush` 当拍组合置零；S3 元数据可能在清零拍后仍携带已锁存的旧值，因此 CommonHR 模块出口在完整 `bpuFlushing` 窗口内置零：

```scala
// CommonHR.scala：正常输出赋值之后
io.s0_commonHR :=
  if (HasBpuFlush) {
    Mux(contextFlush, 0.U.asTypeOf(new CommonHREntry), s0_commonHR)
  } else {
    s0_commonHR
  }

io.s0_imli :=
  if (HasBpuFlush) Mux(contextFlush, 0.U(ImliHistoryLength.W), s0_imli)
  else s0_imli

if (HasBpuFlush) {
  when(bpuFlushing) {
    io.s3ResolveMeta  := 0.U.asTypeOf(new CommonHRResolveMeta)
    io.s3DedupHitMask := 0.U.asTypeOf(io.s3DedupHitMask)
  }
}
```

结合实际输出定义：`CommonHREntry` 和 `CommonHRResolveMeta` 整体置零会同时清除 `valid` 与历史数据；`s0_imli` 和 `s3DedupHitMask` 没有独立有效位，需将 data 整体置零。

T 拍 `contextFlush` 和 `bpuFlushing` 同时有效，CommonHR 的四个输出均不携带旧历史；T+1 起 S0 输出直接读取已清零的本地状态，S3 元数据则继续由 `bpuFlushing` 隔离到刷新窗口结束。

### 4.5 `resetDone`

CommonHR 为寄存器型，1 cycle 完成清零。在打开配置中，`contextFlush` 当拍报告未完成，下一拍起报告完成：

```scala
if (HasBpuFlush) {
  io.resetDone.get := !contextFlush
}
```

BPU 顶层的 CommonHR 分发和 `resetDone` 聚合见 §4.1。

### 4.6 `HasBpuFlush` 编译裁剪清单

| 范围           | `HasBpuFlush=true`                                                       | `HasBpuFlush=false`                              |
| -------------- | -------------------------------------------------------------------------- | -------------------------------------------------- |
| `CommonHRIO` | 生成`contextFlush`、`bpuFlushing`、`resetDone`                       | 三个端口均不生成                                   |
| BPU 顶层       | 单独向 CommonHR 分发，并将其`resetDone` 加入聚合                         | 分发、聚合扩展和中间逻辑均不生成                   |
| 核心状态       | 生成`commonHR`、`debugCommonHR`、`imli`、`histQueue`、四指针清零块 | 清零块不生成，原写入优先级不变                     |
| 流水暂存       | 生成 S1～S3 CommonHR/IMLI 及 S3 候选寄存器清零                             | 清零块不生成，原`RegEnable` 语义不变             |
| 更新路径       | 在完整窗口内直接门控`s0~s3_fire`、`s3_override`、`r0/r1_valid`       | Scala`else` 分支为`true.B`，各信号恢复原表达式 |
| 输出路径       | 隔离 S0 历史、S3 resolve meta 和去重 mask                                  | 新增 Mux/覆盖块不生成，输出保持基线                |
| 完成握手       | 生成`io.resetDone.get := !contextFlush`                                  | 端口和赋值均不生成                                 |

验收要求：

- `HasBpuFlush=true/false` 两种配置均可 elaboration，不出现 `None.get`、未连接端口或 FIRRTL 错误；
- §4.3 的六个控制信号应按 `io.* && (if (HasBpuFlush) !bpuFlushing else true.B)` 门控，`r1_valid` 必须由门控后的 `r0_valid` 延迟产生；其他刷新专用结构仍需受 Scala 守卫保护；
- 打开配置验证 T 拍输出隔离、T 拍末全部功能状态清零、完整窗口内无旧更新注入，且 CommonHR 模块输出不携带旧历史、T+1 `resetDone=1`；
- 关闭配置生成 RTL 中不得出现 CommonHR 刷新端口、清零逻辑、完成逻辑及新增门控，功能与时序必须和刷新前基线一致。

---

## 5. 逐拍追踪

假设 `contextFlush` 在 T 拍有效，`bpuFlushing` 从 T 拍持续到全体参与者完成：

- **T-1 拍**（刷新前）

  - `contextFlush=false`、`bpuFlushing=false`，S0～S3 流水、S3 override 和 redirect 均按基线逻辑工作。
  - `commonHR`、`imli`、`histQueue`、四个指针及各级流水暂存可能保存当前上下文历史，四个模块输出正常输出这些信息。
- **T 拍**（触发清零）

  - `contextFlush=true`、`bpuFlushing=true`，因此 `io.resetDone.get=false`。
  - §4.3 将 `s0_fire`～`s3_fire`、`s3_override` 和 `r0_valid` 门控为假；T 拍可能仍存在由 T-1 拍 redirect 产生的旧 `r1_valid`，但其状态写入会被本拍末的清零覆盖，且 T 拍门控后的 `r0_valid` 不会再产生新的 `r1_valid`。
  - §4.4 立即将 `s0_commonHR`、`s0_imli`、`s3ResolveMeta` 和 `s3DedupHitMask` 置零，CommonHR 模块当拍不输出旧历史。
  - T 拍末，§4.2 清零 `commonHR`、`debugCommonHR`、`imli`、`histQueue`、四个指针、S1～S3 CommonHR/IMLI 快照及四组 S3 候选暂存。
- **T+1 拍**（本地清零完成）

  - `contextFlush=false`，因此 `io.resetDone.get=true`；T 拍末的清零已经生效。
  - `bpuFlushing=true`，六个控制信号继续被门控；`r1_valid` 也因 T 拍的 `r0_valid=false` 而为假，旧 redirect 不会重新注入。
  - S0 两路输出直接读取清零后的本地状态；S3 两路元数据输出继续由 `bpuFlushing` 强制为零。
- **T+2 至 T+k 拍**（等待其他参与者）

  - CommonHR 的本地状态保持为零，更新与流水推进持续被阻断，四个模块输出均不携带旧历史。
  - CommonHR `resetDone` 持续为真，但 §4.1 的聚合还需等待 predictors 中被 mask 选中的预测器以及 PHR 完成。
- **T+k+1 拍**（整体刷新窗口结束）

  - 聚合完成后 `bpuFlushing` 拉低，§4.3 的六个控制信号恢复跟随原 IO，§4.4 的 S3 输出隔离解除。
  - CommonHR 从全零状态恢复正常读取、流水推进和历史更新，`io.resetDone.get` 保持为真。

总结：§4.1 完成可选 I/O、顶层信号分发与完成聚合；§4.2 在 T 拍末清零全部功能状态和流水暂存；§4.3 在完整 `bpuFlushing` 窗口内阻断更新与流水推进；§4.4 保证 CommonHR 模块不输出旧历史；§4.5 以 `!contextFlush` 生成本地完成信号。CommonHR 在 T+1 拍完成清零，并在整体刷新窗口结束前保持状态与输出隔离。

`HasBpuFlush=false` 时不存在上述刷新时序：CommonHR 始终执行原有预测、恢复和更新流程。

---

## 6. 总结

CommonHR 接入刷新机制时采用与 00/01 一致的“可选 I/O + Scala 条件生成 + 原数据通路恒等关闭分支”范式。它不占用预测器 mask 位，每次已接受事务固定参与；寄存器状态一拍清零，完整刷新窗口阻断旧更新，S0/S3 输出同步隔离，并以真实 `resetDone` 加入 BPU 顶层完成聚合。

`HasBpuFlush=false` 的首要约束不是“运行时信号恒为 0”，而是 CommonHR 的刷新端口和专用结构不参与 elaboration，且关闭配置的原始 CommonHR 数据通路保持不变。
