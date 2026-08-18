<!--
# 2. 一条指令在流水线执行过程中的状态

:::warning

## **学习目标**：

* 🧭 理解香山处理器流水线**全阶段划分**，建立指令执行的全局认知
* 📋 掌握单条指令从取指到写回的**完整状态流转链路**
* 🔍 区分正常执行、停顿、重放、冲刷等**特殊指令状态**
* 🗺️ 能够结合源码对应流水线状态字段，具备工程落地认知

:::

## 2.1 流水线指令状态的核心认知

如果你是第一次接触香山乱序流水线架构，很容易混淆“流水线阶段”和“指令状态”——很多人以为指令走到哪个流水线阶段，就是唯一的执行状态，但在香山乱序执行架构中，**流水线是硬件工作流程，指令状态是指令自身的生命周期标记**，二者绑定但不完全等价。

:::info
💡**为什么要单独学习指令状态？**

香山处理器支持乱序执行、流水线重放、异常冲刷、分支预测纠错等复杂机制，单条指令不会始终匀速流转，会出现停顿、回退、重执行等场景。**指令状态是硬件调度、冲突检测、异常处理、流水线修复的核心依据**。

*通俗比喻：流水线是工厂的传送带，固定分为多个加工工位；指令状态是每一份“加工订单”的进度标签，包含待加工、加工中、暂停、返工、完成、作废等状态，传送带工位决定当前加工动作，状态标签决定后续调度逻辑。*

:::

### 2.1.1 香山流水线基础架构（南湖架构）

香山南湖架构采用经典的**乱序超标量流水线**，整体分为前端取指、前端译码、后端乱序执行、访存、写回五大模块，完整流水线阶段如下，所有指令状态流转均依托该架构展开：

**IF（取指）→ Pre-Decode（预译码）→ Decode（译码）→ Rename（重命名）→ Dispatch（派发）→ Issue（发射）→ Execute（执行）→ Memory（访存）→ Writeback（写回）**

普通顺序流水线指令逐级推进、无状态回退，而香山乱序流水线中，指令进入派发阶段后会脱离原始顺序独立执行，状态变化更灵活且复杂。

## 2.2 单条指令全生命周期标准状态

结合香山源码定义与流水线执行逻辑，我们将一条指令的完整状态划分为 **初始态、前端流转态、后端等待态、执行态、收尾态、终态** 六大类，覆盖从指令加载到执行完成的全部场景。

### 2.2.1 基础状态定义速查表

下表为香山流水线核心指令状态的标准定义、对应阶段、核心行为，是本文档的核心速查内容：

| **状态分类** | **状态名称** | **对应流水线阶段** | **核心行为与特征** | **是否可被打断** |
| --- | --- | --- | --- | --- |
| 初始态 | S\_IDLE（空闲未加载） | 流水线空闲，未进入IF阶段 | 指令未被PC寻址，无硬件资源占用，为流水线初始空闲状态 | 否 |
| 前端流转态 | S\_IF（取指中） | IF 取指阶段 | 根据PC地址从ICache读取指令码，填充指令缓存，可能触发ICache缺失 | 是（分支冲刷、ICache异常） |
| 前端流转态 | S\_PREDEC（预译码） | Pre-Decode 预译码阶段 | 初步识别指令类型、分支指令预判、简单合法性校验，筛选无效指令 | 是（预测错误冲刷） |
| 前端流转态 | S\_DEC（译码完成） | Decode 译码阶段 | 完成指令解析，拆分复杂指令、识别操作类型与源目的寄存器 | 是（全局流水线冲刷） |
| 后端等待态 | S\_RENAME（重命名完成） | Rename 重命名阶段 | 消除寄存器假相关，将架构寄存器映射为物理寄存器，规避写后写冲突 | 是 |
| 后端等待态 | S\_DISP（派发等待） | Dispatch 派发阶段 | 指令被派发至对应保留站，等待操作数就绪与发射时机，占用保留站资源 | 是（异常、冲刷） |
| 执行态 | S\_ISSUE（发射执行） | Issue + Execute 阶段 | 操作数就绪后发射至功能单元，完成算术、逻辑、跳转等运算 | 部分可打断（访存指令可重放） |
| 访存态 | S\_MEM（访存阶段） | Memory 访存阶段 | Load/Store指令访问DCache、TLB地址翻译，处理缓存命中/缺失 | 是（缓存缺失、地址异常触发重放） |
| 收尾态 | S\_WB（写回中） | Writeback 写回阶段 | 将执行结果写回物理寄存器，释放流水线硬件资源 | 否（最终收尾阶段） |
| 终态 | S\_DONE（执行完成） | 流水线空闲复位 | 指令执行完全结束，无资源占用，流水线可接收新指令 | 否 |

### 2.2.2 标准状态流转链路（正常无冲突场景）

在无缓存缺失、无分支错误、无数据冲突、无异常的理想场景下，单条指令的状态会**逐级单向流转、无回退、无停顿**，完整链路如下：

`S_IDLE → S_IF → S_PREDEC → S_DEC → S_RENAME → S_DISP → S_ISSUE → S_MEM → S_WB → S_DONE`

为方便直观理解，用ASCII流水线框图展示单周期状态推进逻辑：

```plain
周期1: [S_IDLE] → 取指触发 → S_IF
周期2: S_IF → 预译码完成 → S_PREDEC
周期3: S_PREDEC → 译码完成 → S_DEC
周期4: S_DEC → 寄存器重命名 → S_RENAME
周期5: S_RENAME → 派发至保留站 → S_DISP
周期6: 操作数就绪 → 发射执行 → S_ISSUE
周期7: 访存指令完成数据访问 → S_MEM
周期8: 结果写回寄存器 → S_WB
周期9: 资源释放，指令结束 → S_DONE
```

## 2.3 流水线特殊指令状态（进阶核心）

理想的连续流转仅存在于理论场景，实际运行中，香山处理器会频繁出现停顿、重放、冲刷、异常终止等特殊场景，对应四类关键特殊状态，也是调试与源码阅读的重点。

### 2.3.1 S\_STALL（流水线停顿态）

当出现**数据相关冲突、功能单元繁忙、前端指令未就绪**时，指令会进入停顿态。此时流水线阶段不推进，指令状态锁定在当前节点，不占用新资源，也不释放已有资源，等待冲突解除后继续流转。

:::warning
💡**性能瓶颈提示**：流水线停顿是CPU性能损耗的核心原因之一，连续多周期停顿会大幅降低IPC（每周期执行指令数），日常性能优化的核心就是减少不必要的停顿场景。

:::

### 2.3.2 S\_REPLAY（重放等待态）

该状态是香山**非阻塞流水线**的核心特性，主要针对Load/Store访存指令。当DCache缺失、TLB未命中、访存异常时，指令不会直接报错终止，而是进入S\_REPLAY状态，保留站记录指令上下文，等待资源就绪后重新发射执行，不阻塞流水线其他无关指令运行。

:::warning
💡**背景知识**：香山Load流水线为非阻塞设计，支持从保留站重发机制，单条访存指令重放不会影响流水线整体吞吐能力，这是高端乱序处理器与顺序处理器的核心区别。

:::

### 2.3.3 S\_FLUSH（冲刷作废态）

当出现**分支预测错误、系统异常、中断触发**时，流水线会触发全局冲刷，所有未完成指令进入S\_FLUSH状态。该状态下指令会直接作废，终止所有执行流程、释放全部硬件资源，流水线复位至空闲状态，重新从正确PC地址取指执行。

:::warning
🚨**关键易错点**：冲刷态和停顿态完全不同。停顿是**暂时暂停、保留上下文、后续继续执行**；冲刷是**直接作废、清空上下文、彻底终止执行**，不可恢复。

:::

### 2.3.4 S\_EXC（异常挂起态）

指令执行过程中触发地址错误、非法指令、权限异常时，会进入S\_EXC异常挂起态。此时指令暂停执行，流水线暂停正常推进，硬件记录异常现场，等待内核异常处理程序响应，处理完成后再恢复流水线或终止指令执行。

### 2.3.5 流水线三大核心执行路径（Path）

从宏观流水线调度视角，香山处理器所有指令的执行行为，都可以归纳为三条互斥且全覆盖的路径：**Normal Path（正常执行路径）**、**Speculative Path（投机执行路径）**、**Exception Path（异常处理路径）**。三条路径完全覆盖指令从执行到结束的所有场景，是理解流水线调度、性能损耗、异常恢复的顶层核心。

*通俗比喻：三条路径如同开车行驶的三种路况。Normal Path是全程绿灯、无意外的顺畅通行；Speculative Path是提前预判路况先行行驶，预判正确则提速、预判错误则折返重开；Exception Path是途中遇到故障、罚单、突发状况，需要停车处理故障后再继续或终止行程。*

#### 1. Normal Path（正常执行路径）

Normal Path是**无冲突、无预测错误、无缓存缺失、无异常**的理想执行路径，也是程序最优执行链路。该路径下指令严格按照标准状态单向流转，无停顿、无回退、无冲刷，流水线吞吐效率最高。

**核心特征**：

* 所有指令预测结果全部正确，无分支纠错需求
* ICache/DCache 全部命中，无访存等待重放
* 无数据相关、资源冲突，流水线持续推进
* 指令状态连续流转，直接抵达 S\_DONE 终态

**完整状态链路**：

`S_IDLE → S_IF → S_PREDEC → S_DEC → S_RENAME → S_DISP → S_ISSUE → S_MEM → S_WB → S_DONE`

该路径是流水线设计的**基准最优场景**，CPU性能优化的核心目标，就是尽可能让更多指令走 Normal Path，减少另外两条路径的占比。

#### 2. Speculative Path（投机执行路径）

Speculative Path是乱序超标量处理器的**核心特色路径**。为解决分支跳转延迟、提升流水线吞吐，香山处理器会对分支指令进行预测，提前执行预判路径上的指令，该类提前执行的指令即为「投机指令」，对应执行路径就是投机路径。

投机路径存在两种结果：**预测正确（Spec Success）** 和 **预测错误（Spec Fail）**。

:::warning
💡**关键性能说明**：投机执行是“以预判换速度”，预测正确时无需等待分支结果，大幅提升流水线效率；预测错误时会触发流水线冲刷，产生性能损耗。

:::

**两种分支状态链路**：

**① 预测正确（正常收尾）**：投机指令正常执行，状态流转不受影响，最终正常写回完成。

`S_IF（投机取指）→ 正常逐级流转 → S_DONE`

**② 预测错误（流水线冲刷）**：分支结果校验失败，所有投机指令进入 S\_FLUSH 状态作废。

`S_IF/S_PREDEC/S_DEC（投机执行中） → S_FLUSH（作废） → S_IDLE（复位）`

#### 3. Exception Path（异常处理路径）

Exception Path是指令执行出现**硬件异常、非法操作、外部中断**时的专属处理路径。该路径会打断正常流水线流转，暂停当前指令执行、锁定异常现场、触发内核异常处理，是流水线的容错保障机制。

触发该路径的典型场景：非法指令、地址未对齐、TLB缺失异常、权限错误、外部硬件中断、系统调用触发异常等。

:::warning
🚨**核心易错点**：Exception Path 区别于 Speculative Path 的错误冲刷，投机错误是**流水线预判失误**，属于性能问题；异常路径是**程序或硬件合法/非法报错**，属于功能正确性问题，必须由内核处理，不可简单重跑。

:::

**异常路径标准流转链路**：

<code>任意执行状态 → S_EXC（异常挂起） → 流水线暂停 & 现场保存 → 内核异常处理 → 流水线恢复/指令终止</code>

### 2.3.6 三条执行路径对比速查表

为快速区分三条核心路径，下表汇总核心差异、触发条件、执行结果与性能影响：

| 执行路径 | 核心触发条件 | 最终执行结果 | 性能/功能影响 |
| :--- | :--- | :--- | :--- |
| **Normal Path** 正常路径 | 无异常、无分支错误、无缓存缺失、无数据冲突 | 指令正常执行、逐级流转、完成写回、资源正常释放 | 最优性能，无任何损耗，流水线满速运行 |
| **Speculative Path** 投机路径 | 分支预测触发提前取指、提前执行投机指令 | 预测正确：正常完成；预测错误：指令冲刷作废、流水线回退 | 预测正确提升性能，预测错误产生轻微性能损耗，不影响功能正确性 |
| **Exception Path** 异常路径 | 非法指令、地址异常、中断、权限错误、硬件报错 | 指令暂停、现场保存、触发内核处理，处理后恢复或终止指令 | 大幅降低瞬时性能，用于保障程序与硬件执行正确性 |

## 2.4 源码层面的状态映射（工程落地）

香山处理器的流水线指令状态，在源码中以状态机枚举的形式定义，所有状态变更均由硬件时序逻辑驱动。以下为核心状态定义源码片段。

```plain
// 定义于 src/main/scala/xiangshan/pipeline/InstState.scala L22-L45
// 香山流水线指令核心状态枚举
object InstState extends Enumeration {
  val S_IDLE    = Value("idle")     // 初始空闲态
  val S_IF      = Value("if")       // 取指阶段
  val S_PREDEC  = Value("predec")   // 预译码阶段
  val S_DEC     = Value("dec")      // 译码完成
  val S_RENAME  = Value("rename")    // 寄存器重命名完成
  val S_DISP    = Value("disp")     // 派发等待
  val S_ISSUE   = Value("issue")    // 发射执行中
  val S_MEM     = Value("mem")      // 访存阶段
  val S_WB      = Value("wb")       // 写回阶段
  val S_DONE    = Value("done")     // 执行完成

  // 特殊异常状态
  val S_STALL   = Value("stall")    // 流水线停顿
  val S_REPLAY  = Value("replay")   // 指令重放
  val S_FLUSH   = Value("flush")    // 指令冲刷作废
  val S_EXC     = Value("exception")// 异常挂起
}
```

## 2.5 分级学习路径指引

本节内容适配不同学习阶段，新手可优先掌握核心内容，进阶学习者可深入特殊机制与源码逻辑。

🟢 **入门必掌握**：标准9种基础状态定义、正常状态流转链路、流水线各阶段核心职责

🔵 **进阶需理解**：停顿、重放、冲刷、异常四大特殊状态的触发条件与行为差异

🟣 **精通深挖**：源码状态机跳转逻辑、重放机制时序、流水线冲刷的资源回收流程、**三条执行路径的底层调度差异**

:::warning
❤ 新手建议：现阶段无需深究源码时序细节，优先记住「状态对应执行进度、特殊状态对应流水线异常场景、三条路径覆盖所有执行场景」，建立整体认知即可，后续学习流水线冲突优化时会再次复用本节知识点。

:::

## 2.6 本章总结

✅ **核心知识点回顾**：

* 香山流水线指令状态分为**基础流转状态**与**特殊异常状态**两大类，覆盖指令全生命周期
* 正常场景下指令单向逐级流转，乱序架构下后端指令可独立执行、状态自主更新
* 流水线所有指令执行行为可归纳为**正常、投机、异常**三条核心路径，覆盖全部运行场景
* 所有指令状态均对应源码枚举定义，是硬件调度逻辑的核心依据


> 更新: 2026-06-26 17:44:49
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/vm64o6wq5agtckwp>
-->

# 2. Instruction States During Pipeline Execution

:::warning

## **Learning objectives**

* 🧭 Understand the complete stage breakdown of the XiangShan pipeline and build a global view of instruction execution.
* 📋 Master the complete state-transition path of one instruction from fetch through writeback.
* 🔍 Distinguish normal execution, stalls, replay, flushes, and other special instruction states.
* 🗺️ Relate pipeline-state fields to the source code and develop an implementation-oriented understanding.

:::

## 2.1 Core View of Pipeline Instruction States

When first learning XiangShan's out-of-order pipeline, it is easy to confuse a pipeline stage with an instruction state. A pipeline stage describes the hardware workflow, whereas an instruction state is a lifecycle marker belonging to the instruction itself. They are related, but not equivalent.

:::info
**Why study instruction states separately?**

XiangShan supports out-of-order execution, replay, exception flushes, and branch-prediction recovery. An instruction therefore does not always advance at a uniform rate: it can stall, roll back, or execute again. Its state is the basis for scheduling, conflict detection, exception handling, and pipeline repair.

*A simple analogy: the pipeline is a factory conveyor belt divided into fixed workstations; the instruction state is the progress label on each work order, such as waiting, in progress, paused, reworked, complete, or cancelled. The workstation determines the current operation, while the label determines later scheduling.*

:::

### 2.1.1 XiangShan's Basic Pipeline (Nanhu)

The Nanhu architecture uses a classic out-of-order superscalar pipeline. It is divided into five major modules: frontend fetch, frontend decode, backend out-of-order execution, memory access, and writeback. All instruction-state transitions rely on this structure:

**IF (fetch) → Pre-Decode → Decode → Rename → Dispatch → Issue → Execute → Memory → Writeback**

In an ordinary in-order pipeline, instructions advance one stage at a time without returning to an earlier state. In XiangShan's out-of-order pipeline, an instruction can execute independently after dispatch, so its state transitions are more flexible and complex.

## 2.2 Standard States in an Instruction's Lifetime

Based on the XiangShan source definitions and pipeline behavior, the complete lifetime is divided into six groups: initial, frontend-flow, backend-waiting, execution, finalization, and terminal states. Together they cover every situation from loading an instruction to finishing execution.

### 2.2.1 Quick reference for basic states

| **State category** | **State name** | **Pipeline stage** | **Main behavior and characteristics** | **Interruptible?** |
| --- | --- | --- | --- | --- |
| Initial | S\_IDLE (idle, not loaded) | Pipeline idle, before IF | The PC has not addressed the instruction; no hardware resource is occupied | No |
| Frontend flow | S\_IF (fetching) | IF fetch | Read the instruction from the ICache using the PC and fill the instruction buffer; an ICache miss may occur | Yes (branch flush or ICache exception) |
| Frontend flow | S\_PREDEC (predecode) | Pre-Decode | Identify the instruction class, preliminarily predict branches, perform simple legality checks, and filter invalid instructions | Yes (misprediction flush) |
| Frontend flow | S\_DEC (decoded) | Decode | Parse the instruction, split complex instructions, and identify operation types and source/destination registers | Yes (global pipeline flush) |
| Backend waiting | S\_RENAME (renamed) | Rename | Remove false register dependencies and map architectural registers to physical registers | Yes |
| Backend waiting | S\_DISP (waiting for dispatch) | Dispatch | Send the instruction to the proper reservation station; wait for operands and an issue slot while occupying station resources | Yes (exception or flush) |
| Execution | S\_ISSUE (issue/execute) | Issue + Execute | Issue to a functional unit when operands are ready and perform arithmetic, logical, or branch operations | Partly (memory instructions can replay) |
| Memory | S\_MEM (memory access) | Memory | Access the DCache and translate addresses through the TLB for Load/Store instructions; handle hits and misses | Yes (miss or address exception can trigger replay) |
| Finalization | S\_WB (writing back) | Writeback | Write the result to the physical register and release pipeline resources | No (finalization stage) |
| Terminal | S\_DONE (complete) | Pipeline idle/reset | Execution is finished, no resources are occupied, and the pipeline can accept a new instruction | No |

### 2.2.2 Standard transition path (no conflicts)

With no cache miss, branch error, data conflict, or exception, a single instruction advances one way through the states without rollback or stalls:

`S_IDLE → S_IF → S_PREDEC → S_DEC → S_RENAME → S_DISP → S_ISSUE → S_MEM → S_WB → S_DONE`

The following ASCII diagram shows the per-cycle progression:

```plain
Cycle 1: [S_IDLE] → fetch triggered → S_IF
Cycle 2: S_IF → predecode complete → S_PREDEC
Cycle 3: S_PREDEC → decode complete → S_DEC
Cycle 4: S_DEC → register rename → S_RENAME
Cycle 5: S_RENAME → dispatched to reservation station → S_DISP
Cycle 6: operands ready → issue and execute → S_ISSUE
Cycle 7: memory instruction completes data access → S_MEM
Cycle 8: result written to register → S_WB
Cycle 9: resources released, instruction finished → S_DONE
```

## 2.3 Special Instruction States (Advanced)

The ideal path exists only in a theoretical case. In practice, XiangShan frequently stalls, replays, flushes, or terminates on an exception. These four special states are central to debugging and source-code reading.

### 2.3.1 S\_STALL (pipeline stall)

When a data dependency, a busy functional unit, or an unavailable frontend instruction occurs, the instruction enters the stall state. The pipeline stage does not advance; the instruction remains at its current node, retains its existing resources, and waits for the conflict to clear.

:::warning
**Performance note**: Stalls are a major source of CPU performance loss. Several consecutive stall cycles reduce IPC (instructions per cycle), so practical optimization focuses on removing unnecessary stalls.

:::

### 2.3.2 S\_REPLAY (waiting for replay)

Replay is a defining feature of XiangShan's non-blocking pipeline, especially for Load/Store instructions. On a DCache miss, TLB miss, or memory exception, the instruction does not simply fail. It enters S\_REPLAY, keeps its context in a reservation station, and is issued again when resources are ready, without blocking unrelated instructions.

:::warning
**Background**: The XiangShan load pipeline is non-blocking and can reissue instructions from a reservation station. Replaying one memory instruction does not stop overall pipeline throughput, a key distinction from an in-order processor.

:::

### 2.3.3 S\_FLUSH (flushed/cancelled)

On a branch misprediction, system exception, or interrupt, the pipeline triggers a global flush and all incomplete instructions enter S\_FLUSH. They are cancelled, their execution is terminated, all resources are released, and the pipeline is reset to idle before fetching again from the correct PC.

:::warning
**Common pitfall**: A stall is a temporary pause that preserves context and can resume; a flush cancels the instruction, discards its context, and cannot be resumed.

:::

### 2.3.4 S\_EXC (exception pending)

An address fault, illegal instruction, or permission fault during execution enters S\_EXC. Execution pauses, normal pipeline progress stops, and hardware records the exception context while the kernel handler responds. The pipeline is then resumed or the instruction is terminated.

### 2.3.5 Three top-level execution paths

From a global scheduling perspective, every XiangShan instruction follows one of three mutually exclusive, collectively exhaustive paths: **Normal Path**, **Speculative Path**, or **Exception Path**. They cover every scenario from execution through completion and provide a top-level model for scheduling, performance loss, and recovery.

*Analogy: Normal Path is a road with green lights; Speculative Path is taking a route based on an early forecast and turning back if it is wrong; Exception Path is stopping to handle a fault before continuing or ending the trip.*

#### 1. Normal Path

Normal Path is the ideal path with no conflicts, mispredictions, cache misses, or exceptions. The instruction follows the standard states in one direction, without stalls, rollback, or flush, and the pipeline reaches its highest throughput.

**Main characteristics**:

* All branch predictions are correct.
* ICache and DCache accesses hit, so no memory replay is needed.
* No data or resource conflicts prevent continuous progress.
* The state advances directly to S\_DONE.

**Complete path**:

`S_IDLE → S_IF → S_PREDEC → S_DEC → S_RENAME → S_DISP → S_ISSUE → S_MEM → S_WB → S_DONE`

This is the pipeline's best-case baseline. Performance optimization aims to keep as many instructions as possible on Normal Path.

#### 2. Speculative Path

Speculative Path is a defining feature of an out-of-order superscalar processor. To hide branch latency and increase throughput, XiangShan predicts a branch and executes instructions on the predicted path before the branch result is known. Those instructions are speculative instructions.

There are two outcomes: **Spec Success** and **Spec Fail**.

:::warning
**Performance note**: Speculation trades a prediction for speed. A correct prediction avoids waiting for the branch result; a wrong prediction causes a flush and a performance penalty.

:::

**Two branch-state paths**:

1. **Correct prediction (normal completion)**: the speculative instruction executes normally and eventually writes back.

   `S_IF (speculative fetch) → normal transitions → S_DONE`

2. **Misprediction (pipeline flush)**: branch validation fails and all speculative instructions are cancelled in S\_FLUSH.

   `S_IF/S_PREDEC/S_DEC (speculating) → S_FLUSH (cancelled) → S_IDLE (reset)`

#### 3. Exception Path

Exception Path handles hardware faults, illegal operations, and external interrupts. It interrupts normal flow, pauses the instruction, records the fault context, and invokes the kernel handler. Typical triggers include illegal instructions, misaligned addresses, TLB faults, permission errors, external interrupts, and system calls.

:::warning
**Common pitfall**: A speculative misprediction is a performance event caused by an incorrect prediction; an exception is a functional correctness event caused by the program or hardware and must be handled by the kernel rather than simply rerun.

:::

**Standard exception path**:

`Any execution state → S_EXC (exception pending) → pipeline paused and context saved → kernel exception handler → pipeline resumed or instruction terminated`

### 2.3.6 Quick comparison of the three paths

| Execution path | Main trigger | Final result | Performance/functional impact |
| :--- | :--- | :--- | :--- |
| **Normal Path** | No exception, misprediction, cache miss, or data conflict | Instruction executes, writes back, and releases resources normally | Best performance; pipeline runs at full speed |
| **Speculative Path** | Branch prediction causes early fetch and execution | Correct: completes normally; wrong: speculative instructions are flushed | Correct prediction improves performance; a wrong one causes a small penalty but not a functional error |
| **Exception Path** | Illegal instruction, address fault, interrupt, permission error, or hardware fault | Instruction pauses, context is saved, and the kernel resumes or terminates it | Temporarily reduces performance while preserving correctness |

## 2.4 Mapping States to the Source (Implementation View)

Pipeline instruction states are defined as an enumeration in the source, and hardware timing logic drives every transition. The core definition is:

```plain
// Defined in src/main/scala/xiangshan/pipeline/InstState.scala L22-L45
// Core XiangShan pipeline instruction states
object InstState extends Enumeration {
  val S_IDLE    = Value("idle")     // Initial idle state
  val S_IF      = Value("if")       // Fetch stage
  val S_PREDEC  = Value("predec")   // Predecode stage
  val S_DEC     = Value("dec")      // Decode complete
  val S_RENAME  = Value("rename")   // Register rename complete
  val S_DISP    = Value("disp")     // Waiting for dispatch
  val S_ISSUE   = Value("issue")    // Issuing/executing
  val S_MEM     = Value("mem")      // Memory stage
  val S_WB      = Value("wb")       // Writeback stage
  val S_DONE    = Value("done")     // Execution complete

  // Special exception states
  val S_STALL   = Value("stall")    // Pipeline stall
  val S_REPLAY  = Value("replay")   // Instruction replay
  val S_FLUSH   = Value("flush")    // Instruction flushed/cancelled
  val S_EXC     = Value("exception")// Exception pending
}
```

## 2.5 Tiered Learning Path

🟢 **Essential for beginners**: the nine basic state definitions, the normal transition path, and the main responsibility of each pipeline stage.

🔵 **Advanced**: triggers and behavioral differences of stalls, replay, flush, and exception states.

🟣 **Deep dive**: state-machine transitions in source code, replay timing, resource reclamation during a flush, and the scheduling differences among the three execution paths.

:::warning
**Beginner's note**: You do not need to memorize source-level timing yet. First remember that states represent execution progress, special states represent exceptional pipeline situations, and the three paths cover all execution scenarios. These concepts will be reused when studying pipeline-conflict optimization.

:::

## 2.6 Chapter Summary

**Key points**:

* XiangShan instruction states comprise basic flow states and special exception states, covering the entire instruction lifetime.
* In the normal case, states advance one way; in the out-of-order backend, instructions execute independently and update their states autonomously.
* All instruction behavior can be classified as Normal, Speculative, or Exception Path.
* Every state is represented by a source-level enumeration and serves as a basis for hardware scheduling.

> Updated: 2026-06-26 17:44:49
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/vm64o6wq5agtckwp>
