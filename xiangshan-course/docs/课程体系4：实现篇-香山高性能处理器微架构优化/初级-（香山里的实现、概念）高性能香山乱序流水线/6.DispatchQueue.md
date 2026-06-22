# 6. Dispatch Queue

:::info

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">🧭</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 学习目标</font>

* **✅**\*\* 理解 Dispatch 模块在流水线中的角色和整体结构\*\*
* **✅**\*\* 掌握指令进入派发队列的准入条件\*\*
* **✅**\*\* 搞懂派发决策的完整判定逻辑\*\*
* **✅**\*\* 认识时序压力的来源及其优化策略\*\*

:::

***

## 6.1 整体定位：Dispatch 是什么？

你可以把香山的后端流水线想象成一条**快递分拣中心**：

* **Decode（译码）**：拆包——把原始指令翻译成微操作（uop）
* **Rename（重命名）**：贴标签——给每个操作数换上物理寄存器编号
* **Dispatch（派发）**：分拣上路——根据 uop 的类型和系统资源状况，把它送到正确的发射队列（Issue Queue）

Dispatch 就是那个**站在十字路口的交通指挥员**，它决定了每一条 uop 能不能走、往哪走、什么时候走。

***

## 6.2 Dispatch Structure（派发结构）

### 6.2.1 宏观数据流

```plain
Rename ──→ Dispatch ──┬──→ Issue Queue (Int/ALU)
                       ├──→ Issue Queue (FP)
                       ├──→ Issue Queue (Vec/VF)
                       ├──→ Issue Queue (MemAddr/LS)
                       ├──→ ROB (重排序缓冲)
                       └──→ LSQ (访存队列)
```

Dispatch 模块**同时与多个下游模块交互**，这就像一个快递分拣员要同时盯着多条传送带——每条带子的容量和速度都不一样，必须统筹兼顾。

### 6.2.2 核心子模块

| **子模块** | **职责** | **比喻** |
| --- | --- | --- |
| **BusyTable（×5）** | 跟踪物理寄存器的"忙/闲"状态，判断源操作数是否就绪 | 库存清单——查查快递到了没 |
| **RegCacheTagTable** | 跟踪整数寄存器的 RegCache 标签，加速旁路唤醒 | VIP快速通道标识 |
| **LsqEnqCtrl** | 控制访存指令入 LSQ 队列 | 仓库入口管理员 |
| **IQ 负载均衡器** | 为可复制的功能单元选择最空闲的 Issue Queue | 哪条通道最空走哪条 |

### 6.2.3 IQ 选择与负载均衡

这是 Dispatch 中最精巧的部分之一。对于**只有唯一对应 IQ 的功能单元**（如除法器），派发目标是确定的；但对于**有多个副本的功能单元**（如多个 ALU Issue Queue），Dispatch 采用**负载均衡算法**——谁最空，就派给谁。

具体实现上，它维护了一个 **IQSort 排序矩阵**，周期性地对各 IQ 的有效条目数进行排序，然后按"最空优先"的原则轮转分配：

```scala
// 比较矩阵：比较各 IQ 的有效条目数
compareMatrix(i)(j) := issueQueueCountAddEnq(exuidx(i)) < issueQueueCountAddEnq(exuidx(j)) 
// 排序结果：
IQSort(0) = 最空的 IQ，
IQSort(iqNum-1) = 最满的 IQIQSortWire(i) := compareMatrix.map(x => 
                                                        PopCount(x) === (iqNum - 1 - i).U)
```

:::warning
❤️新手建议\
当前阶段你只需要理解"IQ 负载均衡 = 选最空的队列"这个核心思想。排序矩阵的具体更新策略（分段更新、3 周期间隔等）是时序优化的细节，不必深究。

:::

***

## 6.3 Dispatch Enq Condition（入队条件）

一条 uop 要成功从 Rename 进入 Dispatch 并最终入队，需要经过**层层关卡**。你可以把它想象成乘坐飞机的安检流程——每一道门都要通过，任何一道卡住就只能等待。

### 6.3.1 三道核心关卡

| **关卡** | **条件变量** | **含义** | **比喻** |
| --- | --- | --- | --- |
| **allowDispatch** | <code>**allowDispatch(i)**</code> | LSQ 有空间容纳本条访存指令 | 仓库满了就别再往里塞 |
| **uopBlockByIQ** | <code>**uopBlockByIQ(i)**</code> | 目标 IQ 有空闲入队口 | 传送带满了就等着 |
| **thisCanActualOut** | <code>**thisCanActualOut(i)**</code> | 前方无阻塞、ROB 可接收 | 跑道清空了才能起飞 |

最终的 <code>**ready**</code> 信号是三者的逻辑与：

```scala
fromRename(i).ready := allowDispatch(i) 
                     && !uopBlockByIQ(i) 
                     && thisCanActualOut(i) 
                     && lsqCanAccept
```

### 6.3.2 allowDispatch 详解

<code>**allowDispatch**</code> 是一条**链式传递**的信号——第 <code>**i**</code> 条 uop 的 <code>**allowDispatch**</code> 依赖于第 <code>**i-1**</code> 条。这就像排队买票，前面的人买完了才轮到你。

```scala
for (index <- allowDispatch.indices) {  
  val allowDispatchPrevious = if (index == 0) true.B else allowDispatch(index - 1)  
  when(isStoreVec(index) || isVStoreVec(index)) {    
    allowDispatch(index) := (sqFreeCount > flowTotal) && allowDispatchPrevious  
  }.elsewhen(isLoadVec(index) || isVLoadVec(index)) {    
    allowDispatch(index) := (lqFreeCount > flowTotal) && allowDispatchPrevious  
  }.otherwise {    
    allowDispatch(index) := allowDispatchPrevious  // 非访存指令直接传递  }}
```

对于**向量访存指令**，Dispatch 采用**保守的 flow 分配策略**：

* 标量 Load/Store：flow = 1
* 向量 unit-stride：flow = 2（因为地址已知后才能确定是否需要拆分）
* 其他向量访存：flow = 16（最坏情况）

:::warning
tip 小技巧：\
💡向量 unit-stride 指令的 flow 之所以设为 2 而非 1，是因为这类指令的拆分情况只有在地址计算完成后才能确定。Dispatch 阶段无法获知地址，所以做了保守估计。

:::

### 6.3.3 uopBlockByIQ 详解

这个信号判断**目标 IQ 的入队口是否被占满**。每个 IQ 有 <code>**numEnq**</code> 个入队口，如果分配给该 IQ 的 uop 数量超过了 <code>**numEnq**</code>，超出的 uop 就会被阻塞：

```scala
result = uopSelIQMatrix.map(_(iqidx)).map(x => 
Mux(io.toIssueQueues(temp).ready,
    x > issue.numEnq.U, x.orR))uopBlockByIQ := uopBlockMatrix.map(_.reduce(_ || _))
```

***

## 6.4 Dispatch Condition（派发条件）

入队条件回答了"能不能进"，而派发条件回答了"能不能出"——即 uop 能否真正从 Dispatch 阶段离开，进入后续流水级。

### 6.4.1 thisCanActualOut 三要素

```scala
thisCanActualOut(i) :=   !blockedByWaitForward(i)   
// ① 没有被 waitForward 阻塞  && notBlockedByPrevious(i) 
// ② 没有被前方的 blockBackward 阻塞  && io.enqRob.canAccept     
// ③ ROB 有空间
```

让我们逐一拆解：

#### ① blockedByWaitForward

某些指令被标记为 <code>**waitForward**</code>，意味着它需要等待前序写回结果。如果 ROB 不为空（说明前面还有未提交的指令），这类指令就必须等待：

```scala
blockedByWaitForward(0) := !io.enqRob.isEmpty && isWaitForward(0)
blockedByWaitForward(i) := blockedByWaitForward(i-1) 
|| (!io.enqRob.isEmpty || Cat(fromRename.take(i).map(_.valid)).orR) 
&& isWaitForward(i)
```

这就像你点了外卖，但被告知"前面还有订单在处理，请稍等"——必须等前面清空了才能轮到你。

#### ② notBlockedByPrevious

某些指令被标记为 <code>**blockBackward**</code>，意味着它会**阻止后面的指令通过**。典型的例子是 **fence** 和 **fence.i** 指令：

```scala
val nextCanOut = VecInit((0 until RenameWidth).map(i => !isBlockBackward(i)))
val notBlockedByPrevious = VecInit((0 until RenameWidth)
.map(i => if (i == 0) true.B  
          else Cat((0 until i).map(j => nextCanOut(j))).andR  // 所有前序都不阻塞))
```

这就像高速公路上的收费站——只要前面有一辆车停下来缴费，后面的车全得等。

#### ③ enqRob.canAccept

ROB（重排序缓冲）是所有指令的最终归宿，如果 ROB 满了，所有指令都无法派发。这是最后的、也是最基础的瓶颈。

### 6.4.2 派发条件的完整判定图

```plain
                    ┌──────────────┐
  Rename uop ──────→│ allowDispatch│──No──→ 阻塞（LSQ满）
                    └──────┬───────┘
                           │ Yes
                    ┌──────▼───────┐
                    │ uopBlockByIQ │──No──→ 阻塞（IQ满）
                    └──────┬───────┘
                           │ Yes
                    ┌──────▼───────────┐
                    │ thisCanActualOut │──No──→ 阻塞（ROB满/被前序阻塞）
                    │  ┌─waitForward   │
                    │  ├─blockBackward │
                    │  └─ROB canAccept │
                    └──────┬───────────┘
                           │ Yes
                    ┌──────▼───────┐
                    │ lsqCanAccept │──No──→ 阻塞（LSQ入队口满）
                    └──────┬───────┘
                           │ Yes
                           ▼
                     ✅ 派发成功！
```

***

## 6.5 Timing Pressure（时序压力）

时序压力是处理器设计中一个"看不见但无处不在"的敌人。Dispatch 模块处于流水线的关键路径上，因为它的决策逻辑涉及**大量条件判断和资源查询**，必须在单个时钟周期内完成。

### 6.5.1 关键时序路径

| **路径** | **描述** | **延迟来源** |
| --- | --- | --- |
| **BusyTable 查询** | 查询所有源操作数的就绪状态 | 多表并行读取 + 结果聚合 |
| **IQ 负载均衡** | 比较各 IQ 有效条目数并排序 | 比较矩阵 + 排序网络 |
| **LSQ 容量查询** | 检查 Load/Store Queue 剩余空间 | 跨模块通信 |
| **allowDispatch 链** | 6 条 uop 串行传递的链式依赖 | 组合逻辑链 |

### 6.5.2 IQ 负载均衡的时序优化

IQ 排序是时序最紧张的路径之一。如果每周期都重新排序所有 IQ，比较矩阵的规模会是 O(N²)，随 IQ 数量增长急剧恶化。

香山采用了**分段更新策略**来缓解这一问题：

```scala
val updateInterval = 3  // 每次更新 3 个 IQ 的排序
val segmentNum = (iqNum - 1) / updateInterval + 1
for (segIdx <- 0 until segmentNum) {  // 只在当前段内重新排序，其余保持不变}
```

这就像图书馆整理书架——不需要每次把所有书重新排列，只需要对一小部分进行调整，其余保持不变。每 3 周期完成一轮完整排序更新。

### 6.5.3 IQ 有效条目数的延迟补偿

另一个关键优化是 <code>**enableDispatchIQBalanceOpt**</code>。当该选项开启时，Dispatch 会**预估当前周期正在入队的 uop 数量**，将其加入 IQ 计数中：

```scala
val needAppendIQValidNumVec = Wire(Vec(exuNum, UInt(...)))// 统计每个 IQ 本周期被选中的 uop 数
val selIQNum = PopCount(uopSelIQ.zipWithIndex.map { case (u, i) => 
  u(iqidx) && FuType.FuTypeOrR(fromRename(i).bits.fuType, exuParams.fuConfigs.map(_.fuType))})
needAppendIQValidNumVec(iqDeqIdx) := selIQNum // 加入延迟补偿后的 IQ 计数
val issueQueueCountAddEnq = VecInit(issueQueueCount.zip(needAppendIQValidNumVec).map(x => x._1 + x._2))
```

这就像餐厅等位——不仅看当前排了多少人，还要算上正在进场的客人，才能做出更准确的分配决策。

:::warning
❤️新手建议\
Timing Pressure 的优化细节较为复杂，初学者只需理解核心思想：**用"预估+分段"换"全量+实时"**，在精度和时序之间取折衷。具体的参数（如 <code>**updateInterval = 3**</code>、补偿偏移 <code>**+6**</code>）属于工程调优的范畴，可在后续深入学习中逐步掌握。

:::

### 6.5.4 派发阻塞的性能计数

Dispatch 模块内置了丰富的性能计数器，用于定位阻塞来源：

```scala
XSPerfAccumulate("stall_cycle_rob", stall_rob)   // ROB 满导致阻塞
XSPerfAccumulate("stall_cycle_iq", dispatchBlock && uopBlockByIQ.asUInt.orR)  // IQ 满导致阻塞
XSPerfAccumulate("stall_cycle_allowDispatch", ...)         // LSQ 满导致阻塞
```

***

## 6.6 总结

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **Dispatch Structure**：Dispatch 是流水线的交通指挥员，核心子模块包括 BusyTable、RegCacheTagTable、LsqEnqCtrl 和 IQ 负载均衡器
* **Dispatch Enq Condition**：入队需过三关——LSQ 有空间（allowDispatch）、IQ 有口子、前方无阻塞
* **Dispatch Condition**：派发需满足 waitForward 不阻塞、blockBackward 不阻塞、ROB 可接收
* **Timing Pressure**：通过分段排序更新和入队预估补偿，在精度与时序间取得平衡


> 更新: 2026-06-01 14:39:28  
