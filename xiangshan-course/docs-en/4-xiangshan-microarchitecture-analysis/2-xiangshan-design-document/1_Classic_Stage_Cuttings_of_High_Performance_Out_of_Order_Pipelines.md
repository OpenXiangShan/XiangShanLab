<!--
# 1. 高性能乱序流水线经典划分

##  🚀 1. Xiangshan pipeline（香山流水线总览）
:::info
学习目标：
🧭 理解香山 11 级乱序流水线的整体划分与设计理念
📋 掌握前端、后端、提交三大阶段的核心职责与边界
⚡ 了解各流水级的关键操作与时序约束
📦 能够在源码中定位到对应流水线阶段的实现

:::

如果你是第一次接触高性能乱序处理器，可能会被 "11 级流水线"、"超标量六发射" 这些术语搞得有点头大。别担心 —— 我们可以把整个处理器想象成一座现代化的智能工厂，而流水线就是工厂里的自动化生产线。
想象一下：一条指令就像一个 "产品订单"，从客户下单（程序计数器 PC）开始，经过原料采购（取指）、订单翻译（译码）、任务分配（重命名）、工位调度（分派）、生产加工（执行），最后到成品交付（提交）。香山的 11 级流水线，就是把这个复杂的生产过程拆分成了 11 个连续的工位，每个工位只做一件事，从而实现 "同时处理多个订单" 的超高效率。

### 1.1 流水线整体架构与设计哲学
#### 1.1.1 为什么是 11 级流水线？
现代高性能处理器都采用深度流水线设计，但流水线深度并不是越深越好。香山处理器（雁栖湖 / 南湖架构）选择 11 级流水线，是在频率、功耗、指令延迟三者之间做出的最优权衡。

:::warning
⚠️ 性能权衡：流水线越深，单条指令的总延迟越长，但处理器的时钟频率可以做得更高。11 级是 RISC-V 架构乱序处理器的 "黄金深度"—— 既能在 14nm 工艺下达到 2GHz 以上的频率，又能将关键指令（如整数加法）的延迟控制在 4 个周期以内。

:::

#### 1.1.2 香山 11 级流水线全景图
下面是香山处理器完整的 11 级流水线 ASCII 框图，这是你学习整个架构的 "地图"：

```plain
┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐
│ IF1 │→ │ IF2 │→ │ IF3 │→ │ IF4 │→ │ DEC │→ │ REN │→ │ DP  │→ │ RF  │→ │ ISS │→ │ EXE │→ │ CM  │
└─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘
│        前端(Frontend)            ││              后端(Backend)                        ││提交(Commit)│
│         取指与分支预测            ││  译码、重命名、分派、发射、执行                      ││  指令退休  │
```

#### 1.1.3 三大阶段的核心职责
我们可以将 11 级流水线划分为三个逻辑上独立的大阶段，每个阶段都有明确的输入输出和设计目标：

| 阶段 | 包含流水级 | 核心职责 | 设计目标 |
| --- | --- | --- | --- |
| 前端 (Frontend) | IF1-IF4 | 从内存中获取指令，预测分支方向，保证指令流的连续供给 | 高吞吐、低预测错误率 |
| 后端 (Backend) | DEC-EX | 指令译码、寄存器重命名、乱序调度、执行运算 | 高指令级并行性 (ILP)、低执行延迟 |
| 提交 (Commit) | CM | 按程序顺序提交指令结果，处理异常和中断 | 精确异常、状态一致性 |


:::color4
❤ 新手建议：现阶段你只需记住这三大阶段的划分和核心职责。后面的章节会逐个深入讲解每个流水级的细节。

:::

### 1.2 前端流水线 (IF1-IF4)：指令的 "原料采购部"
前端是处理器的 "水龙头"，它的唯一任务就是以最快的速度、最高的准确率，为后端提供源源不断的正确指令。如果前端 "断流"，后端再强大的执行单元也只能空转。

#### 1.2.1 前端四级流水线详解
| 流水级 | 名称 | 核心操作 |
| --- | --- | --- |
| IF1 | 指令缓存索引 | 根据 PC 值计算 ICache 索引，同时访问分支预测器 (BPU) |
| IF2 | 指令缓存读取 | 从 ICache 中读取指令数据，BPU 完成分支预测 |
| IF3 | 指令解码与预译码 | 对指令进行初步解码，检查是否为分支指令 |
| IF4 | 指令缓冲写入 | 将指令写入指令缓冲 (Instruction Buffer)，等待后端读取 |


:::info
💡 关键概念：前端与后端是解耦的。前端可以超前于后端很多个周期取指，并将指令暂存在指令缓冲中。这样即使后端因为执行长指令而暂时停顿，前端也可以继续工作，不会浪费时钟周期。

:::

#### 1.2.2 前端的 "超级武器"：分支预测器
在程序中，大约每 5-7 条指令就会出现一条分支指令（if-else、for、while 等）。如果处理器等到分支指令执行完才知道下一条指令的地址，流水线就会被完全 "冲毁"，性能会下降 80% 以上。
这就像你在高速公路上开车，每开 5 公里就会遇到一个岔路口。如果你必须开到岔路口才能知道往哪边走，那你每次都要急刹车停下来看路牌。而分支预测器就是你的 "导航系统"，它能提前几公里告诉你应该走哪条路，让你全程保持高速行驶。
香山处理器采用了业界领先的TAGE-SC-L 分支预测器，预测准确率超过 97%，是前端高性能的关键保障。

#### 1.2.3 源码导航
前端流水线的核心实现位于以下目录：
前端顶层模块定义于[src/main/scala/xiangshan/frontend/Frontend.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/frontend/Frontend.scala)
指令缓存(ICache)定义于[src/main/scala/xiangshan/cache/CacheInstruction.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/cache/CacheInstruction.scala)
分支预测器(BPU定义于[src/main/scala/xiangshan/frontend/bpu/BPU.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala)

### 1.3 后端流水线 (DEC-EXE)：指令的 "生产车间"
后端是处理器的 "心脏"，也是乱序执行架构最复杂、最精妙的部分。它的任务是将前端送来的指令流，转化为可以并行执行的微操作流，最大限度地挖掘指令级并行性。

#### 1.3.1 后端七级流水线详解
后端包含从译码 (DEC) 到执行 (EXE) 的 7 个流水级，是整个处理器中逻辑最复杂的部分：

| 流水级 | 名称 | 核心操作 |
| --- | --- | --- |
| DEC | 译码 | 将 RISC-V 指令翻译为内部微操作 (uop)，提取操作数信息 |
| REN | 重命名 | 将架构寄存器映射为物理寄存器，消除伪数据依赖 |
| DP | 分派 | 将微操作按类型分派到不同的执行队列 |
| RF | 寄存器读 | 从物理寄存器堆中读取操作数 |
| ISS | 发射 | 当操作数就绪时，将微操作发射到执行单元 |
| EXE | 执行 | 在对应的执行单元中完成运算操作 |
| WB | 写回 | 将执行结果写回物理寄存器堆 |


:::danger
⚠️ 易错点：很多资料会将写回 (WB) 单独列为一个流水级，但在香山架构中，写回操作是在 EXE 级的末尾完成的，所以我们通常将其合并到 EXE 级中。

:::

## 1.3.2 乱序执行的核心：寄存器重命名
为什么需要寄存器重命名？让我们看一个简单的例子：
```plain
add x1, x2, x3   // x1 = x2 + x3
add x1, x4, x5   // x1 = x4 + x5
```

这两条指令都写 x1 寄存器，存在 "写后写" 依赖。如果按顺序执行，第二条指令必须等第一条执行完才能开始。但实际上，这两条指令之间没有任何真正的数据依赖 —— 它们只是恰好使用了同一个架构寄存器名。
这就像工厂里有两个不同的订单，都要求把产品放到 "1 号货架" 上。如果按顺序执行，第二个订单必须等第一个订单把 1 号货架腾空才能开始。但如果我们有很多物理货架，我们可以把第一个订单放到 "物理货架 A"，第二个放到 "物理货架 B"，这样两个订单就可以并行生产了。
寄存器重命名就是做这件事：它将有限的架构寄存器（x0-x31）映射到数量更多的物理寄存器（香山有 192 个整数物理寄存器和 192 个浮点物理寄存器），从而消除 "写后写" 和 "读后写" 两种伪依赖，让指令可以乱序执行。

#### 1.3.3 执行单元概览
香山处理器是六发射超标量架构，这意味着它每周期最多可以同时发射 6 条微操作到不同的执行单元。执行单元分为三大类：
整数执行单元：**4 个 ALU、2 个乘法 / 除法单元**
浮点执行单元：**4 个乘加单元 (FMAC)、2 个杂项浮点单元
**访存执行单元：**2 条 Load 流水线、2 条 Store 流水线**

:::info
💡 关键数据：
整数加法：1 周期延迟
整数乘法：3 周期延迟
整数除法：16-64 周期延迟
Load 指令：4 周期延迟（命中 L1 缓存）
浮点乘加：5 周期延迟

:::

#### 1.3.4 源码导航
后端流水线的核心实现位于以下目录：
译码单元定义于 [src/main/scala/xiangshan/backend/decode/DecodeUnit.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala)
重命名单元定义于 [src/main/scala/xiangshan/backend/rename/Rename.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/rename/Rename.scala)
分派单元定义于 [src/main/scala/xiangshan/backend/dispatch/Dispatch.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala)

### 1.4 提交阶段 (CM)：指令的 "成品交付部"
提交阶段是流水线的最后一级，也是唯一按程序顺序执行的阶段。它的任务是将乱序执行的结果，按原始程序顺序提交到架构状态，并处理异常和中断。

#### 1.4.1 重排序缓存 (ROB)
提交阶段的核心组件是重排序缓存 (Reorder Buffer, ROB)。所有微操作在重命名阶段都会被分配一个 ROB 项，然后按程序顺序排列在 ROB 中。
当一个微操作执行完成后，它会标记自己的 ROB 项为 "已完成"。ROB 会检查队首的微操作是否已完成，如果是，就将其提交（更新架构寄存器状态），并释放对应的物理寄存器。
这就像工厂里的 "成品检验与发货区"。虽然不同的产品生产速度不同，有的先完成有的后完成，但发货必须按照客户下单的顺序进行。ROB 就是那个负责按订单顺序发货的部门。

#### 1.4.2 精确异常
提交阶段的另一个重要职责是实现精确异常。精确异常意味着：当一条指令发生异常时，处理器的状态就好像这条指令之前的所有指令都已经执行完毕，而这条指令及其之后的所有指令都还没有执行。
这是通过 "先执行后提交" 的机制实现的：所有指令的执行结果都先暂存在物理寄存器中，只有在提交阶段才会真正更新架构状态。如果在执行过程中发生异常，处理器只需清空流水线，从异常指令处重新开始执行即可。

#### 1.4.3 源码导航
提交阶段的核心实现位于以下目录：
重排序缓存定义于 [src/main/scala/xiangshan/backend/rob/ROB.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/rob/Rob.scala)

### 1.5 特殊流水线：访存流水线
访存指令（Load/Store）有自己独立的流水线，这是因为访存操作涉及到缓存、TLB、内存等多个层次，延迟远高于算术运算。

#### 1.5.1 Load 流水线（4 级）
```plain
 ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐
 │  S0  │ → │  S1  │ → │  S2  │ → │  S3  │
 └──────┘   └── ───┘   └──────┘   └──────┘
│ 地址计算││ 缓存查询 ││ 数据选择 ││ 状态更新 │
│ TLB查询 ││ 标签比较 ││ 结果写回 ││ 重发处理 │
```

#### 1.5.2 Store 流水线（4 级）
```plain
┌───────┐   ┌───────┐   ┌───────┐    ┌───────┐
│  S0   │ → │  S1   │ → │  S2   │ →  │  S3   │
└───────┘   └───────┘   └───────┘    └───────┘
│ 地址计算 ││ 地址写入  ││ 访存违例 ││ 数据写入  │
│ TLB查询 ││ StoreQueue││ 检查    ││ 缓存/内存  │
```

:::warning
⚠️ 性能瓶颈：访存指令是现代处理器最主要的性能瓶颈。一条 Load 指令如果命中 L1 缓存需要 4 个周期，如果命中 L2 缓存需要 12 个周期，如果命中 L3 缓存需要 40 个周期，如果访问内存则需要超过 200 个周期。这就是为什么 "缓存友好" 的代码会比 "缓存不友好" 的代码快几十倍。

:::

## 总结
✅ 香山处理器采用11级深度流水线，分为前端 (IF1-IF4)、后端 (DEC-EXE) 和提交 (CM) 三大阶段
✅ 前端负责取指和分支预测，是处理器的 "水龙头"，目标是高吞吐和高预测准确率
✅ 后端负责译码、重命名、分派、发射和执行，是乱序执行的核心，目标是最大化指令级并行性
✅ 提交阶段负责按程序顺序提交结果，实现精确异常，是唯一按顺序执行的阶段
✅ 访存指令有独立的 4 级流水线，是现代处理器最主要的性能瓶颈

:::info
📚 扩展阅读：
如果你想了解更多关于乱序执行的基本概念，可以参考《计算机组成与设计：硬件 / 软件接口》第 4 章
如果你想深入研究香山的微架构设计，可以阅读论文《XiangShan: An Open-Source High-Performance RISC-V Processor》

:::



> 更新: 2026-06-16 17:21:38
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/mlfacffb7hnurwrl>
-->

# 1. Classic Stage Breakdown of a High-Performance Out-of-Order Pipeline

:::info
**Learning objectives**:

* Understand the overall partitioning and design rationale of XiangShan's 11-stage out-of-order pipeline.
* Master the responsibilities and boundaries of the frontend, backend, and commit stages.
* Learn the key operations and timing constraints of each pipeline stage.
* Locate the implementation of each stage in the source tree.

:::

If high-performance out-of-order processors are new to you, terms such as “11-stage pipeline” and “six-issue superscalar” can sound intimidating. Think of the processor as a modern smart factory and the pipeline as its automated production line. An instruction is a “product order”: it starts when the customer places an order (the program counter, PC), then goes through material procurement (fetch), order translation (decode), task assignment (rename), workstation scheduling (dispatch), manufacturing (execute), and finally product delivery (commit). XiangShan divides this complex process into 11 consecutive workstations; each workstation performs a focused task, allowing many orders to be processed at once.

### 1.1 Overall Pipeline Structure and Design Philosophy

#### 1.1.1 Why 11 stages?

Modern high-performance processors use deep pipelines, but deeper is not always better. XiangShan's Yanqihu/Nanhu designs choose 11 stages as a balance among frequency, power, and instruction latency.

:::warning
**Performance trade-off**: A deeper pipeline increases the latency of an individual instruction, but permits a higher clock frequency. Eleven stages are a practical “sweet spot” for a RISC-V out-of-order processor: they can reach above 2 GHz in a 14 nm process while keeping the latency of a critical instruction such as integer addition within four cycles.

:::

#### 1.1.2 XiangShan's 11-stage panorama

The following ASCII diagram is a map of the complete pipeline:

```plain
┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐
│ IF1 │→ │ IF2 │→ │ IF3 │→ │ IF4 │→ │ DEC │→ │ REN │→ │ DP  │→ │ RF  │→ │ ISS │→ │ EXE │→ │ CM  │
└─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘
│             Frontend: fetch and branch prediction             ││             Backend: decode, rename, dispatch, issue, execute             ││ Commit │
```

#### 1.1.3 Responsibilities of the three major stages

| Stage | Pipeline levels | Main responsibility | Design goal |
| --- | --- | --- | --- |
| Frontend | IF1-IF4 | Fetch instructions from memory, predict branches, and keep the instruction stream supplied | High throughput and a low misprediction rate |
| Backend | DEC-EX | Decode, rename, schedule, and execute instructions out of order | High instruction-level parallelism (ILP) and low execution latency |
| Commit | CM | Commit results in program order and handle exceptions and interrupts | Precise exceptions and consistent architectural state |

:::color4
**Beginner's note**: For now, remember only these three stages and their responsibilities. Later chapters examine each pipeline level in detail.

:::

### 1.2 Frontend Pipeline (IF1-IF4): the Instruction “Procurement Department”

The frontend is the processor's faucet. Its sole task is to supply the backend with correct instructions as quickly and accurately as possible. If the frontend “runs dry”, even a powerful backend has nothing to execute.

#### 1.2.1 The four frontend stages

| Level | Name | Main operation |
| --- | --- | --- |
| IF1 | Instruction-cache indexing | Compute the ICache index from the PC and access the branch predictor (BPU) |
| IF2 | Instruction-cache read | Read instruction data from the ICache while the BPU completes prediction |
| IF3 | Decode and predecode | Perform initial decoding and check whether an instruction is a branch |
| IF4 | Instruction-buffer write | Write instructions to the instruction buffer for the backend |

:::info
**Key concept**: The frontend and backend are decoupled. The frontend can fetch many cycles ahead and hold instructions in the instruction buffer. Even if a long-latency backend operation pauses execution, the frontend can continue working instead of wasting cycles.

:::

#### 1.2.2 The frontend's “superweapon”: branch prediction

Programs contain roughly one branch every five to seven instructions (if/else, for, while, and so on). Waiting for a branch to execute before knowing the next address would flush the pipeline and can reduce performance by more than 80 percent. It is like driving on a road with a junction every five kilometres: if you must stop at each junction to read the sign, you cannot keep your speed. A branch predictor is the navigation system that tells you the likely route in advance. XiangShan uses the industry-leading TAGE-SC-L predictor, with accuracy above 97 percent, as a key frontend performance safeguard.

#### 1.2.3 Source navigation

The frontend implementation is located here:

* Frontend top-level module: [src/main/scala/xiangshan/frontend/Frontend.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/frontend/Frontend.scala)
* Instruction cache (ICache): [src/main/scala/xiangshan/cache/CacheInstruction.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/cache/CacheInstruction.scala)
* Branch predictor (BPU): [src/main/scala/xiangshan/frontend/bpu/BPU.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala)

### 1.3 Backend Pipeline (DEC-EXE): the Instruction “Production Floor”

The backend is the processor's heart and the most intricate part of an out-of-order design. It converts the frontend stream into micro-operations that can execute in parallel, extracting as much ILP as possible.

#### 1.3.1 The seven backend levels

| Level | Name | Main operation |
| --- | --- | --- |
| DEC | Decode | Translate RISC-V instructions into internal micro-operations (uops) and extract operand information |
| REN | Rename | Map architectural registers to physical registers and remove false dependencies |
| DP | Dispatch | Send uops to the appropriate execution queues |
| RF | Register read | Read operands from the physical register file |
| ISS | Issue | Issue a uop to an execution unit when its operands are ready |
| EXE | Execute | Perform the operation in the selected execution unit |
| WB | Writeback | Write the result back to the physical register file |

:::danger
**Common pitfall**: Some descriptions list writeback as a separate pipeline level. In XiangShan, writeback occurs at the end of EXE, so it is normally included in the EXE level.

:::

#### 1.3.2 The core of out-of-order execution: register renaming

Why is register renaming necessary? Consider:

```plain
add x1, x2, x3   // x1 = x2 + x3
add x1, x4, x5   // x1 = x4 + x5
```

Both instructions write x1, apparently creating a write-after-write (WAW) dependency. In an in-order machine the second instruction would wait for the first, but there is no true data dependency; the instructions merely reuse an architectural register name. With enough physical registers, the first result can go to physical register A and the second to B, so both can execute in parallel. Register renaming maps the 32 architectural integer registers (x0-x31) to a larger physical file (XiangShan has 192 integer and 192 floating-point physical registers), eliminating WAW and write-after-read (WAR) false dependencies.

#### 1.3.3 Execution-unit overview

XiangShan is a six-issue superscalar design: up to six uops can be issued to execution units each cycle. The units fall into three groups:

* Integer: four ALUs and two multiply/divide units.
* Floating point: four fused multiply-add units (FMAC) and two miscellaneous floating-point units.
* Memory: two load pipelines and two store pipelines.

:::info
**Representative latencies**:

* Integer addition: 1 cycle
* Integer multiplication: 3 cycles
* Integer division: 16-64 cycles
* Load (L1 hit): 4 cycles
* Floating-point multiply-add: 5 cycles

:::

#### 1.3.4 Source navigation

* Decode unit: [src/main/scala/xiangshan/backend/decode/DecodeUnit.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala)
* Rename unit: [src/main/scala/xiangshan/backend/rename/Rename.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/rename/Rename.scala)
* Dispatch unit: [src/main/scala/xiangshan/backend/dispatch/Dispatch.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala)

### 1.4 Commit Stage (CM): the “Finished-Goods Delivery Department”

Commit is the final level and the only stage that follows program order. It places out-of-order results into architectural state in order and handles exceptions and interrupts.

#### 1.4.1 Reorder buffer (ROB)

The reorder buffer is the commit stage's core component. Every uop receives a ROB entry during rename and entries are ordered by program order. Once a uop finishes, it marks its entry complete. The ROB checks the head; if the head uop is complete, it commits the result, updates architectural state, and releases the old physical register. Production can finish out of order, but shipping must follow the customer's order; the ROB is the shipping department that enforces this rule.

#### 1.4.2 Precise exceptions

An exception is precise when the machine state looks as though every instruction before the faulting instruction has completed, while the faulting instruction and all younger instructions have not. XiangShan achieves this with “execute first, commit later”: results remain in physical registers until commit updates architectural state. If execution detects an exception, the processor can flush the pipeline and restart at the exception PC.

#### 1.4.3 Source navigation

The ROB is defined in [src/main/scala/xiangshan/backend/rob/ROB.scala](https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v3/src/main/scala/xiangshan/backend/rob/Rob.scala).

### 1.5 Special Pipeline: Memory Access

Loads and stores use dedicated pipelines because memory operations traverse caches, the TLB, and memory, and therefore have much higher latency than arithmetic operations.

#### 1.5.1 Load pipeline (four levels)

```plain
 ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐
 │  S0  │ → │  S1  │ → │  S2  │ → │  S3  │
 └──────┘   └──────┘   └──────┘   └──────┘
│ Address calculation ││ Cache lookup ││ Data selection ││ State update │
│ TLB lookup          ││ Tag compare  ││ Result writeback││ Replay handling│
```

#### 1.5.2 Store pipeline (four levels)

```plain
┌───────┐   ┌───────┐   ┌───────┐    ┌───────┐
│  S0   │ → │  S1   │ → │  S2   │ →  │  S3   │
└───────┘   └───────┘   └───────┘    └───────┘
│ Address calculation ││ Address write ││ Memory-order violation ││ Data write │
│ TLB lookup          ││ StoreQueue    ││ check                 ││ Cache/memory│
```

:::warning
**Performance bottleneck**: Memory operations are a major bottleneck in modern processors. A load may take four cycles on an L1 hit, 12 on an L2 hit, 40 on an L3 hit, and more than 200 cycles when it reaches memory. This is why cache-friendly code can be tens of times faster than cache-unfriendly code.

:::

## Summary

* XiangShan uses an 11-stage pipeline divided into frontend (IF1-IF4), backend (DEC-EXE), and commit (CM).
* The frontend fetches and predicts branches; it is the processor's faucet and targets high throughput and prediction accuracy.
* The backend decodes, renames, dispatches, issues, and executes instructions; it maximizes ILP.
* Commit retires results in program order and provides precise exceptions; it is the only in-order stage.
* Memory instructions have independent four-level pipelines and are a primary performance bottleneck.

:::info
**Further reading**:

For an introduction to out-of-order execution, see Chapter 4 of *Computer Organization and Design: The Hardware/Software Interface*. For XiangShan's microarchitecture, see *XiangShan: An Open-Source High-Performance RISC-V Processor*.

:::

> Updated: 2026-06-16 17:21:38
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/mlfacffb7hnurwrl>
